# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-01)

**Core value:** Mobile/web clients authenticate with Google or Apple ID tokens and receive JWT access/refresh tokens that secure all API endpoints — the entire auth flow works out of the box
**Current focus:** Phase 2 — Security Wiring (complete — ready for Phase 3)

## Current Position

Phase: 2 of 5 (Security Wiring)
Plan: 3 of 3 in current phase (plan 02-02 complete — Phase 2 complete)
Status: Phase 2 complete — ready for Phase 3 (Google Auth)
Last activity: 2026-03-01 — Completed plan 02-02 (JwtTokenService, AuthRequest DTO, stub UserController, integration tests)

Progress: [████░░░░░░] 40%

## Performance Metrics

**Velocity:**
- Total plans completed: 4
- Average duration: 7 min
- Total execution time: 0.4 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-foundation | 2/2 | 12 min | 6 min |
| 02-security-wiring | 2/3 | 15 min | 7.5 min |

**Recent Trend:**
- Last 5 plans: 5 min, 7 min, 8 min, 7 min
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

### Pending Todos

None.

### Blockers/Concerns

- Phase 3: GoogleIdTokenVerifier builder pattern and multi-audience config need verification during planning (MEDIUM confidence in research)
- Phase 4: Apple JWKS caching strategy (Caffeine TTL) and first-login detection mechanics need validation during planning (MEDIUM confidence)
- Phase 4: Resolve whether to store refresh tokens as SHA-256 hashes or plaintext (TOKN-02 references hashing — adopt it)
- Developer note: If running Postgres.app locally on port 5432, create the `template` role or stop Postgres.app and use Docker only

## Session Continuity

Last session: 2026-03-01
Stopped at: Completed 02-02-PLAN.md — JwtTokenService, AuthRequest DTO, stub UserController, and 4 security integration tests complete; Phase 2 fully done
Resume file: None
