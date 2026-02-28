# Pitfalls Research

**Domain:** Spring Boot JWT Auth — Google/Apple ID token verification, Spring Security 7, refresh token rotation, Spring Authorization Server (signing only)
**Researched:** 2026-03-01
**Confidence:** HIGH (Spring Security 7 migration docs verified via official docs; token pitfalls verified via multiple authoritative sources)

---

## Critical Pitfalls

### Pitfall 1: Using Spring Authorization Server's Full Auto-Configuration When You Only Need JWT Signing

**What goes wrong:**
Importing `@Import(OAuth2AuthorizationServerConfiguration.class)` or relying on Spring Authorization Server auto-configuration installs the entire OAuth2 protocol endpoint suite — `/oauth2/authorize`, `/oauth2/token`, `/oauth2/introspect`, device authorization, OIDC endpoints — none of which this project uses. Worse, it **requires** `RegisteredClientRepository` and `AuthorizationServerSettings` beans or the application fails to start. The auto-configured `SecurityFilterChain` for the authorization server conflicts with your own stateless JWT filter chain.

**Why it happens:**
Developers follow Spring Authorization Server "Getting Started" docs, which always show a full authorization server setup. The narrower use case — borrowing just `JWKSource` and `JwtEncoder` from the library without the full server — is not prominently documented.

**How to avoid:**
Do NOT use `OAuth2AuthorizationServerConfiguration` or `@Import` of it. Instead, wire only what you need directly:

```kotlin
@Bean
fun jwkSource(): JWKSource<SecurityContext> {
    val rsaKey: RSAKey = loadFromKeystore() // or generate
    return ImmutableJWKSet(JWKSet(rsaKey))
}

@Bean
fun jwtEncoder(jwkSource: JWKSource<SecurityContext>): JwtEncoder =
    NimbusJwtEncoder(jwkSource)
```

This is enough for signing JWTs. No `RegisteredClientRepository`, no `AuthorizationServerSettings`, no protocol endpoints exposed.

**Warning signs:**
- Application fails to start with "No qualifying bean of type RegisteredClientRepository"
- Requests to `/oauth2/token` or `/oauth2/authorize` return 404 or 405 (they are registered but unused)
- Two `SecurityFilterChain` beans conflict — authorization server chain intercepts your auth endpoints

**Phase to address:**
Spring Security configuration phase (Phase: Spring Security + JWT infrastructure setup)

---

### Pitfall 2: Spring Security 7 Removes AntPathRequestMatcher — Not Replacing It Breaks Request Authorization

**What goes wrong:**
Spring Security 7.0 removes `AntPathRequestMatcher` and `MvcRequestMatcher`. All online tutorials and older Baeldung articles use `AntPathRequestMatcher.antMatcher(...)`. Copying these patterns compiles against Spring Security 6 but fails at runtime or causes incorrect request matching in Spring Security 7.

**Why it happens:**
The migration happened in Spring Security 7.0 (shipping with Spring Boot 4). The existing internet corpus of Spring Security examples overwhelmingly uses the old matchers.

**How to avoid:**
Use `PathPatternRequestMatcher.withDefaults().matcher(...)` exclusively:

```kotlin
http.authorizeHttpRequests { auth ->
    auth
        .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/**")).permitAll()
        .anyRequest().authenticated()
}
```

For servlet-path-scoped paths, use `PathPatternRequestMatcher.Builder` with `basePath`.

**Warning signs:**
- `ClassNotFoundException: AntPathRequestMatcher` or `MvcRequestMatcher` at startup
- All requests return 401/403 regardless of configured permit rules
- IDE shows deprecation on `antMatcher(...)` or `mvcMatchers(...)`

**Phase to address:**
Spring Security stateless configuration phase

---

### Pitfall 3: Refresh Token Race Condition — Concurrent Requests Bypass Rotation

**What goes wrong:**
When a mobile client fires two concurrent API requests just as the access token expires, both requests may arrive at `/api/v1/auth/refresh` simultaneously with the same refresh token. Without database-level atomicity, both succeed: the first rotates the token (marks old as used, issues new), but the second reads the "old" record before the first transaction commits and also succeeds — issuing a second valid refresh token. The reuse-detection invariant is silently violated.

**Why it happens:**
Naive implementations use a plain JPA `findByToken(token)` + check `used == false` + update as separate non-atomic steps. Under concurrent load (especially with network retries from mobile clients), the time window between read and update is exploited.

**How to avoid:**
Use a pessimistic write lock on the refresh token lookup, or use an atomic CAS update with `@Version` for optimistic locking:

```kotlin
// Option A: pessimistic lock (simpler, correct for low concurrency)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM RefreshToken r WHERE r.token = :token")
fun findByTokenForUpdate(token: String): RefreshToken?

// Option B: @Version on entity (throws OptimisticLockingFailureException on race)
@Entity
class RefreshToken(
    @Version val version: Long = 0,
    ...
)
```

Wrap the entire rotate operation in `@Transactional`. On `OptimisticLockingFailureException`, return 401 — the client retries.

**Warning signs:**
- Load tests with concurrent refresh calls succeed more than once per token
- Refresh token `used` column shows duplicate updates in logs
- Integration tests with two simultaneous refresh calls both return 200

**Phase to address:**
Refresh token rotation implementation phase

---

### Pitfall 4: Apple Sign In Sends User Name and Email Only on the First Login

**What goes wrong:**
Apple only returns `given_name`, `family_name`, and `email` in the ID token on the very first sign-in for a given Apple ID + app combination. Every subsequent sign-in returns only the `sub` claim. If the backend creates the user record asynchronously, or if the initial request fails after the client sends the token (e.g., network timeout), the user data is permanently lost. The user cannot re-authenticate with their name unless they revoke the app in Apple ID settings and re-authorize.

**Why it happens:**
Apple's design prioritizes privacy — it does not expose the name repeatedly. Developers discover this only after production users complain about missing names.

**How to avoid:**
- Extract name and email from the ID token's claims payload during the first Apple sign-in and persist them immediately within the same transaction that creates the user record
- Do not separate user-creation and claim-extraction into async steps
- Accept the possibility that `email` may be a private relay address (e.g., `xyz@privaterelay.appleid.com`) — do not require it to be a real email
- Store the Apple `sub` claim as the `providerId` — it is the only stable identifier across logins

**Warning signs:**
- User entity has empty `name` for Apple-authenticated users who signed in more than once
- Logs show Apple token decoded successfully but `given_name` / `family_name` are null on second login
- Emails in the format `*@privaterelay.appleid.com` appearing in user records

**Phase to address:**
Apple ID token verification implementation phase

---

### Pitfall 5: Using Email as the User Identity Key Across Google and Apple

**What goes wrong:**
Keying user records by email address across both providers causes silent account takeovers. A user who signed up via Google (`user@gmail.com`) can be matched to an Apple user who happens to use the same email — even if they are different people. Additionally, Apple may provide a private relay email (`random@privaterelay.appleid.com`), which changes per app. Google warns in its official docs: "Do not use email as user identifier; use `sub`."

**Why it happens:**
Email feels intuitive as a join key. The project has a single `User` entity — developers want one row per real person, so they try to unify by email.

**How to avoid:**
- Primary lookup key must be `(provider, providerId)` — the immutable `sub` claim from each provider
- `email` is a display attribute only, not an identity key
- Do NOT automatically merge a Google record and an Apple record with the same email without explicit user confirmation
- Store `provider` and `providerId` as a unique composite constraint on the user table

**Warning signs:**
- `findByEmail(...)` is the lookup in the auth flow instead of `findByProviderAndProviderId(...)`
- Users report being logged into someone else's account
- Apple relay emails like `*@privaterelay.appleid.com` being stored and used for lookup

**Phase to address:**
User entity and auth flow design phase

---

### Pitfall 6: Not Validating the `aud` Claim on Google and Apple ID Tokens

**What goes wrong:**
Skipping audience (`aud`) validation means your backend will accept ID tokens issued to any Google/Apple app — not just yours. An attacker who obtains a valid Google ID token for a different app can replay it against your backend and authenticate as that user. Google explicitly warns: "If multiple clients access the backend, also manually verify the `aud` claim."

**Why it happens:**
The token signature verifies correctly, so developers assume it is valid. Audience checking is a separate step that is easy to skip.

**How to avoid:**
- For Google: configure `GoogleIdTokenVerifier.Builder().setAudience(listOf(GOOGLE_CLIENT_ID))` — reject tokens where `aud` does not match your Android/iOS/web client ID
- For Apple: validate that `aud` equals your Apple Services ID (the bundle ID registered for Sign in with Apple)
- For your own JWTs: always set `aud` in issued tokens and validate it in `JwtAuthenticationFilter`

**Warning signs:**
- Token verification only checks signature and expiry — no audience check in code
- `GoogleIdTokenVerifier` built without `.setAudience(...)`
- Tokens from other apps/clients accepted successfully in integration tests

**Phase to address:**
Google/Apple ID token verification phase

---

### Pitfall 7: Hardcoding or Packaging the RSA Private Key Inside the JAR

**What goes wrong:**
Generating an RSA key pair at application startup and storing the .jks file inside `src/main/resources` means every environment (dev, staging, prod) uses different keys unless you override — but worse, if the JAR is extracted or distributed, the private key is compromised. Tokens signed in one instance cannot be verified by a restarted instance (keys regenerated on every startup if not persisted).

**Why it happens:**
The "runtime fallback" key generation pattern is convenient for dev. Developers forget to configure a persistent key for staging/production.

**How to avoid:**
- The `.jks` file must NEVER be committed to source control
- Production startup must fail fast if the keystore environment variable is not set — no silent fallback to generated keys
- The "runtime fallback" is acceptable only in local development (controlled by `spring.profiles.active=dev`)
- `.env.example` must document `JWT_KEYSTORE_PATH`, `JWT_KEYSTORE_PASSWORD`, `JWT_KEY_ALIAS` as required

**Warning signs:**
- `keystore.jks` file appears in `git status` as tracked
- Application starts successfully in a production profile without keystore configuration
- Token verification fails after pod restart (keys regenerated)

**Phase to address:**
RSA keystore and JWT infrastructure phase

---

### Pitfall 8: CORS Pre-flight (OPTIONS) Requests Blocked Before JWT Filter Runs

**What goes wrong:**
Pre-flight OPTIONS requests from browsers do not carry an `Authorization: Bearer` header. If the `JwtAuthenticationFilter` rejects requests with missing tokens (instead of passing them through unauthenticated), OPTIONS requests return 401 or 403 before the CORS headers are set. The browser then blocks the actual request. This is a silent misconfiguration — it looks like a CORS error but is actually an auth ordering problem.

**Why it happens:**
CORS configuration must be applied at the `CorsFilter` level (which runs before Spring Security filters) or via `http.cors { }` in `SecurityFilterChain`. If CORS is configured only at the MVC level (`WebMvcConfigurer`), Spring Security processes the request first and rejects OPTIONS.

**How to avoid:**
- Configure CORS inside `SecurityFilterChain` using `http.cors { it.configurationSource(...) }` — this registers the `CorsFilter` at the correct position in the filter chain
- `JwtAuthenticationFilter` must NOT return 401 for missing tokens — it sets authentication to null and lets Spring Security's `AuthorizationFilter` decide
- Ensure `/api/v1/auth/**` endpoints are `permitAll()` — pre-flight to these must not require auth

**Warning signs:**
- Browser console shows CORS errors on POST requests but OPTIONS returns 4xx
- Postman works (no pre-flight) but browser client fails
- OPTIONS requests appear in server logs with 401 or 403 responses

**Phase to address:**
Spring Security stateless configuration phase

---

### Pitfall 9: Jackson 2 vs Jackson 3 Confusion in Spring Boot 4 + Spring Security 7

**What goes wrong:**
Spring Boot 4 ships with Jackson 3 (the `tools.jackson` namespace). Spring Security 7 migrated its Jackson support to Jackson 3 as well (`SecurityJacksonModules` instead of `SecurityJackson2Modules`). Projects that add `spring-security-data` or copy Jackson serializer configuration from Spring Boot 3 examples will have the wrong import namespace, causing `ClassNotFoundException` or silent serialization failures.

**Why it happens:**
All existing tutorials use Spring Boot 3 / Jackson 2. The ecosystem has not caught up with Spring Boot 4 examples yet.

**How to avoid:**
- Use `tools.jackson.core:jackson-databind` (Jackson 3) — do not add `com.fasterxml.jackson.core:jackson-databind`
- Use `SecurityJacksonModules` (not `SecurityJackson2Modules`) for security-related Jackson configuration
- Check import statements: correct namespace is `tools.jackson.*`, not `com.fasterxml.jackson.*`

**Warning signs:**
- `ClassNotFoundException: com.fasterxml.jackson.databind.ObjectMapper` at startup (wrong Jackson on classpath)
- `NoSuchBeanDefinitionException` for Jackson auto-configuration
- Compile errors where code imports `com.fasterxml.jackson.*` — these are Jackson 2 imports

**Phase to address:**
Foundation and infrastructure setup phase (early)

---

### Pitfall 10: Refresh Token Reuse Detection Locks Out Legitimate Users on Network Retries

**What goes wrong:**
Mobile clients on flaky networks frequently retry failed requests. If a `/auth/refresh` call succeeds on the server but the response is lost in transit, the client retries with the same (now already-rotated) refresh token. The reuse-detection logic correctly identifies this as a potential replay, revokes all tokens for the user, and forces re-authentication. The legitimate user is locked out.

**Why it happens:**
Reuse detection is binary — "used token = theft" — but real mobile networks cause legitimate double-presentation of refresh tokens.

**How to avoid:**
Implement a brief grace window: if a refresh token is re-presented within a short time (e.g., 30 seconds) after being rotated, and the IP/device fingerprint matches, return the newly issued token rather than revoking everything. Auth0's documentation acknowledges this exact tradeoff.

Alternatively: require clients to implement idempotency via a unique `requestId` on the refresh call, deduplicated server-side for 60 seconds.

The simpler acceptable approach for v1: log the reuse-detection event, revoke all tokens, but return a clear `403` with a body of `{ "error": "TOKEN_REUSE_DETECTED" }` so clients can show a helpful "Please log in again" message rather than an opaque error.

**Warning signs:**
- Support tickets from mobile users complaining of random forced logouts
- Logs showing `TOKEN_REUSE_DETECTED` for the same user repeatedly at short intervals
- Users on poor mobile connections affected disproportionately

**Phase to address:**
Refresh token rotation implementation phase

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Generate RSA keys at startup (no keystore file) | Zero configuration for dev | Keys lost on restart; tokens invalid after redeploy; cannot key-rotate | Dev profile only — never prod |
| Hard-code allowed CORS origins as `*` | No CORS configuration needed | Any origin can call the API; security boundary removed | Never — configure allowed origins from env var |
| Use `email` as user lookup key | Simpler JOIN logic | Account takeover across providers; breaks with Apple relay emails | Never |
| Skip `aud` claim validation | Fewer lines of verifier config | Any app's Google/Apple token accepted; replay attacks possible | Never |
| Store refresh token as plain UUID without TTL index | Simple schema | Expired tokens accumulate; table grows unbounded; slow lookups | Never — add DB TTL or scheduled cleanup |
| Use `SessionCreationPolicy.IF_REQUIRED` (default) instead of `STATELESS` | No explicit config | Sessions created silently; stateless contract violated; state leaks | Never — set `STATELESS` explicitly |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Google ID token verification | Using the tokeninfo HTTP endpoint (`https://oauth2.googleapis.com/tokeninfo`) in production | Use `GoogleIdTokenVerifier` (official Java library) — the endpoint is rate-limited and has SLA issues |
| Google ID token verification | Not setting audience in verifier constructor | `GoogleIdTokenVerifier.Builder().setAudience(listOf(GOOGLE_CLIENT_ID)).build()` |
| Apple JWKS | Hard-coding Apple's public keys | Fetch from `https://appleid.apple.com/auth/keys` at startup and cache with TTL — Apple rotates keys |
| Apple JWKS | Not matching `kid` (key ID) from JWT header to JWKS entry | Find the key by `kid` header value in the JWKS; reject if no matching key found |
| Apple Sign In | Expecting name/email on every login | Only available on first authorization; persist from initial token payload immediately |
| Spring Authorization Server | Using `@Import(OAuth2AuthorizationServerConfiguration.class)` | Wire only `JWKSource` + `NimbusJwtEncoder` directly — no full auto-config |
| Spring Security 7 request matching | `AntPathRequestMatcher` / `MvcRequestMatcher` | Replace with `PathPatternRequestMatcher.withDefaults().matcher(...)` — old matchers removed |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Verifying Google/Apple JWKS on every request | Latency spike on every authenticated request; external HTTP calls in the hot path | Cache JWKS in memory with TTL (60 min); only re-fetch on cache miss or key-not-found | From first request under load |
| No database index on refresh token lookup column | Slow `/auth/refresh` at scale; full table scans on `refresh_tokens.token` | Add `@Index` on `token` column in Hibernate entity / Flyway migration | ~10k+ rows |
| Loading full `User` entity in JWT filter on every request | N+1 DB calls per authenticated request | JWT filter should only validate the token signature and populate `Authentication` from claims — do NOT hit the DB in the filter | Under any meaningful load |
| Unbounded refresh token table growth | Slow `findByToken` queries; disk pressure | Add scheduled cleanup of expired/revoked tokens, or PostgreSQL TTL-based partitioning | After weeks of production use |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Skipping `iss` claim validation on Google/Apple tokens | Tokens from other issuers accepted — any valid JWT with right structure passes | Verify `iss == "accounts.google.com"` for Google; `iss == "https://appleid.apple.com"` for Apple |
| Using HS256 (symmetric) for your own JWTs | Private key must be shared with any verifier; one compromise = all tokens forged | Use RS256 (asymmetric) with the RSA keystore as specified in requirements |
| Access token expiry too long (> 1 hour) | Stolen access tokens usable for extended periods; no revocation possible | Keep at 15 minutes (already specified); refresh token rotation handles seamless renewal |
| Storing refresh tokens in plaintext in DB | DB dump exposes all active sessions | Hash refresh token value in DB (store `SHA-256(token)`); compare hash on lookup |
| Returning full stack traces in JWT validation errors | Attacker learns internal class names, library versions | Return generic `{ "error": "INVALID_TOKEN", "message": "...", "status": 401 }` — no stack traces |
| Not clearing SecurityContext after request in stateless mode | Security context leaks between requests if the same thread is reused (less likely with Virtual Threads but still a risk) | Set `SessionCreationPolicy.STATELESS`; Spring Security clears context automatically — verify in config |
| Accepting Apple `email_verified: false` emails | Unverified emails used as identity; email-spoofing risk | Check `email_verified` claim; only store email if verified; use `sub` as primary identity regardless |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Revoking ALL tokens on any reuse-detection event | Legitimate users on flaky networks suddenly logged out | Log the event, revoke, return `403 TOKEN_REUSE_DETECTED` with clear message; inform client to show "Please log in again" |
| Returning `401 Unauthorized` with empty body for expired access tokens | Mobile client cannot distinguish "expired token" from "invalid token" from "server error" | Return `{ "error": "TOKEN_EXPIRED", "message": "Access token expired", "status": 401 }` — clients use the error code to trigger refresh |
| No distinction between `TOKEN_EXPIRED` and `TOKEN_INVALID` in error responses | Clients cannot auto-refresh on expiry — they treat all 401s as permanent auth failure | Use consistent error codes in the `error` field so clients can branch behavior |

---

## "Looks Done But Isn't" Checklist

- [ ] **Google verification:** `aud` claim validated against your client ID(s) — not just signature and `exp`
- [ ] **Apple verification:** JWKS fetched from Apple's endpoint at startup (not hardcoded) and `aud` validated against your Services ID
- [ ] **Apple first-login:** Name and email persisted atomically on first sign-in — tested by calling Apple auth twice with the same token and verifying second call still returns a valid user
- [ ] **Refresh rotation:** Database operation wrapped in `@Transactional`; tested with concurrent requests — only one succeeds
- [ ] **Token reuse detection:** All tokens for user revoked on reuse; tested by presenting a used refresh token
- [ ] **Stateless:** `SessionCreationPolicy.STATELESS` configured; verified no `JSESSIONID` cookie in responses
- [ ] **CSRF disabled:** `http.csrf { it.disable() }` present — stateless JWT APIs do not need CSRF protection
- [ ] **CORS pre-flight:** OPTIONS requests to all `/api/v1/**` paths return 200 with correct `Access-Control-Allow-*` headers without an `Authorization` header
- [ ] **RSA keystore:** Application fails fast with a clear error if keystore is not configured in prod profile — no silent key generation
- [ ] **Spring Authorization Server endpoints:** No `/oauth2/token`, `/oauth2/authorize`, etc. registered — confirmed by requesting these paths returns 404
- [ ] **Error responses:** All 401/403/400 responses use `{ error, message, status }` format — no stack traces in body
- [ ] **`sub` as identity:** User lookup uses `provider` + `providerId` (`sub`) — not email

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Full auth server endpoints accidentally exposed | LOW | Remove `@Import(OAuth2AuthorizationServerConfiguration.class)`; confirm endpoints return 404 |
| RSA key committed to repo | HIGH | Rotate key immediately; revoke all issued tokens (delete all refresh tokens from DB); generate new keystore; redeploy |
| Email used as identity key + data in production | HIGH | Add `provider` + `providerId` columns; backfill from existing Google/Apple `sub` claims; migrate lookup logic; requires user re-verification |
| Unbounded refresh token table (hundreds of thousands of rows) | MEDIUM | Add scheduled cleanup job; add partial index on `(used = false, expires_at > now())` for fast active-token lookups |
| AntPathRequestMatcher used in Spring Security 7 | LOW | Replace with `PathPatternRequestMatcher.withDefaults().matcher(...)` in all security config |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Spring Authorization Server over-configuration | Phase: Spring Security + JWT infrastructure | Verify no `/oauth2/*` endpoints accessible; no `RegisteredClientRepository` bean required |
| AntPathRequestMatcher removed in SS7 | Phase: Spring Security stateless configuration | Integration test confirms `permitAll()` routes return 200, protected routes return 401 |
| Refresh token race condition | Phase: Refresh token rotation implementation | Concurrent integration test — two simultaneous refresh calls; verify only one succeeds |
| Apple first-login user data loss | Phase: Apple ID token verification | Test with Apple token presenting twice; user has name from first call only — stored correctly |
| Email as identity key | Phase: User entity design | Code review: `findByProvider AndProviderId` is the auth lookup; no `findByEmail` in auth path |
| Missing `aud` claim validation | Phase: Google/Apple token verification | Unit test with ID token from wrong client ID — verification must reject it |
| Hardcoded RSA key in JAR | Phase: RSA keystore infrastructure | Startup test with no keystore config in `prod` profile — application must not start |
| CORS OPTIONS blocked | Phase: Spring Security configuration | Integration test: OPTIONS request to `/api/v1/auth/google` without auth header returns 200 |
| Jackson 2 vs Jackson 3 conflict | Phase: Foundation setup | Application starts successfully; no `ClassNotFoundException` on Jackson namespace |
| Refresh token reuse locks legit users | Phase: Refresh token rotation | Load test with retry on network timeout; verify error code is `TOKEN_REUSE_DETECTED` not 500 |

---

## Sources

- Spring Security 7.0 Migration Guide — https://docs.spring.io/spring-security/reference/migration/index.html (verified 2026-03-01)
- Spring Authorization Server Configuration Model — https://docs.spring.io/spring-authorization-server/reference/configuration-model.html (verified 2026-03-01)
- Google: Verify the Google ID Token — https://developers.google.com/identity/gsi/web/guides/verify-google-id-token (verified 2026-03-01)
- Auth0: Refresh Token Rotation — https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation (verified 2026-03-01)
- Apple Developer Forums: User info only on first login — https://developer.apple.com/forums/thread/121496 (MEDIUM confidence — community source confirmed by multiple forum threads)
- Spring Security GitHub Issue #15040: Virtual Threads SecurityContext — https://github.com/spring-projects/spring-security/issues/15040
- Spring Framework GitHub Issue #31839: CORS preflight rejection — https://github.com/spring-projects/spring-framework/issues/31839
- Baeldung: Fixing 401s with CORS Preflights and Spring Security — https://www.baeldung.com/spring-security-cors-preflight
- Spring Security GitHub Issue #16417: PathPatternRequestMatcher migration — https://github.com/spring-projects/spring-security/issues/16417
- Race Conditions in JWT Refresh Token Rotation — https://developers.apideck.com/guides/refresh-token-race-condition (MEDIUM confidence — industry guide)
- Concurrency problems refreshing OAuth2 tokens (Spring legacy issue) — https://github.com/spring-attic/spring-security-oauth/issues/834

---
*Pitfalls research for: Spring Boot JWT Auth — Google/Apple ID token verification, Spring Security 7, refresh token rotation*
*Researched: 2026-03-01*
