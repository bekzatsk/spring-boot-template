# Feature Research

**Domain:** Spring Boot JWT auth backend template (Google/Apple OAuth2 ID token verification)
**Researched:** 2026-03-01
**Confidence:** MEDIUM-HIGH (core auth patterns HIGH; Apple-specific nuances MEDIUM; Spring Boot 4 specifics LOW — limited production reports exist yet)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features any engineer picking up a "Spring Boot JWT auth template" expects to find working out of the box. Missing any of these = the template is not usable as a starting point.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Google ID token verification | Mobile/web clients already use Google Sign-In SDK; backend must validate the `id_token` they send | MEDIUM | Use `GoogleIdTokenVerifier` from `google-api-client`. Must validate `iss`, `aud`, `exp`, `sub`. Use `sub` (not email) as stable identifier. [Source: Google Developers](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token) |
| Apple ID token verification | Apple Sign In is mandatory on iOS apps that offer third-party login; backend must verify the JWT using Apple's JWKS endpoint | HIGH | Fetch keys from `https://appleid.apple.com/auth/keys`. Validate `iss=https://appleid.apple.com`, `aud=<bundle_id>`, `exp`. Apple only sends `user.name` and `user.email` on first sign-in — must persist on first auth. [Source: Apple Dev Docs](https://developer.apple.com/documentation/signinwithapple/authenticating-users-with-sign-in-with-apple) |
| JWT access token issuance (RS256) | Any JWT auth system issues signed access tokens; RS256 is the asymmetric standard for distributed verification | MEDIUM | 15-minute expiry. Sign with private RSA key via Spring Authorization Server's `JwtEncoder`/`JWKSource`. `iss`, `sub`, `iat`, `exp`, `jti` claims required. [Source: Spring Security Docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) |
| Refresh token issuance and rotation | Short-lived access tokens require refresh tokens; rotation is the current security baseline | MEDIUM | Opaque tokens stored in DB. Single-use: invalidate old token on each use. Issue new pair on refresh. 7-30 day expiry typical. [Source: Auth0 Docs](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation) |
| Refresh token reuse detection | Reusing an already-rotated token = stolen token; the industry standard is to revoke all tokens for the user | MEDIUM | If old (already-rotated) token is presented: revoke ALL refresh tokens for that user, force re-auth. DB fields needed: `token`, `nextToken`, `usedAt`, `expiresAt`, `userId`. [Source: Mihai Andrei Blog](https://mihai-andrei.com/blog/refresh-token-reuse-interval-and-reuse-detection/) |
| Token revocation (logout) | Users expect logout to immediately invalidate tokens | LOW | Mark refresh token as revoked in DB on `POST /revoke`. Access token remains valid until expiry (15 min acceptable). |
| `POST /auth/google` endpoint | Entry point for Google-authenticated clients | LOW | Accept `{ idToken }`, verify, upsert user, return `{ accessToken, refreshToken }`. |
| `POST /auth/apple` endpoint | Entry point for Apple-authenticated clients | LOW | Accept `{ idToken, user? }`. The `user` object (with name/email) is only present on first sign-in — must handle its absence gracefully. |
| `POST /auth/refresh` endpoint | Access token rotation flow | LOW | Accept `{ refreshToken }`, validate, rotate, return new pair. |
| `POST /auth/revoke` endpoint | Logout | LOW | Accept `{ refreshToken }`, mark revoked. |
| `GET /users/me` endpoint | Every auth system needs a "who am I" endpoint; clients call it after login to bootstrap their UI | LOW | Returns authenticated user profile from JWT claims + DB. Requires valid Bearer token. |
| `JwtAuthenticationFilter` (OncePerRequestFilter) | Stateless auth requires every request to carry and validate a token in the filter chain | MEDIUM | Extract Bearer token, validate RS256 signature, set `SecurityContextHolder`. Extends `OncePerRequestFilter`. Runs before `UsernamePasswordAuthenticationFilter`. [Source: Java Code Geeks](https://www.javacodegeeks.com/2025/05/how-to-secure-rest-apis-with-spring-security-and-jwt-2025-edition.html) |
| Stateless Spring Security configuration | No sessions; CSRF disabled for stateless APIs | LOW | `SessionCreationPolicy.STATELESS`, `csrf().disable()`. Auth endpoints permitted, all others require authentication. [Source: Spring Security Reference](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) |
| User entity with OAuth2 fields | Social-login users have no password; schema must capture provider identity | LOW | Fields: `id` (UUID), `email`, `name`, `picture`, `provider` (GOOGLE/APPLE), `providerId` (from `sub`), `roles`, `createdAt`, `updatedAt`. Upsert on login (find by `provider + providerId`). |
| Role-based authorization | Even minimal auth templates need at least ROLE_USER; consumers will want to add ROLE_ADMIN | LOW | String-list roles on User entity; embedded as JWT claims. Spring Security `hasRole()` annotations on endpoints. |
| Consistent JSON error responses | Clients must distinguish auth failures from other errors; inconsistent error shapes break client parsers | LOW | Shape: `{ error: String, message: String, status: Int }`. 401 for invalid/expired token, 403 for insufficient roles, 400 for validation errors. Use `@RestControllerAdvice`. |
| CORS configuration | Web SPA clients will get blocked without correct CORS headers | LOW | Configurable `allowedOrigins` via `application.yml`. Do NOT use `allowedOrigins=*` with `allowCredentials=true`. |
| Input validation (Jakarta Validation) | DTOs without validation cause cryptic 500s or security holes from null inputs | LOW | `@Valid` on request bodies. `@NotBlank` on token fields. Return structured 400 on violations. |
| RSA keystore management (.jks) | RS256 signing requires a keypair; the template must handle keystore lifecycle | MEDIUM | Shell script generates `.jks` for production predictability. Runtime fallback generates in-memory key if `.jks` not present (for easy dev startup). |
| Environment variable externalization | Secrets must not be hardcoded in source; template consumers expect a `.env.example` | LOW | All sensitive values via `${ENV_VAR}` in `application.yml`. `.env.example` documents every required variable with description. |
| Dev/prod profiles | Template consumers need working dev defaults and production-ready prod config | LOW | `application.yml` + `application-dev.yml` + `application-prod.yml`. Dev: relaxed CORS, console logs. Prod: strict CORS, external secrets. |
| Domain-based package layout | Template must be easily renameable; layer-based layouts mix concerns across domains | LOW | `kz.innlab.template.{user,authentication,config}` — each domain self-contained. Standard expectation for a reusable template. |
| Docker + docker-compose (PostgreSQL) | Template consumers need to run locally without a pre-existing Postgres install | LOW | `docker-compose.yml` with Postgres 18. No app Dockerfile required (template runs from IDE/mvnw). |

---

### Differentiators (Competitive Advantage)

Features that go beyond what other Spring Boot JWT starters provide. These align with the template's core value of being a production-ready starting point that's also easy to customize.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Refresh token `nextToken` chain with reuse interval | Most tutorials use naive delete-and-replace rotation; the `nextToken` + grace period (10s) approach handles concurrent requests without false revocations | MEDIUM | DB fields: `nextToken` (UUID, nullable), `usedAt` (timestamp). If reuse detected within 10s window, return the already-issued replacement rather than revoking. Outside window = revoke all. Differentiates from basic bezkoder-style implementations. |
| Virtual Threads on Spring MVC | Higher concurrency than traditional thread-per-request without reactive complexity; a genuine 2025 differentiator for Kotlin+Spring Boot templates | LOW-MEDIUM | Configure `spring.threads.virtual.enabled=true`. Compatible with blocking JDBC/Hibernate. Spring Boot 4 + Java 25 makes this simple. Most templates still use platform threads. |
| Spring Authorization Server JWKSource (not JJWT) | Using the battle-tested JWT infrastructure from the authorization server means JWKS endpoint is available out-of-the-box for future service-to-service verification | MEDIUM | `spring-authorization-server` dependency for `NimbusJwtEncoder` + `JWKSource`. Avoids the JJWT library version churn problem. Provides `/oauth2/jwks` endpoint for free. |
| Google `sub`-based user identity (not email) | Email can change; `sub` is stable. Most tutorials use email as the unique key, which breaks if a user changes their Google email | LOW | `findByProviderAndProviderId("GOOGLE", sub)` as the lookup. Document this explicitly. |
| Apple private relay email handling | Apple anonymizes emails by default; templates that ignore this break for a large % of Apple users | MEDIUM | Accept `xyz@privaterelay.appleid.com` as valid email. Store whatever email Apple returns (real or relay). Do not require a "real" email format. Document the behavior. |
| `TODO:` markers for rate limiting extension points | Template consumers will add rate limiting; pre-marked extension points in the filter chain make this trivial | LOW | `// TODO: Rate limiting — integrate Bucket4J or Spring Cloud Gateway here` comments at auth endpoints and filter. Doesn't implement rate limiting (out of scope) but makes the template extensible. |
| RSA runtime key fallback for zero-config dev startup | Most templates require manual keytool commands before first run; runtime fallback eliminates this friction | LOW | If `.jks` not found at startup, generate in-memory RSA keypair. Log a prominent warning. Allows `./mvnw spring-boot:run` to work immediately after clone. |

---

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem like good additions but would hurt the template's core purpose.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Email/password registration | "Not everyone uses social login" | Adds bcrypt, email verification, password reset, forgot-password flow — doubles the codebase scope. The template's value is social-login-only clarity. | Keep auth exclusively Google/Apple. Document that email/password auth should be added as a separate module if needed. |
| Email verification flow | Required for email/password auth | Requires SMTP integration, token expiry, re-send logic — out of scope for this template | Not applicable since there is no email/password auth. |
| 2FA / TOTP | Security-conscious devs ask for it | Adds authenticator app integration, backup codes, recovery — a separate concern orthogonal to OAuth2 ID token verification | Document as a post-template addition. The access/refresh token system is the 2FA analog here (ID token is the second factor). |
| Admin user management endpoints | "We need to list and ban users" | Expands scope significantly: pagination, soft delete, admin roles, audit logging | Keep only `GET /users/me`. Admin endpoints should be added by consumers in their own service layer. |
| Full OAuth2 Authorization Server | "Add your own login flows" | The template uses Spring Authorization Server only for JWKSource/JWT signing, not as a full auth server. Enabling the full server adds redirect flows, client registrations, consent screens — breaks the "stateless ID token verification" model | Use ID token verification flow only. If full AS is needed, that's a different template. |
| Session-based fallback auth | "For server-rendered pages" | Conflicts with stateless design; CSRF must be re-enabled; session storage added | This template is API-only. Server-rendered apps should use a different auth approach. |
| Refresh token in HTTP-only cookie | "More secure than header" | True for browsers, but mobile clients (iOS/Android) use Authorization header, not cookies. Mixing both adds complexity. The template targets mobile-first. | Return tokens in response body. Document that web-only consumers can adapt to cookies. |
| Rate limiting implementation | "Auth endpoints need rate limiting" | Requires Redis or in-memory state; couples the template to an infrastructure dependency | Add `// TODO` markers at auth endpoint handlers. Document Bucket4J as the recommended library. Let consumers add it. |
| Soft delete for users | "Users should be able to delete their account" | Adds `deletedAt` field, filter on all queries, GDPR erasure logic | Out of scope. Document as extension point. |
| Multi-tenancy / organization support | "Enterprise use case" | Fundamentally changes the data model; tenant isolation requires query-level filtering | Single-tenant design. Consumers needing multi-tenancy should use this as a reference, not a drop-in. |
| WebSocket / SSE auth | "Real-time apps need auth too" | WebSocket handshake auth is a separate concern from REST API auth | Out of scope. REST auth only. Document STOMP + JWT as the extension pattern. |

---

## Feature Dependencies

```
Google ID Token Verification
    └──requires──> User Entity (upsert on verified sub)
                       └──requires──> Spring Data JPA + Hibernate
                                          └──requires──> PostgreSQL + docker-compose

Apple ID Token Verification
    └──requires──> User Entity (upsert on verified sub)
    └──requires──> Apple JWKS fetching (HTTP call to appleid.apple.com/auth/keys)
    └──requires──> Apple "first sign-in user data" handling (name/email only sent once)

JWT Access Token Issuance
    └──requires──> RSA Keystore (.jks or runtime-generated)
    └──requires──> Spring Authorization Server JWKSource + NimbusJwtEncoder

Refresh Token Rotation
    └──requires──> RefreshToken entity in DB
    └──requires──> JWT Access Token Issuance (issues new access token on rotate)

Refresh Token Reuse Detection
    └──requires──> Refresh Token Rotation (extends it with nextToken + usedAt fields)

JwtAuthenticationFilter
    └──requires──> JWT Access Token Issuance (knows the signing key / JWKS)
    └──requires──> Spring Security stateless configuration

GET /users/me
    └──requires──> JwtAuthenticationFilter (must be authenticated)
    └──requires──> User Entity

POST /auth/refresh
    └──requires──> Refresh Token Rotation
    └──requires──> JWT Access Token Issuance

POST /auth/revoke
    └──requires──> RefreshToken entity (marks as revoked)

Consistent JSON error responses
    └──enhances──> All endpoints (wraps all error conditions)

CORS configuration
    └──enhances──> All endpoints (required before Spring Security processes requests)

Environment variable externalization
    └──enhances──> RSA Keystore, DB config, CORS allowedOrigins, Google Client IDs
```

### Dependency Notes

- **Apple ID token verification requires handling first-sign-in user data:** Apple sends `user.name` and `user.email` only on the very first authorization. The backend must persist this data on first sign-in. Subsequent logins carry only the `idToken`. If the backend misses this on the first call, the data is gone.
- **Refresh token reuse detection requires `nextToken` field on RefreshToken entity:** This is an extension of basic rotation, not a separate concern. Design the entity with both fields from the start.
- **RSA keystore is a prerequisite for all JWT operations:** Everything else (token issuance, JwtAuthenticationFilter, JWKS endpoint) depends on the keystore being loaded at startup.
- **User upsert logic (find-by-provider + providerId, not email):** Both Google and Apple verification depend on this lookup strategy. Email is unreliable as a unique key (can change on Google, may be a private relay address on Apple).

---

## MVP Definition

### Launch With (v1 — this milestone)

Minimum set to have a fully functional auth backend that mobile/web clients can integrate against.

- [x] Spring Data JPA + Hibernate + PostgreSQL setup (docker-compose)
- [x] Java 25 Virtual Threads configuration
- [ ] RSA keystore: shell script + runtime fallback
- [ ] User entity (UUID PK, email, name, picture, provider, providerId, roles, timestamps)
- [ ] Spring Security stateless configuration (no sessions, CSRF off)
- [ ] JwtAuthenticationFilter (OncePerRequestFilter, RS256 validation)
- [ ] JWT access token issuance via Spring Authorization Server JWKSource (RS256, 15 min)
- [ ] Refresh token entity + rotation + reuse detection (nextToken chain)
- [ ] Google ID token verification (`GoogleIdTokenVerifier`, `sub`-based identity)
- [ ] Apple ID token verification (JWKS fetch, claims validation, first-sign-in user data)
- [ ] Auth endpoints: POST /api/v1/auth/google, /apple, /refresh, /revoke
- [ ] GET /api/v1/users/me
- [ ] Consistent JSON error responses (`@RestControllerAdvice`)
- [ ] CORS configuration (configurable via env var)
- [ ] Input validation (Jakarta Validation on all request DTOs)
- [ ] application.yml with dev/prod profiles
- [ ] .env.example with all required environment variables

### Add After Validation (v1.x)

Features that improve the template's quality-of-life but don't block initial integration.

- [ ] Flyway database migrations — when consumers complain that schema drift causes issues across environments; add `V1__init_users.sql`, `V2__init_refresh_tokens.sql`
- [ ] Spring Boot Actuator health/info endpoints — when the template is used in container deployments with health checks
- [ ] Logging configuration (structured JSON logs) — when the template is used in production with ELK/Loki

### Future Consideration (v2+)

Features worth considering if this template evolves into a more complete auth service.

- [ ] Token introspection endpoint — for service-to-service verification without JWKS dependency
- [ ] User profile update endpoint (PATCH /users/me) — when consumers need writable profiles
- [ ] Account linking (same email from different providers) — complex identity merging; only needed if users can have both Google and Apple linked to one account

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Google ID token verification | HIGH | MEDIUM | P1 |
| Apple ID token verification | HIGH | HIGH | P1 |
| JWT access token issuance (RS256) | HIGH | MEDIUM | P1 |
| Refresh token rotation + reuse detection | HIGH | MEDIUM | P1 |
| JwtAuthenticationFilter | HIGH | MEDIUM | P1 |
| Stateless Spring Security config | HIGH | LOW | P1 |
| User entity (with OAuth2 fields) | HIGH | LOW | P1 |
| Auth endpoints (google, apple, refresh, revoke) | HIGH | LOW | P1 |
| GET /users/me | HIGH | LOW | P1 |
| RSA keystore (script + runtime fallback) | HIGH | LOW | P1 |
| Consistent JSON error responses | HIGH | LOW | P1 |
| CORS configuration | MEDIUM | LOW | P1 |
| Input validation | MEDIUM | LOW | P1 |
| Environment variable externalization | HIGH | LOW | P1 |
| Dev/prod profiles | MEDIUM | LOW | P1 |
| Virtual Threads configuration | MEDIUM | LOW | P2 |
| Apple private relay email handling | MEDIUM | LOW | P2 |
| nextToken reuse interval (grace period) | MEDIUM | LOW | P2 |
| TODO rate limiting markers | LOW | LOW | P2 |
| Flyway migrations | MEDIUM | LOW | P2 |
| Docker/docker-compose | HIGH | LOW | P1 |

**Priority key:**
- P1: Must have for v1 launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

---

## Competitor Feature Analysis

These are the most referenced Spring Boot JWT auth templates/tutorials found in the ecosystem. Used to validate what "table stakes" means in practice.

| Feature | bezkoder (BezKoder.com) | hardikSinghBehl/jwt-auth-flow | devondragon/SpringUserFramework | Our Approach |
|---------|------------------------|-------------------------------|----------------------------------|--------------|
| Social login (Google/Apple) | No (email/password only) | No (email/password only) | No (email/password only) | Yes — core value proposition |
| Refresh token rotation | Yes (delete-replace, no reuse detection) | Yes (with JTI revocation list) | Partial | Yes — with nextToken chain + reuse detection |
| RS256 signing | HS256 (shared secret) | RS256 | Varies | RS256 via Spring Authorization Server JWKSource |
| Stateless JWT filter | Yes | Yes | Yes | Yes — OncePerRequestFilter |
| Role-based auth | Yes | Yes | Yes | Yes — embedded in JWT claims |
| Consistent error responses | Partial | Yes | Partial | Yes — @RestControllerAdvice with structured shape |
| Environment variable externalization | Partial | Yes | Partial | Yes — .env.example + profiles |
| Virtual Threads | No | No | No | Yes — differentiator |
| Apple ID token specific handling | No | No | No | Yes — first-sign-in user data, private relay email |

---

## Sources

- [Google: Verify the Google ID token on your server side](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token) — HIGH confidence
- [Auth0: Refresh Token Rotation](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation) — HIGH confidence
- [Spring Security: OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — HIGH confidence
- [Mihai Andrei: Refresh Token Reuse Interval and Reuse Detection](https://mihai-andrei.com/blog/refresh-token-reuse-interval-and-reuse-detection/) — MEDIUM confidence (single source, but aligns with Auth0 pattern)
- [Apple Developer: Authenticating users with Sign in with Apple](https://developer.apple.com/documentation/signinwithapple/authenticating-users-with-sign-in-with-apple) — HIGH confidence (official)
- [Java Code Geeks: How to Secure REST APIs with Spring Security and JWT (2025 Edition)](https://www.javacodegeeks.com/2025/05/how-to-secure-rest-apis-with-spring-security-and-jwt-2025-edition.html) — MEDIUM confidence
- [Java Code Geeks: Managing JWT Refresh Tokens in Spring Security (Dec 2024)](https://www.javacodegeeks.com/2024/12/managing-jwt-refresh-tokens-in-spring-security-a-complete-guide.html) — MEDIUM confidence
- [ThachTaro: Spring Boot Security JWT with Refresh Token Rotation](https://thachtaro2210.github.io/posts/springboot-jwt-refresh-rotation/) — MEDIUM confidence
- [JWT Security Best Practices Checklist — Curity](https://curity.io/resources/learn/jwt-best-practices/) — HIGH confidence (industry reference)
- [APIsec: JWT Security Vulnerabilities](https://www.apisec.ai/blog/jwt-security-vulnerabilities-prevention) — MEDIUM confidence

---

*Feature research for: Spring Boot JWT auth template (Google/Apple ID token verification)*
*Researched: 2026-03-01*
