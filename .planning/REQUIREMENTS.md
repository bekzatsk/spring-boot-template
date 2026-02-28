# Requirements: Spring Boot Auth Template

**Defined:** 2026-03-01
**Core Value:** Mobile/web clients can authenticate via Google or Apple ID tokens and receive JWT access/refresh tokens that secure all API endpoints

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Authentication

- [ ] **AUTH-01**: Client can send Google ID token to POST /api/v1/auth/google and receive JWT access + refresh tokens
- [ ] **AUTH-02**: Client can send Apple ID token to POST /api/v1/auth/apple and receive JWT access + refresh tokens
- [ ] **AUTH-03**: Backend verifies Google ID token via google-api-client library
- [ ] **AUTH-04**: Backend verifies Apple ID token by fetching Apple public keys and validating JWT signature/claims
- [ ] **AUTH-05**: If user doesn't exist, auto-create from ID token claims (email, name, picture)
- [ ] **AUTH-06**: JWT access tokens are RS256-signed with 15-minute expiry
- [ ] **AUTH-07**: Refresh tokens are opaque, stored in DB, 30-day expiry
- [ ] **AUTH-08**: POST /api/v1/auth/refresh rotates refresh token (old one invalidated, new one issued)
- [ ] **AUTH-09**: Refresh token reuse detection — if revoked token is reused, all tokens for that user are revoked
- [ ] **AUTH-10**: POST /api/v1/auth/revoke invalidates refresh token (logout)

### User Management

- [ ] **USER-01**: User JPA entity with UUID id, email, name, picture, provider, providerId, roles, createdAt, updatedAt
- [ ] **USER-02**: GET /api/v1/users/me returns the current authenticated user's profile
- [ ] **USER-03**: AuthProvider enum: LOCAL, GOOGLE, APPLE

### Security Infrastructure

- [ ] **SEC-01**: Spring Security stateless filter chain with JWT authentication filter
- [ ] **SEC-02**: All /api/** endpoints require authentication except /api/v1/auth/**
- [ ] **SEC-03**: RSA key pair for JWT signing via keystore (.jks) file and JWKSource
- [ ] **SEC-04**: CORS configured with restrictive, configurable allowed origins
- [ ] **SEC-05**: CSRF disabled (stateless API)
- [ ] **SEC-06**: Consistent JSON error responses: { "error": "...", "message": "...", "status": N }
- [ ] **SEC-07**: Jakarta Validation on request DTOs (AuthRequest, RefreshRequest)
- [ ] **SEC-08**: Rate limiting TODOs placed at auth endpoints

### Data & Infrastructure

- [ ] **INFRA-01**: Spring Data JPA + Hibernate for User and RefreshToken entities
- [ ] **INFRA-02**: Flyway migration V1__init.sql creating users and refresh_tokens tables
- [ ] **INFRA-03**: Docker Compose with PostgreSQL 18
- [ ] **INFRA-04**: Virtual Threads enabled via spring.threads.virtual.enabled=true
- [ ] **INFRA-05**: application.yml with dev and prod profiles
- [ ] **INFRA-06**: All secrets via environment variable placeholders (${ENV_VAR})
- [ ] **INFRA-07**: .env.example documenting all required environment variables
- [ ] **INFRA-08**: pom.xml with all dependencies (Spring Security, Authorization Server, OAuth2 Resource Server, Flyway, google-api-client, etc.)

### Code Quality

- [ ] **QUAL-01**: Idiomatic Kotlin — data classes for DTOs, no !! operators, proper null safety
- [ ] **QUAL-02**: Constructor injection everywhere, no @Autowired
- [ ] **QUAL-03**: SLF4J logging with meaningful messages
- [ ] **QUAL-04**: KDoc comments on public APIs
- [ ] **QUAL-05**: Clean separation: controllers handle HTTP, services contain logic, repositories handle data
- [ ] **QUAL-06**: Modular package layout: config/, user/, authentication/ with sub-packages

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Extended Auth

- **AUTH-V2-01**: Local email/password authentication (register, login, password reset)
- **AUTH-V2-02**: Rate limiting implementation on auth endpoints
- **AUTH-V2-03**: Account linking (merge Google + Apple accounts for same email)

## Out of Scope

| Feature | Reason |
|---------|--------|
| WebFlux / reactive stack | Using Spring MVC + Virtual Threads instead |
| Full OAuth2 Authorization Server | Only using for JWT encoding/JWKSource |
| Session-based auth | Stateless JWT only |
| Frontend/mobile client | API-only template |
| Email verification / password reset | OAuth2 only for v1 |
| Admin panel | No admin UI needed for template |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| (To be filled by roadmapper) | | |

**Coverage:**
- v1 requirements: 31 total
- Mapped to phases: 0
- Unmapped: 31

---
*Requirements defined: 2026-03-01*
*Last updated: 2026-03-01 after initial definition*
