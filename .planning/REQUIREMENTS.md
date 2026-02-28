# Requirements: Spring Boot Auth Template

**Defined:** 2026-03-01
**Core Value:** Mobile/web clients can authenticate with Google or Apple ID tokens and receive JWT access/refresh tokens that secure all API endpoints — the entire auth flow works out of the box.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Authentication

- [ ] **AUTH-01**: Client can send Google ID token to POST /api/v1/auth/google and receive JWT access + refresh tokens
- [ ] **AUTH-02**: Client can send Apple ID token to POST /api/v1/auth/apple and receive JWT access + refresh tokens
- [ ] **AUTH-03**: Backend verifies Google ID token directly with Google (via google-api-client) including `aud` claim validation
- [ ] **AUTH-04**: Backend verifies Apple ID token via Apple's JWKS endpoint with `iss`, `aud`, `exp` claim validation
- [ ] **AUTH-05**: Apple first-sign-in user data (name, email) is persisted atomically — never lost
- [ ] **AUTH-06**: Apple private relay emails (`*@privaterelay.appleid.com`) are accepted without failure

### Token Management

- [ ] **TOKN-01**: JWT access tokens are RS256-signed with 15-minute expiry via NimbusJwtEncoder + JWKSource
- [ ] **TOKN-02**: Refresh tokens are opaque, stored in DB as SHA-256 hashes, with single-use rotation
- [ ] **TOKN-03**: Reuse detection revokes all refresh tokens for the user when a used token is replayed
- [ ] **TOKN-04**: 10-second grace window on refresh token rotation to handle concurrent mobile retries
- [ ] **TOKN-05**: POST /api/v1/auth/refresh rotates refresh token and returns new access + refresh tokens
- [ ] **TOKN-06**: POST /api/v1/auth/revoke invalidates the refresh token (logout)

### Security

- [ ] **SECU-01**: Spring Security configured as stateless (STATELESS session policy, CSRF disabled)
- [ ] **SECU-02**: JwtAuthenticationFilter validates Bearer token and sets SecurityContext from JWT claims only (no DB hit)
- [ ] **SECU-03**: All /api/** endpoints require authentication except /api/v1/auth/**
- [ ] **SECU-04**: CORS configured inside SecurityFilterChain so OPTIONS pre-flight requests pass correctly
- [ ] **SECU-05**: CORS allowed origins are configurable (not hardcoded)
- [ ] **SECU-06**: RSA keystore (.jks) for JWT signing with helper generation script + runtime fallback for dev
- [ ] **SECU-07**: Consistent JSON error responses: `{ error, message, status }` via @RestControllerAdvice
- [ ] **SECU-08**: Input validation with Jakarta Validation on auth request DTOs
- [ ] **SECU-09**: No hardcoded secrets — all sensitive values via environment variables / ${ENV_VAR} in application.yml

### User

- [ ] **USER-01**: User entity with UUID id, email, name, picture, provider (enum), providerId, roles, createdAt, updatedAt
- [ ] **USER-02**: Users are identified by (provider, providerId) composite key — never by email
- [ ] **USER-03**: GET /api/v1/users/me returns the current authenticated user's profile
- [ ] **USER-04**: New users are created automatically on first successful authentication (find-or-create)

### Infrastructure

- [ ] **INFR-01**: Virtual Threads enabled via spring.threads.virtual.enabled=true
- [ ] **INFR-02**: Spring Data JPA + Hibernate for User and RefreshToken persistence
- [ ] **INFR-03**: docker-compose.yml with PostgreSQL 18
- [ ] **INFR-04**: application.yml with dev and prod profiles
- [ ] **INFR-05**: .env.example documenting all required environment variables
- [ ] **INFR-06**: Domain-based package layout: config/, user/, authentication/
- [ ] **INFR-07**: Rate limiting TODO markers at auth endpoints and filter entry points
- [ ] **INFR-08**: Project compiles and runs with ./mvnw spring-boot:run after database setup

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Schema Management

- **SCHM-01**: Flyway database migrations for versioned schema changes

### User Management

- **UMGT-01**: PATCH /api/v1/users/me for profile updates
- **UMGT-02**: Account linking (same email across different providers)

### Observability

- **OBSV-01**: Spring Boot Actuator health endpoint
- **OBSV-02**: Structured logging configuration

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Frontend / web UI | Backend API only — clients are separate projects |
| WebFlux / reactive | Using Spring MVC with Virtual Threads instead |
| Full OAuth2 Authorization Server | Only using spring-authorization-server for JWT signing (JWKSource, JwtEncoder) |
| Session-based auth | Stateless JWT only |
| Rate limiting implementation | TODO markers only — implementation depends on deployment (API gateway vs in-app) |
| Email/password registration | Auth exclusively via Google/Apple ID tokens |
| Admin panel / user management | Beyond /users/me — template consumers add their own |
| 2FA / TOTP | Over-scoped for a template |
| Token introspection endpoint | Not needed for stateless JWT |
| WebSocket auth | Separate concern, add when needed |
| Firebase Auth integration | Raw Google/Apple tokens verified directly, no Firebase backend dependency |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 3 | Pending |
| AUTH-02 | Phase 4 | Pending |
| AUTH-03 | Phase 3 | Pending |
| AUTH-04 | Phase 4 | Pending |
| AUTH-05 | Phase 4 | Pending |
| AUTH-06 | Phase 4 | Pending |
| TOKN-01 | Phase 2 | Pending |
| TOKN-02 | Phase 3 | Pending |
| TOKN-03 | Phase 3 | Pending |
| TOKN-04 | Phase 3 | Pending |
| TOKN-05 | Phase 3 | Pending |
| TOKN-06 | Phase 3 | Pending |
| SECU-01 | Phase 2 | Pending |
| SECU-02 | Phase 2 | Pending |
| SECU-03 | Phase 2 | Pending |
| SECU-04 | Phase 2 | Pending |
| SECU-05 | Phase 2 | Pending |
| SECU-06 | Phase 1 | Pending |
| SECU-07 | Phase 2 | Pending |
| SECU-08 | Phase 2 | Pending |
| SECU-09 | Phase 1 | Pending |
| USER-01 | Phase 1 | Pending |
| USER-02 | Phase 1 | Pending |
| USER-03 | Phase 3 | Pending |
| USER-04 | Phase 3 | Pending |
| INFR-01 | Phase 1 | Pending |
| INFR-02 | Phase 1 | Pending |
| INFR-03 | Phase 1 | Pending |
| INFR-04 | Phase 1 | Pending |
| INFR-05 | Phase 1 | Pending |
| INFR-06 | Phase 1 | Pending |
| INFR-07 | Phase 5 | Pending |
| INFR-08 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 33 total
- Mapped to phases: 33
- Unmapped: 0

Note: The coverage counter previously read 32 — a recount of the requirement IDs shows 33 (AUTH 6 + TOKN 6 + SECU 9 + USER 4 + INFR 8). All 33 are mapped.

---
*Requirements defined: 2026-03-01*
*Last updated: 2026-03-01 after roadmap creation — traceability complete*
