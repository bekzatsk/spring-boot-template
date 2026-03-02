# Spring Boot Auth Template

## What This Is

A production-ready Spring Boot 4 backend API template with stateless JWT authentication via Google OAuth2, Apple Sign-In, email+password, and phone+SMS OTP. Built with Kotlin on Java 25 with Virtual Threads. Multi-provider account linking: one email = one user = N providers. Designed as a reusable starting point: rename the package and artifact to bootstrap new projects with auth already wired.

## Core Value

Mobile/web clients can authenticate with Google or Apple ID tokens, email+password, or phone+SMS OTP and receive JWT access/refresh tokens that secure all API endpoints. Account linking ensures one email = one user across all providers.

## Requirements

### Validated

- ✓ Spring Boot 4 project structure with Kotlin — v1.0
- ✓ Maven build system with mvnw wrapper — v1.0
- ✓ PostgreSQL JDBC driver configured — v1.0
- ✓ Jackson + Kotlin module for JSON serialization — v1.0
- ✓ Basic application entry point (TemplateApplication.kt) — v1.0
- ✓ Java 25 with Virtual Threads enabled — v1.0
- ✓ Spring Data JPA + Hibernate for persistence — v1.0
- ✓ Docker + docker-compose with PostgreSQL 18 — v1.0
- ✓ Spring Security 7+ stateless configuration (no sessions, CSRF disabled) — v1.0
- ✓ JWT access tokens (RS256, 15-min expiry) via Spring Authorization Server JWKSource — v1.0
- ✓ Refresh token rotation (opaque, DB-stored, single-use with reuse detection) — v1.0
- ✓ Google OAuth2 ID token verification (via google-api-client) — v1.0
- ✓ Apple OAuth2 ID token verification (fetch Apple public keys, validate JWT) — v1.0
- ✓ Auth endpoints: POST /api/v1/auth/google, /apple, /refresh, /revoke — v1.0
- ✓ User entity (UUID, email, name, picture, provider, providerId, roles, timestamps) — v1.0
- ✓ GET /api/v1/users/me endpoint — v1.0
- ✓ RSA keystore — dev auto-generates in-memory keypair, prod uses .jks file — v1.0
- ✓ Bearer token validation via Spring's BearerTokenAuthenticationFilter — v1.0
- ✓ Consistent JSON error responses: { error, message, status } — v1.0
- ✓ CORS configuration (restrictive, configurable allowed origins) — v1.0
- ✓ Input validation with Jakarta Validation — v1.0
- ✓ application.yml with dev/prod profiles, environment variable placeholders — v1.0
- ✓ .env.example with all required environment variables documented — v1.0
- ✓ Domain-based package layout: config/, user/, authentication/ with layered sub-packages — v1.0

- ✓ Email+password local registration and login — v2.0
- ✓ Phone+SMS OTP authentication via Twilio Verify — v2.0
- ✓ Flyway-managed schema migrations (V1 initial, V2 account linking) — v2.0
- ✓ Multi-provider User entity with @ElementCollection (providers Set, providerIds Map) — v2.0
- ✓ Account linking: one email = one user across LOCAL, GOOGLE, APPLE providers — v2.0
- ✓ Email-first lookup (findByEmail) as universal user identity key — v2.0

### Active

(None — next milestone requirements TBD via `/gsd:new-milestone`)

### Out of Scope

- Frontend / web UI — backend API only, clients are separate projects
- WebFlux / reactive stack — using Spring MVC with Virtual Threads instead
- Full OAuth2 Authorization Server — only using spring-authorization-server for JWT signing (JWKSource, JWT encoder)
- Session-based auth — stateless JWT only
- Rate limiting implementation — TODO markers only; implementation depends on deployment (API gateway vs in-app)
- Admin panel or user management endpoints beyond /users/me

## Context

- **Current state:** v2.0 shipped. 22 integration tests passing. Local auth (email+password, phone+SMS OTP) and multi-provider account linking added on top of v1.0.
- **Tech stack:** Spring Boot 4.0.3, Kotlin, Java 25, Maven, PostgreSQL 18, Spring Security 7, Spring Authorization Server (JWT only), Flyway, Twilio Verify, libphonenumber.
- **Package structure:** `kz.innlab.template` with `config/`, `user/{model,repository,service,controller,dto}`, `authentication/{model,repository,service,controller,dto,exception,filter}`.
- The "template" naming is a placeholder. When creating a new project, rename package and artifact.
- Target clients: mobile apps (iOS/Android) and web SPAs that handle Google/Apple sign-in client-side and send ID tokens to the backend.
- Server runs on port 7070.

## Constraints

- **Tech stack**: Spring Boot 4+ with Spring MVC (NOT WebFlux), Kotlin, Java 25, Maven
- **Auth approach**: ID token verification only — no OAuth2 authorization code flow, no redirect-based login
- **JWT signing**: RS256 via Spring Authorization Server's JWKSource (dev: auto-generated in-memory keypair; prod: .jks keystore)
- **Database**: PostgreSQL via Spring Data JPA + Hibernate (connection: localhost:5432, db: template, user: postgres, password: postgres)
- **No hardcoded secrets**: All sensitive values via environment variables / ${ENV_VAR} placeholders in application.yml
- **Code style**: Idiomatic Kotlin — data classes for DTOs, regular classes for JPA entities, constructor injection, no @Autowired

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Spring MVC + Virtual Threads over WebFlux | Simpler programming model, Virtual Threads provide sufficient concurrency without reactive complexity | ✓ Good |
| Google/Apple ID token verification (not auth code flow) | Mobile-first — clients already have native sign-in SDKs that produce ID tokens | ✓ Good |
| Spring Authorization Server for JWT only | Leverages battle-tested JWT infrastructure (JWKSource, encoder) without full OAuth2 server overhead | ✓ Good — required excluding OAuth2AuthorizationServerAutoConfiguration |
| Refresh token rotation with reuse detection | Prevents token theft — if a refresh token is reused, all tokens for that user are revoked | ✓ Good — 10s grace window handles mobile concurrent retries |
| Domain-based package layout (user/, authentication/) | Better than layer-based for template reuse — each domain is self-contained | ✓ Good — layered sub-packages added in Phase 6 |
| RSA keystore with dev auto-generation | Dev auto-generates in-memory keypair (no file needed); prod uses .jks keystore | ✓ Good |
| open-in-view: false | Prevents LazyInitializationException leaking into controllers; requires explicit JOIN FETCH | ✓ Good — fixed one bug (Quick-02) with JOIN FETCH pattern |
| User entity as regular class (not data class) | data class equals/hashCode breaks Hibernate proxy equality | ✓ Good |
| Jackson 3.x (tools.jackson.* namespace) | Spring Boot 4.x ships Jackson 3.x; old com.fasterxml namespace no longer works | ✓ Good |
| Multi-provider User entity with @ElementCollection | providers Set + providerIds Map replaces single provider/providerId; FetchType.EAGER since tiny collections (max 3 entries) | ✓ Good |
| Email-first account linking | findByEmail() as primary lookup; link provider to existing account if email matches | ✓ Good — one user per email across all providers |
| Flyway for schema migrations | V1 initial schema, V2 account linking; disabled in test profile (H2 uses create-drop) | ✓ Good |
| Twilio Verify for phone OTP | Interface abstraction (TwilioVerifyClient) for testability; E.164 format enforced | ✓ Good |

---
*Last updated: 2026-03-02 after v2.0 milestone (Phase 02 — account linking)*
