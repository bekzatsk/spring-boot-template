# Project Research Summary

**Project:** Spring Boot JWT Auth Template (Google/Apple ID Token Verification)
**Domain:** Stateless REST API backend — social login auth with RS256 JWT issuance
**Researched:** 2026-03-01
**Confidence:** MEDIUM-HIGH

## Executive Summary

This project is a production-ready Spring Boot 4 + Kotlin template for mobile-first backends that authenticate users via Google and Apple Sign In. The standard approach is well-established: verify the provider-issued ID token server-side using official libraries (Google's `GoogleIdTokenVerifier`, Apple's JWKS endpoint via Nimbus), upsert the user record keyed on `(provider, providerId)` — never email — and issue a short-lived RS256 access token alongside a rotating opaque refresh token. All of this operates fully stateless with `SessionCreationPolicy.STATELESS`, no CSRF, and JWT claims carrying enough information that the `JwtAuthenticationFilter` never touches the database on regular authenticated requests.

The recommended stack is largely dictated by the existing project: Spring Boot 4.0.3 (managing Spring Security 7, Hibernate 7.2.4, HikariCP 7), `google-api-client:2.9.0` for Google token verification, Spring Authorization Server (via the renamed `spring-boot-starter-security-oauth2-authorization-server`) used narrowly for `JWKSource`/`NimbusJwtEncoder` only — not the full OAuth2 server flow — and Virtual Threads via `spring.threads.virtual.enabled=true`. Four new Maven dependencies are needed (security, oauth2-authorization-server, data-jpa, validation) plus the Google library. Everything else is managed transitively by Boot 4.

The critical risks cluster around three areas: Spring Boot 4 migration breaking changes (Jackson 3 namespace, `AntPathRequestMatcher` removal, renamed Authorization Server starter), Apple-specific nuances (first-login-only user data, private relay emails, JWKS `kid` matching), and refresh token correctness (concurrent rotation race conditions requiring pessimistic locking, reuse detection falsely revoking legitimate retries from mobile clients). Address these in design and test coverage — they are all preventable with known patterns documented in PITFALLS.md.

## Key Findings

### Recommended Stack

The project runs on Spring Boot 4.0.3 with Kotlin and Java 24 (targeting Java 25). Spring Security 7 is managed automatically by Boot 4 — no separate version pin needed. The Authorization Server library has been merged into Spring Security 7 with a renamed artifact: `spring-boot-starter-security-oauth2-authorization-server`. Using the old artifact name or importing `OAuth2AuthorizationServerConfiguration` are both breaking mistakes. For Apple token verification, use `NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys")` — there is no official Apple Java library. Nimbus is already transitive. PostgreSQL 18 is specified in Docker; Jackson 3 (`tools.jackson.*` namespace) is already correct in the project's pom.xml.

**Core technologies:**
- Spring Boot 4.0.3 + Spring Security 7: HTTP security, stateless filter chain — managed automatically, no version pins
- Spring Authorization Server (JWT signing only): `JWKSource`, `NimbusJwtEncoder`, RS256 signing — do NOT enable full server auto-config
- `google-api-client:2.9.0`: `GoogleIdTokenVerifier` for Google ID tokens — official library, handles JWK caching internally
- Spring Data JPA + Hibernate 7.2.4.Final: User and RefreshToken persistence — managed by Boot 4 via `spring-boot-starter-data-jpa`
- Virtual Threads: `spring.threads.virtual.enabled=true` — enables Tomcat, `@Async`, and `@Scheduled` to use virtual threads with zero other config changes
- PostgreSQL 18 (`postgres:18`): Primary database for users and refresh tokens
- `nimbus-jose-jwt:10.8`: Apple JWKS verification and RSA key loading — transitive from authorization server starter, no explicit declaration needed

### Expected Features

**Must have (table stakes) — all P1:**
- Google ID token verification via `GoogleIdTokenVerifier` with `aud` validation against client ID
- Apple ID token verification via JWKS endpoint with `iss`, `aud`, `exp` claim checks
- First-sign-in Apple user data (name/email) persisted atomically — lost forever if missed
- JWT access token issuance (RS256, 15-minute expiry) via `NimbusJwtEncoder` + `JWKSource`
- Opaque refresh token rotation with reuse detection (`nextToken` chain, revoke-all on replay)
- `JwtAuthenticationFilter` (`OncePerRequestFilter`) — validates Bearer token, sets `SecurityContextHolder` from claims only (no DB)
- Stateless Spring Security config: `STATELESS`, CSRF disabled, CORS via `SecurityFilterChain`
- Auth endpoints: `POST /api/v1/auth/google`, `/apple`, `/refresh`, `/revoke`
- `GET /api/v1/users/me` — requires valid Bearer token
- User entity: UUID PK, `provider`, `providerId` (`sub`), email, name, picture, roles, timestamps
- RSA keystore: shell script for production key generation + runtime fallback for zero-config dev startup
- Consistent JSON error responses: `{ error, message, status }` via `@RestControllerAdvice`
- CORS, input validation, environment variable externalization, dev/prod profiles, docker-compose (PostgreSQL)

**Should have (differentiators) — P2:**
- Virtual Threads configuration (`spring.threads.virtual.enabled=true`) — no competing template does this
- `nextToken` grace window (10-second reuse interval) to handle mobile network retries without false revocations
- `sub`-based user identity (not email) — documented explicitly as a best-practice differentiator
- Apple private relay email handling — accept `*@privaterelay.appleid.com` without validation failure
- `// TODO` rate limiting markers at auth endpoints and filter entry points
- Flyway database migrations (v1.x — when consumers request schema drift control)

**Defer (v2+):**
- Token introspection endpoint
- `PATCH /users/me` profile update
- Account linking (same email, different providers)
- Admin user management, 2FA/TOTP, email/password auth, WebSocket auth

### Architecture Approach

The architecture follows a domain-based vertical slice layout with two feature domains (`authentication/`, `user/`) and a cross-cutting `config/` package. The Spring Security filter chain runs `CorsFilter` then `JwtAuthenticationFilter` then the authorization rules — CORS must be configured inside `SecurityFilterChain`, not only at the MVC layer, or OPTIONS pre-flight requests are rejected before CORS headers are set. Controllers are thin (no logic); all business logic lives in services; repositories own all queries. The build order is critical: entities first, then repositories and `RsaKeyConfig`, then services, then `JwtAuthenticationFilter` and `SecurityConfig`, then controllers.

**Major components:**
1. `JwtAuthenticationFilter` (`OncePerRequestFilter`) — extracts Bearer token, validates RS256 signature via `JwtService`, populates `SecurityContextHolder` from JWT claims only; never reads DB
2. `SecurityConfig` — declares `SecurityFilterChain` with `STATELESS`, CSRF off, CORS from `SecurityFilterChain`, permit `/api/v1/auth/**`, authenticate everything else; registers filter before `UsernamePasswordAuthenticationFilter`
3. `RsaKeyConfig` — loads `.jks` keystore at startup, exposes `JWKSource`, `JwtEncoder`, `JwtDecoder` beans; runtime-generated key fallback for dev profile only; fails fast on missing keystore in prod
4. `AuthService` — orchestrates: verifies ID token via `GoogleVerifier` or `AppleVerifier`, calls `UserService.findOrCreate()`, calls `JwtService.issueAccessToken()`, calls `RefreshTokenService.issue()`
5. `GoogleVerifier` / `AppleVerifier` — provider-specific ID token verification; both return a normalized `ProviderIdentity` value object
6. `RefreshTokenService` — opaque UUID refresh tokens with pessimistic DB lock on rotation; reuse detection revokes all user tokens; `@Transactional` wrapping required
7. `GlobalExceptionHandler` (`@RestControllerAdvice`) — maps domain exceptions to `{ error, message, status }` responses; no stack traces in body

### Critical Pitfalls

1. **Spring Authorization Server full auto-config** — Do NOT use `@Import(OAuth2AuthorizationServerConfiguration.class)`. Wire only `JWKSource` + `NimbusJwtEncoder` beans directly. Full auto-config requires `RegisteredClientRepository` bean, conflicts with your `SecurityFilterChain`, and registers unused OAuth2 endpoints.

2. **`AntPathRequestMatcher` removed in Spring Security 7** — All tutorials use it; it does not exist in SS7. Use `PathPatternRequestMatcher.withDefaults().matcher(...)` exclusively for all request matching in `SecurityFilterChain`.

3. **Refresh token race condition** — Concurrent mobile requests can bypass rotation without DB-level atomicity. Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the `findByToken` query and wrap the entire rotate operation in `@Transactional`.

4. **Apple first-login user data loss** — Apple sends `given_name`, `family_name`, and `email` only on the very first authorization. Persist them synchronously within the same transaction that creates the user. There is no recovery path if the first request fails after the client sends the token.

5. **Email as user identity key** — Never key users by email across providers. Apple may return a private relay address (`*@privaterelay.appleid.com`) and Google warns explicitly not to use email as an identifier. Use `(provider, providerId)` — the `sub` claim — as the composite unique key. Enforce a unique constraint on this pair in the DB schema.

6. **Jackson 2 vs Jackson 3 namespace** — Spring Boot 4 uses `tools.jackson.*` not `com.fasterxml.jackson.*`. Spring Security 7 uses `SecurityJacksonModules` not `SecurityJackson2Modules`. Do not add any new `com.fasterxml.jackson` dependencies.

7. **CORS OPTIONS pre-flight blocked by JWT filter** — Configure CORS inside `SecurityFilterChain` (`http.cors { it.configurationSource(...) }`), not only via `WebMvcConfigurer`. `JwtAuthenticationFilter` must pass through requests with no Bearer header, not return 401.

## Implications for Roadmap

Based on the architecture's build order dependencies and the pitfall-to-phase mapping from PITFALLS.md:

### Phase 1: Foundation and Infrastructure
**Rationale:** Everything depends on the RSA keystore beans (`JWKSource`, `JwtEncoder`, `JwtDecoder`) and the database schema (User, RefreshToken entities). These have no upstream dependencies in the build graph. Establishing the Jackson 3 correctness and Spring Authorization Server scoping here prevents the hardest-to-diagnose startup failures later.
**Delivers:** Working Spring Boot app with JPA entities, HikariCP connection to PostgreSQL, RSA key loading with dev fallback, Boot context startup verified — no auth logic yet
**Addresses:** User entity, RefreshToken entity, RSA keystore script + runtime fallback, docker-compose, application.yml dev/prod profiles, `.env.example`
**Avoids:** Jackson 2 vs Jackson 3 confusion (Pitfall 9); Spring Authorization Server over-configuration (Pitfall 1); RSA key packaged in JAR (Pitfall 7)
**Research flag:** Standard patterns — skip phase research

### Phase 2: Spring Security Stateless Configuration and JWT Filter
**Rationale:** `SecurityConfig` and `JwtAuthenticationFilter` must exist before any endpoint can be tested. These have no dependency on the Google/Apple verifiers — the filter validates the app's own JWTs, not external tokens. Getting the security wiring correct first (STATELESS, CORS in filter chain, PathPatternRequestMatcher) prevents integration-level debugging later.
**Delivers:** `SecurityFilterChain` with stateless config, CORS pre-flight passing, `JwtAuthenticationFilter` validating RS256 Bearer tokens, `JwtService` encoding/decoding access tokens — all endpoints return correct HTTP codes on auth failure
**Uses:** `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-authorization-server` (JWKSource/NimbusJwtEncoder only), `RsaKeyConfig` from Phase 1
**Implements:** `SecurityConfig`, `JwtAuthenticationFilter`, `JwtService`, `GlobalExceptionHandler`
**Avoids:** `AntPathRequestMatcher` in SS7 (Pitfall 2); CORS OPTIONS blocked by JWT filter (Pitfall 8); full Authorization Server auto-config (Pitfall 1); DB query in JWT filter (Architecture anti-pattern 1)
**Research flag:** Standard patterns — official Spring Security 7 docs are HIGH confidence

### Phase 3: User Domain and Auth Orchestration (Google)
**Rationale:** Google verification is simpler than Apple (official library, single client ID, no first-login edge case). Implementing Google first proves the full auth flow — ID token verification, user upsert, access + refresh token issuance — before adding Apple's complexity. The `(provider, providerId)` identity key must be established here.
**Delivers:** Full auth flow for Google: `POST /auth/google` returns `{ accessToken, refreshToken }`, `RefreshTokenService` with rotation and reuse detection, `GET /users/me`, structured error responses
**Uses:** `google-api-client:2.9.0` (`GoogleIdTokenVerifier`), `UserService`, `RefreshTokenService`, `AuthService`, `AuthController`, `UserController`
**Implements:** `GoogleVerifier`, `UserService.findOrCreate()` keyed on `(GOOGLE, sub)`, `RefreshTokenService.rotate()` with pessimistic lock, `UserController.getMe()`
**Avoids:** Email as identity key (Pitfall 5); missing `aud` validation (Pitfall 6); refresh token race condition (Pitfall 3)
**Research flag:** Likely needs phase research — `GoogleIdTokenVerifier` Java API setup details

### Phase 4: Apple ID Token Verification
**Rationale:** Apple verification is the most complex individual feature — JWKS endpoint caching, `kid` matching, first-login user data handling, and private relay email acceptance. It builds directly on the Google-verified user upsert and auth flow from Phase 3. Isolating it in its own phase allows focused testing of Apple-specific edge cases.
**Delivers:** `POST /auth/apple` working end-to-end, Apple user data persisted on first login atomically, private relay email accepted, subsequent Apple logins work without user data in token
**Implements:** `AppleVerifier` (JWKS fetch + Nimbus signature verification with `kid` matching + claim validation), Apple-specific user create logic (first-login guard), `aud` validation against Apple Services ID
**Avoids:** Apple first-login data loss (Pitfall 4); email as identity (Pitfall 5); missing `aud` (Pitfall 6); hardcoded Apple JWKS keys (integration gotcha)
**Research flag:** Needs phase research — Apple JWKS caching strategy and first-login detection pattern need verification against current Apple docs

### Phase 5: Quality, Observability, and Template Hardening
**Rationale:** Template consumers need polish that makes the template trustworthy: comprehensive test coverage proving the pitfalls are avoided, structured logging, Virtual Threads enabled, rate limiting `// TODO` markers, and Flyway migrations for schema reproducibility. These do not block the core auth flow but determine whether the template is production-ready.
**Delivers:** Integration tests for concurrent refresh rotation (race condition), reuse detection, CORS pre-flight, Apple first-login, Virtual Threads enabled, Flyway `V1__init_users.sql` + `V2__init_refresh_tokens.sql`, Spring Boot Actuator health endpoint, `.env.example` complete, rate limiting TODO markers
**Uses:** All stack elements from previous phases; `spring.threads.virtual.enabled=true`; Spring Boot Actuator
**Avoids:** Reuse detection false positives on network retries (Pitfall 10); unbounded refresh token table growth (performance trap); stack traces in error responses (security mistake)
**Research flag:** Standard patterns — skip phase research

### Phase Ordering Rationale

- **Entities and RSA keystore first** because `JWKSource` bean is required by `JwtService`, which is required by `JwtAuthenticationFilter`, which is required by `SecurityConfig`. The dependency graph is linear from Phase 1 through Phase 2.
- **Security wiring before provider verification** because every auth endpoint flows through `SecurityFilterChain`. A misconfigured security layer produces misleading 401/403 errors that make provider verification debugging impossible.
- **Google before Apple** because `GoogleIdTokenVerifier` is one library call vs. Apple's multi-step JWKS fetch + Nimbus parsing + first-login guard. The complete flow is proven with Google before Apple's edge cases are introduced.
- **Hardening last** because test infrastructure and Flyway do not block correctness — they validate it. Adding them after core flows are working avoids rewriting tests due to schema changes.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 3 (Google auth):** `GoogleIdTokenVerifier` builder pattern and multi-audience configuration need verification against `google-api-client:2.9.0` API (MEDIUM confidence in current research — library API may differ from older examples)
- **Phase 4 (Apple auth):** Apple JWKS caching strategy (TTL, cache-on-startup vs. lazy), `kid` matching with Nimbus `JWKSet`, and the exact mechanics of detecting first-login vs. subsequent login need validation against current Apple documentation (MEDIUM confidence)

Phases with standard patterns (skip research-phase):
- **Phase 1:** Spring Data JPA entities, HikariCP, docker-compose — well-documented, HIGH confidence
- **Phase 2:** Spring Security 7 stateless config, `SecurityFilterChain` Kotlin DSL — official docs are authoritative, HIGH confidence
- **Phase 5:** Virtual Threads config, Spring Boot Actuator, Flyway — all well-documented, standard patterns

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Core Spring starters verified via official Spring Boot 4 docs; Maven Central versions confirmed; Authorization Server merger confirmed via spring.io blog + secondary sources |
| Features | MEDIUM-HIGH | Table stakes verified against official Google/Apple docs and multiple implementations; Apple-specific nuances (first-login, private relay) confirmed via Apple Developer Forums + docs |
| Architecture | HIGH | Spring Security 7 docs are authoritative; filter chain patterns verified against official Kotlin DSL docs; token rotation pattern from Auth0 canonical docs |
| Pitfalls | HIGH | Spring Security 7 migration guide directly documents removed classes; Apple first-login behavior confirmed via official Apple forums; race condition pattern documented in multiple sources |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **Apple JWKS in-process caching:** Research recommends Caffeine with a 60-minute TTL but no specific Spring Boot 4 + Nimbus integration example was found. During Phase 4 planning, validate the exact approach for caching `JWKSet` returned by Apple's endpoint.
- **Google multi-client-ID audience configuration:** The project may need to accept tokens from both Android and iOS client IDs (different `aud` values). The `GoogleIdTokenVerifier.Builder().setAudience(listOf(...))` accepts multiple IDs — validate the correct configuration for this template's use case during Phase 3 planning.
- **Refresh token hashing:** PITFALLS.md flags storing refresh tokens in plaintext as a security mistake and recommends SHA-256 hashing in the DB. FEATURES.md does not mention this. Resolve during Phase 3 design: adopt hashing or explicitly document the decision to use plaintext with rationale.
- **PostgreSQL 18 production readiness:** PostgreSQL 18 is newly released. For template consumers deploying immediately, STACK.md notes PostgreSQL 17 as a safer alternative. Document this tradeoff in `docker-compose.yml` comments.

## Sources

### Primary (HIGH confidence)
- [Spring Authorization Server Getting Started](https://docs.spring.io/spring-authorization-server/reference/getting-started.html) — JWKSource/JwtEncoder pattern, Maven coordinates
- [Spring Security 7.0 Reference: OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — NimbusJwtDecoder, stateless config, JWT validation
- [Spring Security 7.0 Migration Guide](https://docs.spring.io/spring-security/reference/migration/index.html) — AntPathRequestMatcher removal, PathPatternRequestMatcher API
- [Spring Security Kotlin DSL](https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html) — SecurityFilterChain Kotlin DSL patterns
- [Spring Boot Dependency Versions](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html) — Hibernate 7.2.4.Final, PostgreSQL 42.7.10, Jakarta Validation 3.1.1
- [Google: Verify the Google ID Token](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token) — GoogleIdTokenVerifier usage, sub vs email identity
- [Apple Developer: Authenticating users with Sign in with Apple](https://developer.apple.com/documentation/signinwithapple/authenticating-users-with-sign-in-with-apple) — JWKS endpoint, first-login behavior
- [Auth0: Refresh Token Rotation](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation) — token family revocation algorithm
- [Maven Central: google-api-client 2.9.0](https://central.sonatype.com/artifact/com.google.api-client/google-api-client) — version confirmed
- [Maven Central: nimbus-jose-jwt 10.8](https://central.sonatype.com/artifact/com.nimbusds/nimbus-jose-jwt) — version confirmed

### Secondary (MEDIUM confidence)
- [spring.io blog: Spring Authorization Server moving to Spring Security 7.0](https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/) — artifact rename, merger announcement
- [Mihai Andrei: Refresh Token Reuse Interval and Reuse Detection](https://mihai-andrei.com/blog/refresh-token-reuse-interval-and-reuse-detection/) — nextToken grace window pattern
- [Java Code Geeks: Secure REST APIs with Spring Security and JWT (2025)](https://www.javacodegeeks.com/2025/05/how-to-secure-rest-apis-with-spring-security-and-jwt-2025-edition.html) — OncePerRequestFilter pattern
- [GitHub: MossaabFrifita/spring-boot-4-security-7-jwt](https://github.com/MossaabFrifita/spring-boot-4-security-7-jwt) — working Spring Boot 4 + Security 7 example
- [Apple Developer Forums: User info only on first login](https://developer.apple.com/forums/thread/121496) — first-login behavior confirmed
- [ITNEXT: Spring Boot 4 Virtual Threads benchmark (Feb 2026)](https://itnext.io/) — Virtual Threads performance on Spring Boot 4.0.2

### Tertiary (LOW confidence)
- [Spring Boot 4.0 Migration Guide (GitHub Wiki)](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) — artifact renames (page structure confirmed, content verified via secondary sources)

---
*Research completed: 2026-03-01*
*Ready for roadmap: yes*
