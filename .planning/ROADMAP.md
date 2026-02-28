# Roadmap: Spring Boot Auth Template

## Overview

Five phases build from raw project skeleton to a fully working OAuth2 + JWT auth template. Phase 1 lays the project foundation (dependencies, Docker, config, Flyway). Phase 2 creates the data model (entities, migrations). Phase 3 wires Spring Security (JWT filter chain, RSA keys, error handling, CORS). Phase 4 delivers the complete auth flow (Google + Apple verification, token generation, all auth endpoints). Phase 5 finishes the user API, validation, logging, and code quality so the template is immediately reusable.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation** - Project dependencies, Docker Compose, application config, and environment variable setup
- [ ] **Phase 2: Data Layer** - JPA entities for User and RefreshToken, Flyway migrations, repositories
- [ ] **Phase 3: Security Infrastructure** - Spring Security JWT filter chain, RSA key pair, CORS, error responses
- [ ] **Phase 4: Authentication** - Google and Apple ID token verification, JWT issuance, refresh/revoke endpoints
- [ ] **Phase 5: User API and Quality** - GET /users/me endpoint, Jakarta Validation, logging, Kotlin code quality

## Phase Details

### Phase 1: Foundation
**Goal**: The project boots cleanly with all dependencies present, connects to a Dockerized PostgreSQL database, and loads configuration from environment variables across dev and prod profiles
**Depends on**: Nothing (first phase)
**Requirements**: INFRA-03, INFRA-04, INFRA-05, INFRA-06, INFRA-07, INFRA-08
**Success Criteria** (what must be TRUE):
  1. `docker compose up` starts PostgreSQL 18 and the application connects to it without errors
  2. Application starts on both `dev` and `prod` profiles without missing-property errors
  3. `.env.example` documents every required environment variable and the app reads them via `${ENV_VAR}` placeholders
  4. `mvnw verify` succeeds with all required dependencies resolved (Spring Security, Authorization Server, OAuth2 Resource Server, Flyway, google-api-client)
  5. Virtual Threads are active (`spring.threads.virtual.enabled=true`) and logged on startup
**Plans**: TBD

### Phase 2: Data Layer
**Goal**: User and RefreshToken data structures exist in the database via Flyway-managed schema, with JPA entities and Spring Data repositories ready for use by the auth layer
**Depends on**: Phase 1
**Requirements**: USER-01, USER-03, INFRA-01, INFRA-02
**Success Criteria** (what must be TRUE):
  1. Flyway applies V1__init.sql on startup, creating `users` and `refresh_tokens` tables with correct columns and constraints
  2. User JPA entity has UUID primary key, email, name, picture, provider (GOOGLE/APPLE/LOCAL enum), providerId, roles, createdAt, updatedAt fields
  3. RefreshToken JPA entity links to User and stores token hash, expiry, and revocation status
  4. Spring Data repositories for User and RefreshToken are injectable and support basic CRUD operations
**Plans**: TBD

### Phase 3: Security Infrastructure
**Goal**: All API endpoints are protected by a stateless JWT authentication filter; RSA keys are loaded from a keystore; CORS is configured; unauthorized requests receive consistent JSON error responses
**Depends on**: Phase 2
**Requirements**: SEC-01, SEC-02, SEC-03, SEC-04, SEC-05, SEC-06, SEC-08
**Success Criteria** (what must be TRUE):
  1. A request to any `/api/**` endpoint without a valid JWT receives a JSON error response with `{ error, message, status }` shape and HTTP 401
  2. A request to `/api/v1/auth/**` without a JWT is allowed through (public endpoint)
  3. RSA key pair is loaded from a `.jks` keystore file and exposed via JWKSource — the app fails to start if the keystore is missing
  4. A cross-origin request from an allowed origin receives correct CORS headers; a disallowed origin is rejected
  5. Rate limiting TODO comments are present at every auth endpoint handler method
**Plans**: TBD

### Phase 4: Authentication
**Goal**: A client can exchange a Google or Apple ID token for JWT access and refresh tokens, rotate the refresh token, and revoke it — the complete auth flow works end-to-end
**Depends on**: Phase 3
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, AUTH-07, AUTH-08, AUTH-09, AUTH-10
**Success Criteria** (what must be TRUE):
  1. POST `/api/v1/auth/google` with a valid Google ID token returns a JWT access token (RS256, 15-min expiry) and an opaque refresh token
  2. POST `/api/v1/auth/apple` with a valid Apple ID token returns the same token pair; Apple public keys are fetched from `https://appleid.apple.com/auth/keys`
  3. If the user does not exist, they are auto-created from the ID token claims (email, name, picture, provider, providerId)
  4. POST `/api/v1/auth/refresh` with a valid refresh token returns a new access + refresh token pair and invalidates the old refresh token
  5. POST `/api/v1/auth/refresh` with a previously used (revoked) refresh token revokes ALL tokens for that user (reuse detection)
  6. POST `/api/v1/auth/revoke` invalidates the supplied refresh token; subsequent use of that token is rejected
**Plans**: TBD

### Phase 5: User API and Quality
**Goal**: Authenticated clients can retrieve their own profile; all request DTOs are validated; services log meaningful messages; the codebase is idiomatic Kotlin following the clean package structure
**Depends on**: Phase 4
**Requirements**: USER-02, SEC-07, QUAL-01, QUAL-02, QUAL-03, QUAL-04, QUAL-05, QUAL-06
**Success Criteria** (what must be TRUE):
  1. GET `/api/v1/users/me` with a valid JWT returns the current user's profile (id, email, name, picture, provider, roles)
  2. A malformed auth request body (missing required field) returns a 400 JSON error response with field-level validation details
  3. Application logs show meaningful SLF4J messages for auth events (token issued, user created, token revoked, reuse detected)
  4. All public service and controller methods have KDoc comments describing their contract
  5. No `!!` operators appear in Kotlin source; all DI uses constructor injection; controllers contain no business logic
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 0/TBD | Not started | - |
| 2. Data Layer | 0/TBD | Not started | - |
| 3. Security Infrastructure | 0/TBD | Not started | - |
| 4. Authentication | 0/TBD | Not started | - |
| 5. User API and Quality | 0/TBD | Not started | - |
