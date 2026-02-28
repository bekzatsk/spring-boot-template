# Spring Boot Auth Template

## What This Is

A production-ready Spring Boot 4+ backend API template with stateless JWT authentication via Google and Apple OAuth2 ID token verification. Built with Kotlin on Java 25 with Virtual Threads. Designed as a reusable starting point — rename the package and artifact to bootstrap new projects with auth already wired.

## Core Value

Mobile/web clients can authenticate with Google or Apple ID tokens and receive JWT access/refresh tokens that secure all API endpoints — the entire auth flow works out of the box.

## Requirements

### Validated

- ✓ Spring Boot 4 project structure with Kotlin — existing
- ✓ Maven build system with mvnw wrapper — existing
- ✓ PostgreSQL JDBC driver configured — existing
- ✓ Jackson + Kotlin module for JSON serialization — existing
- ✓ Basic application entry point (TemplateApplication.kt) — existing

### Active

- [ ] Java 25 with Virtual Threads enabled
- [ ] Spring Data JPA + Hibernate for persistence
- [ ] Docker + docker-compose with PostgreSQL 18
- [ ] Spring Security 7+ stateless configuration (no sessions, CSRF disabled)
- [ ] JWT access tokens (RS256, 15-min expiry) via Spring Authorization Server JWKSource
- [ ] Refresh token rotation (opaque, DB-stored, single-use with reuse detection)
- [ ] Google OAuth2 ID token verification (via google-api-client)
- [ ] Apple OAuth2 ID token verification (fetch Apple public keys, validate JWT)
- [ ] Auth endpoints: POST /api/v1/auth/google, /apple, /refresh, /revoke
- [ ] User entity (UUID, email, name, picture, provider, providerId, roles, timestamps)
- [ ] GET /api/v1/users/me endpoint
- [ ] RSA keystore (.jks) generation — helper script + runtime fallback
- [ ] JwtAuthenticationFilter for Bearer token validation
- [ ] Consistent JSON error responses: { error, message, status }
- [ ] CORS configuration (restrictive, configurable allowed origins)
- [ ] Input validation with Jakarta Validation
- [ ] application.yml with dev/prod profiles, environment variable placeholders
- [ ] .env.example with all required environment variables documented
- [ ] Domain-based package layout (user/, authentication/, config/)

### Out of Scope

- Frontend / web UI — backend API only, clients are separate projects
- WebFlux / reactive stack — using Spring MVC with Virtual Threads instead
- Full OAuth2 Authorization Server — only using spring-authorization-server for JWT signing (JWKSource, JWT encoder)
- Session-based auth — stateless JWT only
- Rate limiting implementation — TODOs will mark where to add it
- Email/password local registration — auth is exclusively via Google/Apple ID tokens
- Admin panel or user management endpoints beyond /users/me

## Context

- This is a brownfield project — a minimal Spring Boot 4.0.3 scaffold already exists with Kotlin, Maven, PostgreSQL driver, and Jackson configured. The package structure is `kz.innlab.template`.
- The "template" naming is a placeholder. When creating a new project from this template, the user renames the package and artifact.
- Target clients are mobile apps (iOS/Android) and web SPAs that handle Google/Apple sign-in on the client side and send the ID token to the backend.
- Server currently runs on port 7070.
- Existing codebase has no controllers, services, repositories, or security — just the bootstrap entry point.

## Constraints

- **Tech stack**: Spring Boot 4+ with Spring MVC (NOT WebFlux), Kotlin, Java 25, Maven
- **Auth approach**: ID token verification only — no OAuth2 authorization code flow, no redirect-based login
- **JWT signing**: RS256 via keystore (.jks) file, managed through Spring Authorization Server's JWKSource
- **Database**: PostgreSQL via Spring Data JPA + Hibernate (connection: localhost:5432, db: template, user: postgres, password: postgres)
- **No hardcoded secrets**: All sensitive values via environment variables / ${ENV_VAR} placeholders in application.yml
- **Code style**: Idiomatic Kotlin — data classes for DTOs/entities, constructor injection, no @Autowired, no !! operators

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Spring MVC + Virtual Threads over WebFlux | Simpler programming model, Virtual Threads provide sufficient concurrency without reactive complexity | — Pending |
| Google/Apple ID token verification (not auth code flow) | Mobile-first — clients already have native sign-in SDKs that produce ID tokens | — Pending |
| Spring Authorization Server for JWT only | Leverages battle-tested JWT infrastructure (JWKSource, encoder) without full OAuth2 server overhead | — Pending |
| Refresh token rotation with reuse detection | Prevents token theft — if a refresh token is reused, all tokens for that user are revoked | — Pending |
| Domain-based package layout (user/, authentication/) | Better than layer-based for template reuse — each domain is self-contained | — Pending |
| RSA keystore with script + runtime fallback | Script for production predictability, runtime generation for easy dev startup | — Pending |

---
*Last updated: 2026-03-01 after initialization*
