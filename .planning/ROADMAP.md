# Roadmap: Spring Boot Auth Template

## Overview

Starting from a minimal Spring Boot 4 + Kotlin scaffold with no auth logic, this roadmap builds a production-ready JWT authentication backend in five phases. The dependency graph is linear: entities and RSA infrastructure must exist before the security filter chain can be wired; the security filter chain must be correct before provider verification can be tested; Google auth proves the full token-issuance flow before Apple's edge cases are introduced; all core flows must work before hardening and quality work validates them. Each phase delivers a coherent, independently verifiable capability.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Foundation** - Project infrastructure, JPA entities, RSA keystore, Docker, profiles
- [x] **Phase 2: Security Wiring** - Stateless Spring Security 7, JWT filter, CORS, error handling
- [ ] **Phase 3: Google Auth and Token Management** - Google ID token verification, full token lifecycle, /users/me
- [ ] **Phase 4: Apple Auth** - Apple ID token verification, first-login data, private relay emails
- [ ] **Phase 5: Hardening** - Integration tests, rate limit markers, compile verification, template polish

## Phase Details

### Phase 1: Foundation
**Goal**: The application starts with a working database connection, JPA entities for User and RefreshToken, and RSA key infrastructure ready — all without any auth logic
**Depends on**: Nothing (first phase)
**Requirements**: INFR-01, INFR-02, INFR-03, INFR-04, INFR-05, INFR-06, USER-01, USER-02, SECU-06, SECU-09
**Success Criteria** (what must be TRUE):
  1. `./mvnw spring-boot:run` starts without errors after `docker-compose up -d`
  2. PostgreSQL tables for `users` and `refresh_tokens` are created by Hibernate on startup
  3. The RSA keystore is loaded at startup (or auto-generated in dev profile) and exposes `JWKSource`, `JwtEncoder`, and `JwtDecoder` beans
  4. `application.yml` has dev and prod profiles with all sensitive values as `${ENV_VAR}` placeholders — no hardcoded secrets
  5. `.env.example` documents every required environment variable with descriptions
**Plans:** 2 plans
Plans:
- [x] 01-01-PLAN.md — Build infrastructure (pom.xml deps, Docker, profiles, env vars)
- [x] 01-02-PLAN.md — Domain entities and RSA key config (User, RefreshToken, JWKSource/JwtEncoder/JwtDecoder)

### Phase 2: Security Wiring
**Goal**: The Spring Security filter chain is configured stateless with CORS, JWT Bearer validation, and consistent JSON error responses — all endpoints return correct HTTP status codes before any provider auth exists
**Depends on**: Phase 1
**Requirements**: SECU-01, SECU-02, SECU-03, SECU-04, SECU-05, SECU-07, SECU-08, TOKN-01
**Success Criteria** (what must be TRUE):
  1. A request to `GET /api/v1/users/me` without a Bearer token returns `401` with JSON body `{ error, message, status }`
  2. An OPTIONS pre-flight request to any `/api/v1/**` endpoint returns `200` with CORS headers — not blocked by JWT filter
  3. A request with a valid RS256 Bearer token (signed with the loaded keystore) passes authentication and reaches the controller without hitting the database
  4. CORS allowed origins are read from configuration — changing the env var changes allowed origins without code changes
  5. Jakarta Validation rejects malformed auth request DTOs with a `400` error response in `{ error, message, status }` format
**Plans:** 2 plans
Plans:
- [x] 02-01-PLAN.md — SecurityFilterChain, CORS, error handlers, GlobalExceptionHandler
- [x] 02-02-PLAN.md — JwtTokenService, auth request DTO, stub UserController, end-to-end verification

### Phase 3: Google Auth and Token Management
**Goal**: A mobile client can exchange a valid Google ID token for JWT access and refresh tokens, rotate the refresh token, revoke it on logout, and retrieve the authenticated user's profile
**Depends on**: Phase 2
**Requirements**: AUTH-01, AUTH-03, TOKN-02, TOKN-03, TOKN-04, TOKN-05, TOKN-06, USER-03, USER-04
**Success Criteria** (what must be TRUE):
  1. `POST /api/v1/auth/google` with a valid Google ID token returns `{ accessToken, refreshToken }` and creates the user record on first call
  2. `POST /api/v1/auth/google` with the same Google account on a second call returns tokens without creating a duplicate user
  3. `POST /api/v1/auth/refresh` with a valid refresh token returns a new access token and a new refresh token, and the old refresh token is no longer valid
  4. Replaying a used refresh token revokes all refresh tokens for that user (reuse detection), and subsequent `/refresh` calls return `401`
  5. `POST /api/v1/auth/revoke` invalidates the refresh token, and `GET /api/v1/users/me` with the previous access token still works until expiry while no new tokens can be issued
  6. `GET /api/v1/users/me` with a valid Bearer token returns the authenticated user's profile JSON
**Plans**: TBD

### Phase 4: Apple Auth
**Goal**: A mobile client can exchange a valid Apple ID token for JWT access and refresh tokens, with first-login user data persisted atomically and private relay emails accepted
**Depends on**: Phase 3
**Requirements**: AUTH-02, AUTH-04, AUTH-05, AUTH-06
**Success Criteria** (what must be TRUE):
  1. `POST /api/v1/auth/apple` with a valid Apple ID token on first sign-in (with name/email in the token) creates the user with correct name and email persisted
  2. `POST /api/v1/auth/apple` on a subsequent sign-in (no name/email in token) finds and authenticates the existing user without error
  3. `POST /api/v1/auth/apple` with a `*@privaterelay.appleid.com` email returns a successful auth response — not a validation error
  4. Apple ID token `iss`, `aud`, and `exp` claims are validated — an expired or wrong-audience token returns `401`
**Plans**: TBD

### Phase 5: Hardening
**Goal**: The template is verified end-to-end, rate limiting extension points are marked, and a developer can clone and run it with confidence
**Depends on**: Phase 4
**Requirements**: INFR-07, INFR-08
**Success Criteria** (what must be TRUE):
  1. `./mvnw spring-boot:run` succeeds from a clean checkout after `docker-compose up -d` with only `.env` populated from `.env.example`
  2. `// TODO: rate limiting` markers exist at auth endpoint entry points and in `JwtAuthenticationFilter`
  3. The project compiles with `./mvnw clean package` with zero errors and zero warnings about deprecated APIs
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 2/2 | Complete | 2026-03-01 |
| 2. Security Wiring | 2/2 | Complete | 2026-03-01 |
| 3. Google Auth and Token Management | 0/TBD | Not started | - |
| 4. Apple Auth | 0/TBD | Not started | - |
| 5. Hardening | 0/TBD | Not started | - |
