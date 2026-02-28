# Spring Boot Auth Template

## What This Is

A production-ready Spring Boot 4+ template project with Kotlin that provides OAuth2 authentication (Google and Apple) via ID token verification, JWT access/refresh token management with rotation, and a clean modular package structure. Designed as a reusable starting point for new backend projects.

## Core Value

Mobile/web clients can authenticate via Google or Apple ID tokens and receive JWT access/refresh tokens that secure all API endpoints — the entire auth flow must work end-to-end.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- ✓ Spring Boot 4 application bootstrap with Kotlin — existing
- ✓ Maven build with wrapper (`mvnw`) — existing
- ✓ PostgreSQL driver configured — existing
- ✓ Base package structure `kz.innlab.template` — existing

### Active

<!-- Current scope. Building toward these. -->

- [ ] Google OAuth2 ID token verification and user creation
- [ ] Apple OAuth2 ID token verification and user creation
- [ ] JWT access token generation (RS256, 15-min expiry)
- [ ] Refresh token generation with DB storage and single-use rotation
- [ ] Refresh token reuse detection (revoke all tokens for user)
- [ ] Auth endpoints: POST /api/v1/auth/google, /apple, /refresh, /revoke
- [ ] User endpoint: GET /api/v1/users/me
- [ ] Spring Security stateless JWT filter chain
- [ ] JPA entities: User (UUID, email, name, picture, provider, roles, timestamps) and RefreshToken
- [ ] Flyway migrations for users and refresh_tokens tables
- [ ] Docker Compose with PostgreSQL 18
- [ ] Virtual Threads enabled (spring.threads.virtual.enabled=true)
- [ ] CORS configuration (restrictive, configurable origins)
- [ ] Consistent JSON error responses: { error, message, status }
- [ ] Jakarta Validation on request DTOs
- [ ] Environment variable placeholders for all secrets
- [ ] Dev/prod application.yml profiles
- [ ] RSA key pair for JWT signing via JWKSource/keystore

### Out of Scope

- WebFlux / reactive stack — using Spring MVC with Virtual Threads instead
- Full OAuth2 Authorization Server — only using spring-authorization-server for JWT encoding/JWKSource
- Session-based authentication — stateless JWT only
- Rate limiting implementation — TODOs will be placed where needed
- Frontend/mobile client — API-only template
- Email verification / password reset — no local auth, OAuth2 only
- Admin panel — no admin UI

## Context

- Existing codebase is a minimal Spring Boot 4.0.3 skeleton with Kotlin 2.2.21, Maven, and PostgreSQL driver
- Target JVM: Java 25 with Virtual Threads
- "template" is a placeholder name — will be renamed when creating new projects
- Google token verification via `com.google.api-client` library
- Apple token verification via fetching public keys from `https://appleid.apple.com/auth/keys`
- JWT signing uses keystore (.jks) file, not in-memory generated keys
- Auth providers: LOCAL (future), GOOGLE, APPLE

## Constraints

- **Tech stack**: Spring Boot 4+ with Spring MVC (not WebFlux) — Virtual Threads for concurrency
- **Language**: Kotlin with idiomatic style — no Java-style code, no `!!` operators
- **Auth**: Stateless JWT only — no server-side sessions, no cookies
- **Tokens**: RS256 for access tokens, opaque refresh tokens in DB with rotation
- **Security**: No hardcoded secrets — all via environment variables / application.yml placeholders
- **Database**: PostgreSQL with Flyway migrations — no auto-DDL
- **DI**: Constructor injection only — no `@Autowired`

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Spring MVC + Virtual Threads over WebFlux | Simpler programming model, same throughput benefits | — Pending |
| spring-authorization-server for JWT only | Lightweight JWT encoding without full OAuth2 server complexity | — Pending |
| Keystore (.jks) for RSA keys | Secure key storage, standard Java approach | — Pending |
| Refresh token rotation with reuse detection | Prevents token theft — if old token reused, all tokens revoked | — Pending |
| Modular package layout (user/, authentication/) | Clean separation of concerns, easy to extend with new modules | — Pending |

---
*Last updated: 2026-03-01 after initialization*
