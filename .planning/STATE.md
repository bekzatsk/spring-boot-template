# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-01)

**Core value:** Mobile/web clients authenticate with Google or Apple ID tokens and receive JWT access/refresh tokens that secure all API endpoints — the entire auth flow works out of the box
**Current focus:** Phase 5 — Hardening (plan 05-01 complete — all phases complete)

## Current Position

Phase: 5 of 5 (Hardening)
Plan: 1 of 1 complete in current phase (plan 05-01 complete)
Status: ALL PHASES COMPLETE — Template fully hardened with Maven Wrapper, rate limiting markers, and clean test output
Last activity: 2026-03-01 - Completed 05-01-PLAN.md: Maven Wrapper 3.9.9, rate limiting TODO markers at all 5 auth entry points, H2Dialect deprecation warning eliminated

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**
- Total plans completed: 7
- Average duration: 6 min
- Total execution time: 0.7 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-foundation | 2/2 | 12 min | 6 min |
| 02-security-wiring | 2/3 | 15 min | 7.5 min |
| 03-google-auth-and-token-management | 2/2 | 8 min | 4 min |
| 04-apple-auth | 1/1 | 13 min | 13 min |
| 05-hardening | 1/1 | 2 min | 2 min |

**Recent Trend:**
- Last 5 plans: 7 min, 8 min, 7 min, 13 min, 2 min
- Trend: stable

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
- [Phase 04-01]: @MockitoBean is from org.springframework.test.context.bean.override.mockito (Spring 7.x) — not from spring-boot-test; mockito-kotlin not on classpath; use plain Mockito.when()/anyString()
- [Phase 04-01]: refreshTokenRepository.deleteAll() must precede userRepository.deleteAll() in test @BeforeEach — FK constraint from refresh_tokens.user_id prevents deleting users with active tokens
- [Phase 04-01]: appleJwtDecoder bean named explicitly and injected with @Qualifier("appleJwtDecoder") — prevents NoUniqueBeanDefinitionException with resource server JwtDecoder from RsaKeyConfig
- [Phase 04-01]: JwtException from NimbusJwtDecoder.decode() wrapped as BadCredentialsException — JwtException not handled by GlobalExceptionHandler (would produce 500 without wrap)
- [Phase 05-01]: Maven Wrapper 3.3+ does not require maven-wrapper.jar — only maven-wrapper.properties needed; wrapperVersion=3.3.4
- [Phase 05-01]: H2Dialect auto-detection preferred — removing explicit database-platform eliminates HHH90000025 deprecation warning in Hibernate 7.x
- [Phase 05-01]: Rate limiting markers as // TODO comments (not stubs) — zero runtime behavior change, found via grep "TODO: rate limiting"
- [Quick-02]: JOIN FETCH rt.user on findByTokenHash fixes LazyInitializationException — open-in-view=false means persistence context closes before controller layer; fetching eagerly avoids re-opening transaction in AuthController
- [Quick-02]: SLF4J logger in GlobalExceptionHandler companion object — all catch-all handlers must log with logger.error() before returning 500 so unhandled exceptions are diagnosable

### Pending Todos

None.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 1 | Fix DataSource 'url' not specified error — add default spring profile | 2026-03-01 | cd51f12 | [1-fix-datasource-url-not-specified-error-a](./quick/1-fix-datasource-url-not-specified-error-a/) |
| 2 | Fix 500 on POST /api/v1/auth/refresh — JOIN FETCH User in findByTokenHash, add exception logging | 2026-03-01 | ce3560f | [2-fix-500-internal-server-error-on-api-v1-](./quick/2-fix-500-internal-server-error-on-api-v1-/) |

### Blockers/Concerns

- Developer note: If running Postgres.app locally on port 5432, create the `template` role or stop Postgres.app and use Docker only

## Session Continuity

Last session: 2026-03-01
Stopped at: Completed 05-01-PLAN.md — All phases complete: Maven Wrapper 3.9.9, rate limiting TODO markers at 5 entry points (4 AuthController + 1 SecurityConfig), H2Dialect deprecation eliminated, all 9 tests pass
Resume file: None
