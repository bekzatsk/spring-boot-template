# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-01)

**Core value:** Mobile/web clients authenticate with Google or Apple ID tokens and receive JWT access/refresh tokens that secure all API endpoints — the entire auth flow works out of the box
**Current focus:** Phase 3 — Google Auth and Token Management (complete — advancing to Phase 4)

## Current Position

Phase: 3 of 5 (Google Auth and Token Management)
Plan: 2 of 2 complete in current phase (plan 03-02 complete)
Status: Phase 3 complete — all endpoints wired; Phase 4 (Apple Auth) is next
Last activity: 2026-03-01 - Completed 03-02-PLAN.md: Google OAuth2 auth endpoints, AuthController, real UserController

Progress: [██████░░░░] 60%

## Performance Metrics

**Velocity:**
- Total plans completed: 5
- Average duration: 6 min
- Total execution time: 0.5 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-foundation | 2/2 | 12 min | 6 min |
| 02-security-wiring | 2/3 | 15 min | 7.5 min |

**Recent Trend:**
- Last 5 plans: 5 min, 7 min, 8 min, 7 min
- Trend: stable

| 03-google-auth-and-token-management | 2/2 | 8 min | 4 min |

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Spring MVC + Virtual Threads chosen over WebFlux — simpler model, sufficient concurrency
- Spring Authorization Server used narrowly for JWKSource/NimbusJwtEncoder only — NOT full OAuth2 server
- Refresh token rotation with 10-second grace window for mobile concurrent retry handling
- Users keyed on (provider, providerId) composite — never by email
- OAuth2AuthorizationServerAutoConfiguration excluded from TemplateApplication — prevents competing SecurityFilterChain; Spring Boot 4.0.3 package is org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet
- Dev profile uses :default values for DB credentials (convenience), prod uses bare ${ENV_VAR} (SECU-09)
- Kotlin all-open plugin configured for jakarta.persistence annotations (Entity, MappedSuperclass, Embeddable)
- User entity is a regular Kotlin class (not data class) — data class equals/hashCode breaks Hibernate proxy equality
- Manual equals/hashCode on id field only for JPA entities — safe for transient (null id) and persistent entities
- NimbusJwtDecoder.withJwkSource() used over OAuth2AuthorizationServerConfiguration.jwtDecoder() — avoids Authorization Server coupling
- Dev profile auto-generates in-memory RSA keypair — tokens do not persist across restarts (acceptable for dev)
- Jackson 3.x (tools.jackson.* namespace) used for ObjectMapper in error handlers — not com.fasterxml.jackson
- AuthenticationEntryPoint registered in both exceptionHandling and oauth2ResourceServer DSL (Pitfall 4 per research)
- Spring Boot 4.x @AutoConfigureMockMvc is in org.springframework.boot.webmvc.test.autoconfigure — not boot.test.autoconfigure.web.servlet
- H2 test-scoped dependency added — enables full @SpringBootTest with JPA without a running PostgreSQL instance
- JwtTokenService omits JwsHeader in JwtEncoderParameters — NimbusJwtEncoder infers RS256 from RSA JWKSource automatically
- [Phase 03-01]: Grace window replay returns 409 Conflict (not replacement raw token) — raw tokens not stored per TOKN-02; mobile client retries with token received from first successful rotation
- [Phase 03-01]: deleteAllByUser uses JPQL DELETE (not derived deleteBy) to avoid N+1 entity loading on family revocation
- [Phase 03-02]: GoogleIdTokenVerifier.verify() returns null on invalid token — checked explicitly, throws BadCredentialsException caught by GlobalExceptionHandler
- [Phase 03-02]: Test application.yaml must include app.auth config — test YAML overrides main YAML; auth section missing caused context init failure
- [Phase 03-02]: SecurityIntegrationTest /users/me creates real User in H2 — required because UserController now does real DB lookup instead of returning JWT claims

### Pending Todos

None.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 1 | Fix DataSource 'url' not specified error — add default spring profile | 2026-03-01 | cd51f12 | [1-fix-datasource-url-not-specified-error-a](./quick/1-fix-datasource-url-not-specified-error-a/) |

### Blockers/Concerns

- Phase 4: Apple JWKS caching strategy (Caffeine TTL) and first-login detection mechanics need validation during planning (MEDIUM confidence)
- Developer note: If running Postgres.app locally on port 5432, create the `template` role or stop Postgres.app and use Docker only

## Session Continuity

Last session: 2026-03-01
Stopped at: Completed 03-02-PLAN.md — Google OAuth2 login wired end-to-end: AuthController with /google, /refresh, /revoke; real UserController with UserService; 5 tests passing
Resume file: None
