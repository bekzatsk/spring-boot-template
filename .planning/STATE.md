# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-01)

**Core value:** Mobile/web clients authenticate with Google or Apple ID tokens and receive JWT access/refresh tokens that secure all API endpoints — the entire auth flow works out of the box
**Current focus:** Phase 1 — Foundation

## Current Position

Phase: 1 of 5 (Foundation)
Plan: 1 of 2 in current phase
Status: Executing
Last activity: 2026-03-01 — Completed plan 01-01 (build infrastructure)

Progress: [█░░░░░░░░░] 10%

## Performance Metrics

**Velocity:**
- Total plans completed: 1
- Average duration: 5 min
- Total execution time: 0.1 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-foundation | 1/2 | 5 min | 5 min |

**Recent Trend:**
- Last 5 plans: 5 min
- Trend: -

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

### Pending Todos

None.

### Blockers/Concerns

- Phase 3: GoogleIdTokenVerifier builder pattern and multi-audience config need verification during planning (MEDIUM confidence in research)
- Phase 4: Apple JWKS caching strategy (Caffeine TTL) and first-login detection mechanics need validation during planning (MEDIUM confidence)
- Phase 4: Resolve whether to store refresh tokens as SHA-256 hashes or plaintext (TOKN-02 references hashing — adopt it)

## Session Continuity

Last session: 2026-03-01
Stopped at: Completed 01-01-PLAN.md — build infrastructure complete, ready for plan 01-02
Resume file: None
