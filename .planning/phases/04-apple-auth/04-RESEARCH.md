# Phase 4: Apple Auth - Research

**Researched:** 2026-03-01
**Domain:** Apple Sign In identity token verification / JWKS-based JWT validation / first-login user data atomicity / private relay email handling
**Confidence:** HIGH (core token verification stack verified via Spring Security official docs; Apple-specific token behavior verified via official Apple Developer documentation and multiple authoritative secondary sources)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| AUTH-02 | Client can send Apple ID token to POST /api/v1/auth/apple and receive JWT access + refresh tokens | `NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys")` with `iss`/`aud`/`exp` validators decodes the Apple identity token. On success, call find-or-create user with APPLE provider, then `JwtTokenService.generateAccessToken()` + `RefreshTokenService.createToken()`. Return `{ accessToken, refreshToken }`. |
| AUTH-04 | Backend verifies Apple ID token via Apple's JWKS endpoint with `iss`, `aud`, `exp` claim validation | `NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys").jwsAlgorithm(SignatureAlgorithm.RS256).build()` combined with `DelegatingOAuth2TokenValidator` using `JwtTimestampValidator`, `JwtIssuerValidator("https://appleid.apple.com")`, and a custom `JwtClaimValidator` for `aud`. NimbusJwtDecoder auto-caches Apple's JWKS and fetches new keys on `kid` miss. |
| AUTH-05 | Apple first-sign-in user data (name, email) is persisted atomically — never lost | Name (given_name + family_name) is NOT in the identity token — the iOS client must pass it separately in the request body. Email IS in the identity token on first login (may be absent on subsequent logins). The `@Transactional` find-or-create pattern (already used for Google) handles atomicity. The request DTO must accept `givenName`/`familyName` as optional fields. |
| AUTH-06 | Apple private relay emails (`*@privaterelay.appleid.com`) are accepted without failure | `privaterelay.appleid.com` is a syntactically valid email domain. Hibernate Validator's `@Email` passes it without issue. The only failure risk is if the backend uses `@Email(regexp=...)` with a restrictive pattern or if a custom domain-blocklist validator is applied. Neither exists in the current codebase. AUTH-06 is satisfied automatically once no restrictive `@Email` is placed on the Apple auth DTO email field. |
</phase_requirements>

---

## Summary

Phase 4 adds Apple Sign In alongside the already-wired Google auth. The core technical problem is Apple identity token verification: unlike Google (which uses `GoogleIdTokenVerifier` from a dedicated library), Apple's identity tokens are standard JWTs signed with RS256 using keys from Apple's JWKS endpoint (`https://appleid.apple.com/auth/keys`). The right tool for this is `NimbusJwtDecoder.withJwkSetUri()` from Spring Security (already on classpath) configured with explicit `iss`, `aud`, and `exp` validators. No new library dependency is needed.

The two non-trivial Apple-specific behaviors that must be planned carefully are: (1) **first-login user data** — Apple puts email in the JWT on first login, but the user's name (given_name/family_name) is NEVER in the JWT; the iOS client sends name separately in the request body and the backend must accept and persist it atomically on first sign-in; (2) **subsequent-login user data** — on all logins after the first, email may also be absent from the JWT, so the backend must look up the user by `sub` (the stable Apple User ID) alone, without requiring email. This is the key design difference from Google auth, where email is always present.

Private relay emails (`*@privaterelay.appleid.com`) are syntactically valid email addresses and pass Hibernate Validator's `@Email` check automatically. AUTH-06 is satisfied by not adding an email-format constraint on the Apple auth request DTO. The `aud` claim in Apple tokens contains the iOS app's **bundle ID** (e.g., `com.example.myapp`), not a Services ID, which is a common source of confusion. The backend must be configured to accept the correct audience value for the mobile client.

**Primary recommendation:** Use `NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys")` as a Spring `@Bean` in a new `AppleAuthConfig.kt`, add a `DelegatingOAuth2TokenValidator` with issuer + timestamp + audience validators, wire a new `AppleAuthService` following the same pattern as `GoogleAuthService`, and extend the `AuthController` with a `POST /api/v1/auth/apple` endpoint. Add `findOrCreateAppleUser()` to `UserService` that handles email-absent subsequent logins gracefully.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-security-oauth2-jose` | managed by Boot 4.0.3 BOM (Spring Security 7.x) | `NimbusJwtDecoder.withJwkSetUri()` — fetches Apple JWKS, verifies RS256 JWT signature | Already on classpath via `spring-boot-starter-oauth2-authorization-server`; no new dependency needed |
| `spring-security-oauth2-resource-server` | managed by Boot 4.0.3 BOM | `JwtTimestampValidator`, `JwtIssuerValidator`, `JwtClaimValidator`, `DelegatingOAuth2TokenValidator` — composable claim validators | Already on classpath; standard Spring Security approach for JWKS-backed JWT validation |
| `spring-boot-starter-validation` | managed by Boot 4.0.3 BOM | Jakarta Bean Validation for Apple auth request DTO | Already on classpath (Phase 2) |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.security.*` (JDK 24) | JDK 24 | SHA-256 hashing, SecureRandom for refresh tokens | Already used in `RefreshTokenService` |
| JPA / Hibernate | managed by Boot 4.0.3 BOM | `findOrCreateAppleUser()` in `UserService` | Already on classpath; no new work |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `NimbusJwtDecoder.withJwkSetUri()` (Spring Security built-in) | Nimbus JOSE+JWT `RemoteJWKSet` directly | `NimbusJwtDecoder` wraps Nimbus with Spring's `OAuth2TokenValidator` composable pipeline — simpler than raw Nimbus; auto-caches JWKS with key rotation on `kid` miss |
| `NimbusJwtDecoder.withJwkSetUri()` | Custom manual RS256 verification against Apple JWKS | More code, more risk of getting key caching wrong; defeats the purpose of using a framework |
| Separate Apple JWT library (e.g., `com.nimbusds:nimbus-jose-jwt` directly) | Spring Security built-in JWKS decoder | An extra dependency that duplicates what's already on classpath; unnecessary |

**Installation:** No new Maven dependencies required. All needed classes are already on the classpath via `spring-boot-starter-oauth2-authorization-server`.

---

## Architecture Patterns

### Recommended Project Structure

Phase 4 adds minimal new files, mirroring the Google auth structure:

```
src/main/kotlin/kz/innlab/template/
├── config/
│   ├── GoogleAuthConfig.kt         # Phase 3: DONE
│   └── AppleAuthConfig.kt          # Phase 4: NEW — NimbusJwtDecoder @Bean for Apple JWKS
├── authentication/
│   ├── GoogleAuthService.kt        # Phase 3: DONE
│   ├── AppleAuthService.kt         # Phase 4: NEW — Apple identity token → user + tokens
│   ├── AuthController.kt           # Phase 3: DONE — EXTEND with POST /apple endpoint
│   └── dto/
│       ├── AuthRequest.kt          # Phase 3: DONE — reused for Apple (idToken field)
│       └── AppleAuthRequest.kt     # Phase 4: NEW — idToken + optional givenName/familyName
└── user/
    └── UserService.kt              # Phase 3: DONE — ADD findOrCreateAppleUser()
```

### Pattern 1: AppleAuthConfig — NimbusJwtDecoder Bean for Apple JWKS

**What:** Singleton `NimbusJwtDecoder` that fetches Apple's JWKS and validates RS256 signatures with `iss`, `aud`, `exp` validators. Registered as a Spring `@Bean` (NOT named `jwtDecoder` to avoid replacing the existing RS256 decoder used by the resource server).
**When to use:** AUTH-04 — all Apple token verification routes through this single bean.

```kotlin
// Source: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
// File: config/AppleAuthConfig.kt
package kz.innlab.template.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm

private const val APPLE_JWKS_URI = "https://appleid.apple.com/auth/keys"
private const val APPLE_ISSUER = "https://appleid.apple.com"

@Configuration
class AppleAuthConfig(
    @Value("\${app.auth.apple.bundle-id}")
    private val appleBundleId: String
) {

    @Bean(name = ["appleJwtDecoder"])
    fun appleJwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder
            .withJwkSetUri(APPLE_JWKS_URI)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build()

        val audienceValidator: OAuth2TokenValidator<Jwt> =
            JwtClaimValidator(JwtClaimNames.AUD) { aud: List<String>? ->
                aud != null && aud.contains(appleBundleId)
            }

        val validator = DelegatingOAuth2TokenValidator(
            JwtTimestampValidator(),            // validates exp (and nbf if present)
            JwtIssuerValidator(APPLE_ISSUER),   // validates iss = "https://appleid.apple.com"
            audienceValidator                   // validates aud contains bundle ID
        )
        decoder.setJwtValidator(validator)

        return decoder
    }
}
```

In `application.yaml` (under `app.auth`):
```yaml
app:
  auth:
    google:
      client-id: ${GOOGLE_CLIENT_ID:test-client-id}
    apple:
      bundle-id: ${APPLE_BUNDLE_ID:com.example.app}
    refresh-token:
      expiry-days: ${REFRESH_TOKEN_EXPIRY_DAYS:30}
```

In `.env.example`:
```
APPLE_BUNDLE_ID=com.example.myapp
```

**Key points:**
- Bean name is `appleJwtDecoder` (NOT `jwtDecoder`) — prevents collision with the existing `JwtDecoder` bean in `RsaKeyConfig` used by the OAuth2 Resource Server for access token validation.
- `jwsAlgorithm(SignatureAlgorithm.RS256)` explicitly — `NimbusJwtDecoder.withJwkSetUri()` defaults to RS256 only, but being explicit is safer and documents intent.
- `JwtTimestampValidator()` uses a default 60-second clock skew (Spring Security 6+ default). This is appropriate for production.
- `JwtIssuerValidator` checks `iss` == `"https://appleid.apple.com"` exactly.
- Apple JWKS (`https://appleid.apple.com/auth/keys`) is fetched lazily on first token verification. NimbusJwtDecoder caches the JWKS internally (Nimbus-level `RemoteJWKSet` with default 5-minute TTL). On encountering an unknown `kid`, it immediately re-fetches (handles Apple key rotation transparently).

### Pattern 2: Apple Identity Token Claims — What's Present and When

**What:** The Apple identity token is a standard JWT with these claims:

| Claim | Type | Always Present | Value |
|-------|------|----------------|-------|
| `iss` | String | Yes | `"https://appleid.apple.com"` |
| `aud` | String/Array | Yes | The iOS app bundle ID (e.g., `"com.example.myapp"`) |
| `exp` | Long | Yes | Epoch seconds — expiry time |
| `iat` | Long | Yes | Epoch seconds — issued at |
| `sub` | String | Yes | Stable Apple User ID (team-scoped, never changes) |
| `email` | String | First login only (may be absent on subsequent logins) | User's real email OR private relay email |
| `email_verified` | String/Bool | When email is present | `"true"` or `true` |
| `is_private_email` | String/Bool | When user hid email | `"true"` or `true` |
| `auth_time` | Long | Yes | Epoch seconds of authentication |

**Critical:** `given_name` and `family_name` are NEVER in the Apple identity token JWT. The iOS client receives the user's full name from `ASAuthorizationAppleIDCredential.fullName` (an `NSPersonNameComponents` object) separately — NOT from the JWT. The client must pass name data alongside the identity token in the request body.

**The `sub` claim** is team-scoped: the same Apple user signing into different apps within the same Apple Developer team account gets the same `sub` value. This is the stable, permanent user identifier to use as `providerId`.

### Pattern 3: AppleAuthRequest DTO — Handling First-Login Name

**What:** A new request DTO that accepts the identity token plus optional name fields from the iOS client.

```kotlin
// File: authentication/dto/AppleAuthRequest.kt
package kz.innlab.template.authentication.dto

import jakarta.validation.constraints.NotBlank

data class AppleAuthRequest(
    @field:NotBlank(message = "ID token is required")
    val idToken: String = "",

    // Name fields: only present on first sign-in (iOS sends from ASAuthorizationAppleIDCredential.fullName)
    // Absent on subsequent sign-ins — backend must handle null gracefully
    val givenName: String? = null,
    val familyName: String? = null
)
```

**Why a separate DTO (not reuse `AuthRequest`):** `AuthRequest` has only `idToken`. Apple requires optional `givenName`/`familyName` fields. Adding them to `AuthRequest` would pollute the Google flow and create ambiguity.

**No `@Email` on email:** The email for Apple auth is extracted from the identity token JWT claims, NOT from the request body. This sidesteps any `@Email` annotation issue entirely. AUTH-06 is automatically satisfied.

### Pattern 4: Apple Claims Extraction — Handling Absent Email on Subsequent Logins

**What:** After the Apple JWT decodes successfully, extract claims. `email` may be null on subsequent logins. Must not throw `BadCredentialsException` when email is absent — instead, look up the user by `sub` alone.

```kotlin
// File: authentication/AppleAuthService.kt
package kz.innlab.template.authentication

import kz.innlab.template.authentication.dto.AppleAuthRequest
import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.user.UserService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppleAuthService(
    private val appleJwtDecoder: JwtDecoder,        // injected by bean name "appleJwtDecoder"
    private val userService: UserService,
    private val jwtTokenService: JwtTokenService,
    private val refreshTokenService: RefreshTokenService
) {

    @Transactional
    fun authenticate(request: AppleAuthRequest): AuthResponse {
        // Throws JwtException (subtype of AuthenticationException) if token is invalid/expired/wrong-aud/wrong-iss
        val jwt = try {
            appleJwtDecoder.decode(request.idToken)
        } catch (ex: Exception) {
            throw BadCredentialsException("Invalid Apple ID token: ${ex.message}", ex)
        }

        val sub: String = jwt.subject
            ?: throw BadCredentialsException("Apple identity token missing sub claim")

        // Email is present on first login, absent on subsequent logins
        val email: String? = jwt.getClaimAsString("email")

        // Name is NEVER in the JWT — comes from the iOS client in the request body
        val givenName: String? = request.givenName?.takeIf { it.isNotBlank() }
        val familyName: String? = request.familyName?.takeIf { it.isNotBlank() }

        val fullName: String? = listOfNotNull(givenName, familyName)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        val user = userService.findOrCreateAppleUser(
            providerId = sub,
            email = email,
            name = fullName
        )

        val accessToken = jwtTokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)

        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
```

### Pattern 5: findOrCreateAppleUser — Handling Absent Email

**What:** Extension of `UserService` for Apple. Key difference from Google: email may be null on subsequent logins. The user is always looked up by `(APPLE, sub)` — never by email.

```kotlin
// Add to user/UserService.kt
@Transactional
fun findOrCreateAppleUser(
    providerId: String,
    email: String?,    // nullable — absent on subsequent Apple logins
    name: String?
): User {
    val existing = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, providerId)
    if (existing != null) {
        return existing  // Returning user — email absent is expected, name absent is expected
    }

    // First login: email SHOULD be present (Apple sends it on first auth)
    // If email is absent on first login (edge case), use a placeholder or throw
    val resolvedEmail = email
        ?: throw BadCredentialsException("Email not present in Apple identity token on first sign-in")

    return userRepository.save(
        User(
            email = resolvedEmail,
            provider = AuthProvider.APPLE,
            providerId = providerId
        ).also { user ->
            user.name = name  // May be null if iOS client didn't send name (e.g., name scope not requested)
        }
    )
}
```

**Key design decisions:**
- Existing user lookup is by `(APPLE, providerId)` — the `User` entity already has `findByProviderAndProviderId` in `UserRepository`. No schema or repository changes needed.
- For existing users: email and name are NOT updated on subsequent logins (user may have changed name in-app; Apple won't re-send; don't overwrite).
- For new users: email absent on first login throws `BadCredentialsException`. This is an edge case (scope configuration issue on the iOS client) and the appropriate response is 401 with a clear message.
- `email` column on `User` is `nullable = false` at the JPA level. A new APPLE user must have an email — if Apple's JWT has no email for a new user, we cannot create the account.

### Pattern 6: AuthController Extension — POST /apple Endpoint

**What:** Add the `/apple` endpoint to the existing `AuthController`. Inject `AppleAuthService`.

```kotlin
// Additions to authentication/AuthController.kt

// Constructor add:
private val appleAuthService: AppleAuthService

// New endpoint:
@PostMapping("/apple")
fun appleLogin(@Valid @RequestBody request: AppleAuthRequest): ResponseEntity<AuthResponse> {
    val response = appleAuthService.authenticate(request)
    return ResponseEntity.ok(response)
}
```

The existing `/refresh` and `/revoke` endpoints are unchanged. The existing `AuthRequest` DTO is unchanged (still used for `/google`).

### Pattern 7: JwtDecoder Bean Name Collision Avoidance

**What:** The existing `RsaKeyConfig.kt` registers a `JwtDecoder` bean for the OAuth2 Resource Server (used by `SecurityFilterChain` to validate Bearer access tokens). Adding a second unnamed `JwtDecoder` bean for Apple would cause Spring to fail with "expected single matching bean but found 2: jwtDecoder, appleJwtDecoder".

**How to avoid:**
1. Name the Apple decoder bean explicitly: `@Bean(name = ["appleJwtDecoder"])`
2. Inject it by name in `AppleAuthService`: `private val appleJwtDecoder: JwtDecoder` — Spring injects by the parameter name matching the bean name when there are multiple beans of the same type.
3. If ambiguity persists, add `@Qualifier("appleJwtDecoder")` to the `AppleAuthService` constructor parameter.

```kotlin
// In AppleAuthService constructor
@Service
class AppleAuthService(
    @Qualifier("appleJwtDecoder")
    private val appleJwtDecoder: JwtDecoder,
    // ...
)
```

### Anti-Patterns to Avoid

- **Reusing the resource server `jwtDecoder` bean for Apple token verification:** The resource server decoder is configured with the app's own RSA keypair for verifying the app's own JWT access tokens. It has a different issuer, audience, and algorithm configuration. Using it to verify Apple tokens will always fail.
- **Treating email as a required claim for Apple tokens:** Email is absent on all logins after the first. If the service throws `BadCredentialsException` when email is missing, returning users will get 401 permanently.
- **Treating name as present in the Apple identity token:** `given_name` and `family_name` are never in Apple JWTs. Trying to read them from `jwt.getClaim("given_name")` returns null always.
- **Adding `@Email` validation to the Apple request DTO on any email field:** The email comes from the JWT, not the request body. Even if it were in the request body, `privaterelay.appleid.com` addresses are syntactically valid and should never be rejected. AUTH-06 is satisfied by design.
- **Using `@Bean` without a name for the Apple JwtDecoder:** Spring auto-names unnamed beans by method name (`appleJwtDecoder` from `fun appleJwtDecoder()`). This works, but explicit `@Bean(name = ["appleJwtDecoder"])` makes intent clear and avoids subtle naming bugs.
- **Using `JwtDecoders.fromIssuerLocation("https://appleid.apple.com")` for auto-discovery:** Apple does NOT publish a standard OpenID Connect Discovery Document at `https://appleid.apple.com/.well-known/openid-configuration`. Using `fromIssuerLocation` will fail at startup with a connection error or incorrect metadata.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Apple JWKS fetching and RS256 verification | Custom HTTP call to `https://appleid.apple.com/auth/keys` + manual RSA verification | `NimbusJwtDecoder.withJwkSetUri()` | Handles JWKS fetch, key rotation on `kid` miss, RS256 signature verification, and claim validation pipeline — all battle-tested |
| JWT claim validation (`iss`, `aud`, `exp`) | Manual if/else on JWT string after Base64 decoding | `JwtIssuerValidator`, `JwtTimestampValidator`, `JwtClaimValidator` | Composable validators with correct clock skew handling (60s default), proper error types, and DelegatingOAuth2TokenValidator composition |
| JWKS key caching | Custom `Map<String, PublicKey>` with TTL | `NimbusJwtDecoder` built-in (Nimbus `RemoteJWKSet`) | Handles TTL-based refresh, concurrent access, and automatic re-fetch on unknown `kid` (Apple key rotation) |
| Apple user lookup | Query by email | `userRepository.findByProviderAndProviderId(APPLE, sub)` | `sub` is stable; email changes, may be absent; the existing `UserRepository` already supports this query |

**Key insight:** Phase 4 is almost entirely configuration and orchestration. The only new logic is the "null email on subsequent login" handling in `findOrCreateAppleUser`. Everything else is using existing infrastructure with different configuration values.

---

## Common Pitfalls

### Pitfall 1: JwtDecoder Bean Name Collision

**What goes wrong:** Spring context fails to start with "expected single matching bean but found 2" error when a second unnamed `JwtDecoder` bean is added.
**Why it happens:** `RsaKeyConfig.kt` already registers a `JwtDecoder` bean. Adding another in `AppleAuthConfig.kt` without naming it creates an ambiguous autowiring situation.
**How to avoid:** Always use `@Bean(name = ["appleJwtDecoder"])` and `@Qualifier("appleJwtDecoder")` in `AppleAuthService`.
**Warning signs:** `NoUniqueBeanDefinitionException` in Spring context initialization.

### Pitfall 2: Treating Email as Required on Subsequent Apple Logins

**What goes wrong:** `AppleAuthService` throws `BadCredentialsException("Email not present")` for any user whose email is absent from the token, blocking all returning users after first login.
**Why it happens:** Mirroring the Google pattern where email is always present. Apple only guarantees email on first login.
**How to avoid:** Check `existing = userRepository.findByProviderAndProviderId(APPLE, sub)` FIRST. If the user exists, return it immediately without requiring email. Only require email when creating a new user (first login).
**Warning signs:** All returning Apple users get 401 after their first session expires.

### Pitfall 3: NimbusJwtDecoder Throws JwtException, Not BadCredentialsException

**What goes wrong:** `appleJwtDecoder.decode(idToken)` throws `JwtException` (or its subtypes like `JwtValidationException`) when the token is invalid. The `GlobalExceptionHandler` only handles `BadCredentialsException` → 401. `JwtException` falls through to the catch-all → 500.
**Why it happens:** `NimbusJwtDecoder.decode()` throws `JwtException` (from Spring Security OAuth2), NOT `BadCredentialsException`. These are separate exception hierarchies.
**How to avoid:** Wrap the `decode()` call in a try/catch and rethrow as `BadCredentialsException`:
```kotlin
val jwt = try {
    appleJwtDecoder.decode(request.idToken)
} catch (ex: Exception) {
    throw BadCredentialsException("Invalid Apple ID token", ex)
}
```
**Warning signs:** Invalid Apple tokens return 500 instead of 401.

### Pitfall 4: Incorrect `aud` Claim Value — Bundle ID vs Services ID

**What goes wrong:** Apple token verification fails (400 or 401) for iOS clients because the `aud` claim in the token contains the iOS app bundle ID (e.g., `com.example.myapp`) while the backend validator is configured with a Services ID (e.g., `com.example.service`).
**Why it happens:** Apple generates different audience values depending on the client type. Native iOS apps use the app's bundle ID as the `aud` claim. Web apps using Sign In with Apple JS use a Services ID. The backend must match the value used by the client.
**How to avoid:** For a mobile-only template, configure `APPLE_BUNDLE_ID` as the bundle ID of the iOS app. Document in `.env.example` that this is the iOS bundle ID, not the Services ID.
**Warning signs:** `JwtValidationException: The iss or aud claims in the id_token are invalid` or `aud` mismatch in decoder logs.

### Pitfall 5: Using `JwtDecoders.fromIssuerLocation()` for Apple — Fails at Startup

**What goes wrong:** `JwtDecoders.fromIssuerLocation("https://appleid.apple.com")` attempts to fetch `https://appleid.apple.com/.well-known/openid-configuration`. Apple does not serve a standard OIDC Discovery Document at this URL (as of 2025). The call fails at application startup.
**Why it happens:** Apple implements Sign In with Apple as a partial OIDC provider — they publish keys at `/auth/keys` but the discovery document is either non-standard or unavailable via the standard well-known path.
**How to avoid:** Use `NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys")` directly — hardcode the known JWKS URI.
**Warning signs:** `IllegalStateException` or `IOException` at bean creation time referencing the openid-configuration URL.

### Pitfall 6: User.email Column Not Nullable — Create with Null Email Fails

**What goes wrong:** If a new user's Apple token has no email (rare but possible), `userRepository.save(User(email = null, ...))` fails with `ConstraintViolationException` because `email` is `@Column(nullable = false)` in the `User` entity.
**Why it happens:** The User entity (Phase 1) requires a non-null email. Apple may not provide email if the iOS client didn't request the email scope or if there's a scope configuration issue.
**How to avoid:** In `findOrCreateAppleUser`, when the user is new AND email is null, throw `BadCredentialsException("Email not present in Apple identity token on first sign-in")` — 401 response. This is the correct behavior: the iOS app is misconfigured. Document this in comments.
**Warning signs:** `ConstraintViolationException` from Hibernate when saving a new Apple user without email.

### Pitfall 7: Name Data Lost If Not Persisted Atomically

**What goes wrong:** iOS client sends `givenName`/`familyName` in the request body on first sign-in. If the service creates the user but fails to persist the name (crash, separate transaction, or ignoring the fields), the name is lost permanently — Apple never sends it again.
**Why it happens:** Apple only sends user name data on the first sign-in. Subsequent calls have no name fields. If the first call doesn't persist the name, it's gone.
**How to avoid:** Set `user.name = fullName` inside the same `@Transactional` call as the `userRepository.save()`. Since `findOrCreateAppleUser` is already `@Transactional`, this is automatically atomic — either the user is created with the name or nothing is saved.
**Warning signs:** Newly registered Apple users have null name in the database after first sign-in that included name data.

### Pitfall 8: `is_private_email` Type Coercion — String vs Boolean

**What goes wrong:** The Apple JWT `is_private_email` claim can be either a JSON string (`"true"`) or a JSON boolean (`true`). Code that reads `jwt.getClaim<Boolean>("is_private_email")` may fail with a `ClassCastException` if Apple sends it as a String.
**Why it happens:** Apple's token implementation is inconsistent about this claim's JSON type across different versions and scenarios.
**How to avoid:** This is a non-issue for AUTH-06 because the current design does NOT check `is_private_email` at all. The email from the token is accepted regardless of whether it's a private relay address. Only relevant if future code inspects this claim.

---

## Code Examples

Verified patterns from official sources:

### Complete AppleAuthConfig with All Validators

```kotlin
// File: config/AppleAuthConfig.kt
// Source: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
package kz.innlab.template.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm

private const val APPLE_JWKS_URI = "https://appleid.apple.com/auth/keys"
private const val APPLE_ISSUER = "https://appleid.apple.com"

@Configuration
class AppleAuthConfig(
    @Value("\${app.auth.apple.bundle-id}")
    private val appleBundleId: String
) {
    @Bean(name = ["appleJwtDecoder"])
    fun appleJwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder
            .withJwkSetUri(APPLE_JWKS_URI)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build()

        val audienceValidator: OAuth2TokenValidator<Jwt> =
            JwtClaimValidator(JwtClaimNames.AUD) { aud: List<String>? ->
                aud != null && aud.contains(appleBundleId)
            }

        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(),
                JwtIssuerValidator(APPLE_ISSUER),
                audienceValidator
            )
        )

        return decoder
    }
}
```

### UserService.findOrCreateAppleUser — Null-Safe Email Handling

```kotlin
// Add to user/UserService.kt
@Transactional
fun findOrCreateAppleUser(
    providerId: String,
    email: String?,   // Nullable: absent on subsequent Apple logins
    name: String?
): User {
    // Always look up by (APPLE, sub) — sub is stable; email may be absent
    val existing = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, providerId)
    if (existing != null) {
        return existing  // Returning user — email/name absent on subsequent logins is expected
    }

    // New user (first login) — email must be present to create account
    val resolvedEmail = email
        ?: throw org.springframework.security.authentication.BadCredentialsException(
            "Email not present in Apple identity token on first sign-in — check iOS email scope configuration"
        )

    return userRepository.save(
        User(
            email = resolvedEmail,
            provider = AuthProvider.APPLE,
            providerId = providerId
        ).also { user ->
            user.name = name  // Null if iOS client didn't send name fields
        }
    )
}
```

### application.yaml — Apple Config Addition

```yaml
# Under app.auth (existing section):
app:
  auth:
    google:
      client-id: ${GOOGLE_CLIENT_ID:test-client-id}
    apple:
      bundle-id: ${APPLE_BUNDLE_ID:com.example.app}   # iOS app bundle ID (not Services ID)
    refresh-token:
      expiry-days: ${REFRESH_TOKEN_EXPIRY_DAYS:30}
```

### .env.example Addition

```
# Apple Sign In (Phase 4)
# Use the iOS app bundle ID (e.g., com.yourcompany.yourapp)
# For web clients, use the Services ID registered in Apple Developer Portal instead
APPLE_BUNDLE_ID=com.example.myapp
```

### Test Patterns — Mocking NimbusJwtDecoder for Apple Auth

```kotlin
// In AppleAuthIntegrationTest (to be designed in planning)
// Since NimbusJwtDecoder calls Apple's JWKS endpoint, tests must mock it.
// The cleanest pattern: register a @MockBean for JwtDecoder qualified as "appleJwtDecoder"

@SpringBootTest
@AutoConfigureMockMvc
class AppleAuthIntegrationTest {

    @MockitoBean(name = "appleJwtDecoder")  // Spring Boot 3.4+ annotation
    private lateinit var appleJwtDecoder: JwtDecoder

    // In each test: configure appleJwtDecoder mock to return a Jwt object
    // with desired claims (sub, email, aud, iss, exp)
}
```

**Note on `@MockitoBean` vs `@MockBean`:** Spring Boot 4.x (Spring Framework 6.2+) introduced `@MockitoBean` as the replacement for `@MockBean`. The project is on Spring Boot 4.0.3, so use `@MockitoBean`. Verify the exact annotation name against the installed Spring Boot version test classpath.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Fetching Apple JWKS manually and building RSA key | `NimbusJwtDecoder.withJwkSetUri()` with Spring Security validator pipeline | Spring Security 5.1 (stable since 5.x) | Eliminates custom key caching and rotation code |
| `JwtDecoders.fromIssuerLocation()` for auto-discovery | `NimbusJwtDecoder.withJwkSetUri()` with hardcoded JWKS URI | N/A — Apple never supported standard OIDC discovery reliably | Apple's OIDC discovery isn't standard; hardcode the known JWKS URI |
| Checking `is_private_email` claim to gate email acceptance | Accept all emails from Apple without domain checking | Best practice c. 2020+ | Private relay emails are valid; blocking them breaks user trust |
| Google-style "email always present" assumption | Nullable email with `sub`-based user lookup | Apple design (2019+) | Required for correct Apple Sign In implementation |
| `@MockBean` in Spring Boot tests | `@MockitoBean` (Spring Boot 3.4+/4.x) | Spring Boot 3.4 | `@MockBean` is deprecated in Spring Boot 4.x |

**Deprecated/outdated:**
- `@MockBean` in tests: deprecated in Spring Boot 3.4+, use `@MockitoBean`.
- `JwtDecoders.fromIssuerLocation("https://appleid.apple.com")`: does not work reliably for Apple.

---

## Open Questions

1. **`@MockitoBean` vs `@MockBean` annotation name in Spring Boot 4.0.3**
   - What we know: Spring Boot 3.4 introduced `@MockitoBean` to replace `@MockBean`. The project is on Spring Boot 4.0.3.
   - What's unclear: Whether `@MockBean` is still available as a deprecated fallback or fully removed.
   - Recommendation: During planning, verify against the actual test classpath. The `SecurityIntegrationTest.kt` doesn't use `@MockBean`, so this may not be discovered until the Apple auth test is written. The pattern `@MockitoBean(name = "appleJwtDecoder")` is the expected correct form for Spring Boot 4.x.

2. **JWKS cache TTL — Should it be customized?**
   - What we know: NimbusJwtDecoder's internal Nimbus `RemoteJWKSet` has a default 5-minute TTL. This is generally acceptable. Apple rotates keys infrequently.
   - What's unclear: Whether the 5-minute default causes noticeable latency in tests or local dev (repeated outbound HTTPS calls).
   - Recommendation: Do NOT add Caffeine or Spring Cache configuration for the Apple JWKS cache. The default Nimbus-level cache is sufficient. If test isolation requires avoiding real HTTPS calls, mock the `JwtDecoder` bean entirely.

3. **Email absent on first login — Is it actually possible in practice?**
   - What we know: Apple's documentation guarantees email is sent on first login when the iOS client requests the email scope. However, forum reports indicate edge cases where email is absent even on first login (scope encoding issues on the iOS client side).
   - What's unclear: The exact failure rate. Does the backend need a "fallback email" strategy?
   - Recommendation: Throw `BadCredentialsException` when email is absent for a new user. Document the likely cause (iOS client scope configuration). Do NOT generate placeholder emails — this creates data integrity problems.

4. **Multi-platform aud claim — iOS bundle ID vs web Services ID**
   - What we know: iOS native apps send the bundle ID as `aud`. Web clients send a Services ID. These are different strings.
   - What's unclear: Whether the template should support both (comma-separated `APPLE_BUNDLE_ID` env var that the config splits into a list).
   - Recommendation: Support a single audience value via `APPLE_BUNDLE_ID`. Document in `.env.example` and CLAUDE.md that multiple audiences can be supported by splitting on commas: `appleBundleId.split(",")`. Keep the initial implementation simple.

---

## Sources

### Primary (HIGH confidence)

- [Spring Security OAuth2 Resource Server JWT — NimbusJwtDecoder](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — `withJwkSetUri()`, `JwtTimestampValidator`, `JwtIssuerValidator`, `JwtClaimValidator`, `DelegatingOAuth2TokenValidator` — verified API as of Spring Security 7.x (Spring Boot 4.0.3)
- [NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder API docs](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder.html) — `jwsAlgorithm()`, `cache()`, `build()` methods confirmed
- Apple JWKS URI: `https://appleid.apple.com/auth/keys` — confirmed from multiple authoritative sources including Apple Developer Forums and Apple documentation metadata
- Apple issuer: `https://appleid.apple.com` — confirmed from Apple Developer documentation
- `aud` claim = iOS app bundle ID — confirmed from Apple Developer Forums thread and multiple Apple Sign In implementation guides

### Secondary (MEDIUM confidence)

- [sarunw.com — Sign in with Apple Tutorial, Part 3: Backend Token verification](https://sarunw.com/posts/sign-in-with-apple-3/) — Apple identity token claim structure; page not fully renderable via WebFetch (JS-required site) but claim structure confirmed via multiple other sources
- Apple Developer Forums (multiple threads) — email absent on subsequent logins; name never in JWT; `sub` claim stability; `aud` = bundle ID for iOS
- [Auth0 Blog — What is Sign in with Apple](https://auth0.com/blog/what-is-sign-in-with-apple-a-new-identity-provider/) — Identity token claims list, first-login behavior, `sub` claim team-scoping
- [Curity — Authenticate Using Sign in With Apple](https://curity.io/resources/learn/sign-in-with-apple/) — `aud` = bundle ID confirmed; JWKS URI confirmed

### Tertiary (LOW confidence — verify before use)

- `@MockitoBean(name = "appleJwtDecoder")` annotation pattern for Spring Boot 4.x tests — inferred from Spring Boot 3.4+ migration docs and `@MockBean` deprecation notice; not directly verified against Spring Boot 4.0.3 classpath. Verify during implementation.
- Nimbus `RemoteJWKSet` default 5-minute TTL — from WebSearch results referencing Spring Security GitHub issues; consistent across multiple sources but not directly verified against current Nimbus version.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `NimbusJwtDecoder.withJwkSetUri()` is the documented Spring Security pattern; no new dependencies required; verified via official Spring Security docs
- Architecture: HIGH — directly mirrors the Phase 3 Google auth pattern; key differences (null email, null name, bean name disambiguation) are well-documented and verified
- Pitfalls: HIGH — Pitfalls 1-6 are directly verified against known Spring Security behavior, Apple's documented token behavior, and existing codebase constraints; Pitfall 7 (name atomicity) is a logical consequence of the transaction design; Pitfall 8 is LOW (not actually exercised in current design)

**Research date:** 2026-03-01
**Valid until:** 2026-04-01 (Apple JWKS URI and claim structure have been stable since 2019; Spring Security 7.x patterns are stable; `@MockitoBean` naming may shift if Spring Boot 4.x changes naming)
