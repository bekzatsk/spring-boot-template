# Milestones

## v1.0 MVP (Shipped: 2026-03-01)

**Phases completed:** 6 phases, 10 plans
**Timeline:** 4 days (2026-02-26 → 2026-03-01)
**Kotlin LOC:** 1,293
**Files changed:** 91

**Key accomplishments:**
- Spring Boot 4 + Kotlin foundation with JPA entities, Docker Compose, RSA key infrastructure
- Stateless Spring Security 7 with JWT Bearer validation, CORS, and JSON error responses
- Google ID token verification with full refresh token lifecycle (rotate, revoke, reuse detection, grace window)
- Apple Sign In with JWKS validation, first-login data persistence, private relay email support
- Hardening: Maven Wrapper, rate limiting TODO markers, clean compile with zero warnings
- Layered package restructure: config/, user/, authentication/ with model/repository/service/controller sub-packages

---

