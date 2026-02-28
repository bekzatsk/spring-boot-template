# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-01)

**Core value:** Mobile/web clients authenticate with Google or Apple ID tokens and receive JWT access/refresh tokens that secure all API endpoints — the entire auth flow works out of the box
**Current focus:** Phase 1 — Foundation

## Current Position

Phase: 1 of 5 (Foundation)
Plan: 0 of TBD in current phase
Status: Ready to plan
Last activity: 2026-03-01 — Roadmap created, all 5 phases defined, 33 requirements mapped

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: -
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

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 3: GoogleIdTokenVerifier builder pattern and multi-audience config need verification during planning (MEDIUM confidence in research)
- Phase 4: Apple JWKS caching strategy (Caffeine TTL) and first-login detection mechanics need validation during planning (MEDIUM confidence)
- Phase 4: Resolve whether to store refresh tokens as SHA-256 hashes or plaintext (TOKN-02 references hashing — adopt it)

## Session Continuity

Last session: 2026-03-01
Stopped at: Roadmap written — ready to plan Phase 1
Resume file: None
