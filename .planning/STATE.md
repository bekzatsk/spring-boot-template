# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-02)

**Core value:** Mobile/web clients can authenticate with Google, Apple, email+password, or phone+SMS OTP and receive JWT tokens. Account linking ensures one email = one user across all providers.
**Current focus:** Phase 03 — Replace Twilio Verify with self-managed SMS OTP (COMPLETE)

## Current Position

Milestone: v3.0 Self-Managed SMS OTP — COMPLETE
Phase: 03-replace-twilio-verify-with-self-managed-sms-code-generation-and-verification (COMPLETE)
Current Plan: 2 of 2 (COMPLETE)
Last activity: 2026-03-02 - Completed quick task 4: Fix rsaKeyPair bean creation failure in RsaKeyConfig

Progress: [██████████] 100% (phase 03 complete)

## Performance Metrics

**Velocity:**
- Total plans completed: 10
- Average duration: 5.4 min
- Total execution time: 0.87 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-foundation | 2/2 | 12 min | 6 min |
| 02-security-wiring | 2/3 | 15 min | 7.5 min |
| 03-google-auth-and-token-management | 2/2 | 8 min | 4 min |
| 04-apple-auth | 1/1 | 13 min | 13 min |
| 05-hardening | 1/1 | 2 min | 2 min |
| 06-restructure | 2/2 | 7 min | 3.5 min |
| 01-local-auth (v2) | 3/3 | 12 min | 4 min |
| 02-account-linking | 2/2 | 7 min | 3.5 min |
| 03-self-managed-sms | 2/2 | 15 min | 7.5 min |

**Recent Trend:**
- Last 5 plans: 3 min, 4 min, 8 min, 3 min, 4 min
- Trend: stable

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Spring MVC + Virtual Threads chosen over WebFlux — simpler model, sufficient concurrency
- Spring Authorization Server used narrowly for JWKSource/NimbusJwtEncoder only — NOT full OAuth2 server
- Refresh token rotation with 10-second grace window for mobile concurrent retry handling
- Users keyed on email (globally unique) with multi-provider support — findByEmail is primary lookup
- OAuth2AuthorizationServerAutoConfiguration excluded from TemplateApplication — prevents competing SecurityFilterChain; Spring Boot 4.0.3 package is org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet
- Dev profile uses :default values for DB credentials (convenience), prod uses bare ${ENV_VAR} (SECU-09)
- Kotlin all-open plugin configured for jakarta.persistence annotations (Entity, MappedSuperclass, Embeddable)
- User entity is a regular Kotlin class (not data class) — data class equals/hashCode breaks Hibernate proxy equality
- Manual equals/hashCode on id field only for JPA entities — safe for transient (null id) and persistent entities
- NimbusJwtDecoder.withJwkSource() used over OAuth2AuthorizationServerConfiguration.jwtDecoder() — avoids Authorization Server coupling
- Dev profile auto-generates in-memory RSA keypair — tokens do not persist across restarts (acceptable for dev)
- Jackson 3.x (tools.jackson.* namespace) used for ObjectMapper in error handlers — not com.fasterxml.jackson
- AuthenticationEntryPoint registered in both exceptionHandling and oauth2ResourceServer DSL (Pitfall 4 per research)
- Spring Boot 4.x @AutoConfigureMockMvc is in org.springframework.boot.webmvc.test.autoconfigure — not boot.test.autoconfigure.web.servlet
- H2 test-scoped dependency added — enables full @SpringBootTest with JPA without a running PostgreSQL instance
- JwtTokenService omits JwsHeader in JwtEncoderParameters — NimbusJwtEncoder infers RS256 from RSA JWKSource automatically
- [Phase 03-01]: Grace window replay returns 409 Conflict (not replacement raw token) — raw tokens not stored per TOKN-02; mobile client retries with token received from first successful rotation
- [Phase 03-01]: deleteAllByUser uses JPQL DELETE (not derived deleteBy) to avoid N+1 entity loading on family revocation
- [Phase 03-02]: GoogleIdTokenVerifier.verify() returns null on invalid token — checked explicitly, throws BadCredentialsException caught by GlobalExceptionHandler
- [Phase 03-02]: Test application.yaml must include app.auth config — test YAML overrides main YAML; auth section missing caused context init failure
- [Phase 03-02]: SecurityIntegrationTest /users/me creates real User in H2 — required because UserController now does real DB lookup instead of returning JWT claims
- [Phase 04-01]: @MockitoBean is from org.springframework.test.context.bean.override.mockito (Spring 7.x) — not from spring-boot-test; mockito-kotlin not on classpath; use plain Mockito.when()/anyString()
- [Phase 04-01]: refreshTokenRepository.deleteAll() must precede userRepository.deleteAll() in test @BeforeEach — FK constraint from refresh_tokens.user_id prevents deleting users with active tokens
- [Phase 04-01]: appleJwtDecoder bean named explicitly and injected with @Qualifier("appleJwtDecoder") — prevents NoUniqueBeanDefinitionException with resource server JwtDecoder from RsaKeyConfig
- [Phase 04-01]: JwtException from NimbusJwtDecoder.decode() wrapped as BadCredentialsException — JwtException not handled by GlobalExceptionHandler (would produce 500 without wrap)
- [Phase 05-01]: Maven Wrapper 3.3+ does not require maven-wrapper.jar — only maven-wrapper.properties needed; wrapperVersion=3.3.4
- [Phase 05-01]: H2Dialect auto-detection preferred — removing explicit database-platform eliminates HHH90000025 deprecation warning in Hibernate 7.x
- [Phase 05-01]: Rate limiting markers as // TODO comments (not stubs) — zero runtime behavior change, found via grep "TODO: rate limiting"
- [Quick-02]: JOIN FETCH rt.user on findByTokenHash fixes LazyInitializationException — open-in-view=false means persistence context closes before controller layer; fetching eagerly avoids re-opening transaction in AuthController
- [Quick-02]: SLF4J logger in GlobalExceptionHandler companion object — all catch-all handlers must log with logger.error() before returning 500 so unhandled exceptions are diagnosable
- [Phase 06-01]: GlobalExceptionHandler renamed to AuthExceptionHandler and placed in authentication/exception — semantically belongs with auth domain, not shared error utilities
- [Phase 06-01]: TokenGracePeriodException extracted from RefreshTokenService into its own file — single-responsibility for exception definitions
- [Phase 06-01]: ErrorResponse kept in shared/error (not moved) — generic DTO used by both authentication and user domains
- [Phase 06-01]: authentication/error/ package replaced by authentication/filter/ — filter handlers belong with filter layer, not generic error handling
- [Phase 06]: JwtTokenService renamed to TokenService — clearer name without JWT implementation detail leaking into service name
- [Phase 06]: GoogleAuthService renamed to GoogleOAuth2Service — explicit OAuth2 protocol reference; AppleAuthService renamed to AppleOAuth2Service — symmetric naming
- [Phase 06]: Clean Maven build required after moving compiled classes — incremental compile left stale .class files causing ConflictingBeanDefinitionException
- [Phase 01-01 v2]: V1 migration includes all columns (including password_hash and phone) — no V2 needed for greenfield project; single migration for complete target schema
- [Phase 01-01 v2]: spring-boot-starter-flyway used (not bare flyway-core) — Boot 4.x requires starter for auto-configuration
- [Phase 01-01 v2]: Flyway disabled in test profile (spring.flyway.enabled=false) — H2 incompatible with PostgreSQL-specific DDL (gen_random_uuid, TIMESTAMPTZ); H2 create-drop preserved for tests
- [Phase 01-01 v2]: Twilio config added as ${ENV_VAR:placeholder} in common section now — Plans 02/03 reference app.auth.twilio.* without modifying application.yaml again
- [Phase 01-02 v2]: localAuthenticationManager bean uses ProviderManager(DaoAuthenticationProvider) — separate from resource server JWT auth to avoid interference
- [Phase 01-02 v2]: LocalUserDetailsService scopes loadUserByUsername to (LOCAL, email) lookup — prevents cross-provider credential leakage from Google/Apple users
- [Phase 01-02 v2]: IllegalStateException -> 409 Conflict via AuthExceptionHandler — consistent with TokenGracePeriodException -> 409 pattern for resource conflicts
- [Phase 01-03]: TwilioVerifyClient interface abstracts Twilio API — @MockitoBean replaces only Twilio boundary in tests; full service layer exercised without network calls
- [Phase 01-03]: Phone users keyed on (LOCAL, phoneE164) — symmetric with email users (LOCAL, email); phone stored in both providerId and phone fields; email set to empty string (NOT NULL column)
- [Phase 01-03]: normalizeToE164() requires '+' prefix — eliminates region ambiguity; no default region configured per research
- [Quick-03]: flyway-database-postgresql added (BOM-managed 11.14.1, no explicit version) — Flyway 10+ requires this separate module for PostgreSQL support; spring-boot-starter-flyway only pulls flyway-core
- [Phase 02-01]: User constructor takes only email — providers and providerIds are mutable body fields, not constructor params
- [Phase 02-01]: FetchType.EAGER on both @ElementCollection fields (providers, providerIds) — tiny collections (max 3), needed after transaction closes (open-in-view=false)
- [Phase 02-01]: Partial unique index on email WHERE email != '' — allows multiple phone users with empty email
- [Phase 02-01]: LOCAL email users NOT migrated to user_provider_ids — LOCAL has no external provider ID; providers set entry is sufficient
- [Phase 02-02]: UserService.findOrCreateGoogleUser uses findByEmail first — links GOOGLE provider to existing account if email matches
- [Phase 02-02]: UserService.findOrCreateAppleUser uses findByAppleProviderId first (returning), then findByEmail (first login) — handles both Apple auth cases
- [Phase 02-02]: LocalAuthService.register links LOCAL credentials to existing social accounts if email exists but passwordHash is null
- [Phase 02-02]: LocalUserDetailsService checks AuthProvider.LOCAL in user.providers — prevents social-only users from local password auth
- [Phase 02-02]: UserRepository.findByPhone added for phone user lookup — replaces old findByProviderAndProviderId(LOCAL, phone)
- [Phase 02-02]: UserProfileResponse changed from single provider to providers list — API-breaking change for multi-provider model
- [Phase 03-01]: ConsoleSmsService has no @Component — registered via @Bean @ConditionalOnMissingBean(SmsService::class) in SmsSchedulerConfig; future real SMS provider defines its own @Bean SmsService to override
- [Phase 03-01]: PasswordEncoder.encode() is a Java method inferred as String? in Kotlin; !! operator required at call sites even though it never returns null
- [Phase 03-01]: Tests pre-seed H2 with passwordEncoder.encode(knownCode)!! to exercise real BCrypt verification path; old approach of mocking checkVerification return value not applicable
- [Phase 03-01]: mvnw clean required after deleting Kotlin source files — incremental compile leaves stale .class files in target/ causing BeanCreationException on test startup
- [Phase 03-01]: Phone endpoint paths renamed /phone/request-otp -> /phone/request and /phone/verify-otp -> /phone/verify; DTO field phoneNumber -> phone
- [Phase 03-02]: ArgumentCaptor.capture() returns null in Kotlin — plain Mockito's capture() is incompatible with Kotlin non-null String params; doAnswer { invocation -> capturedCode = invocation.arguments[1] as String; null } is the correct workaround when mockito-kotlin is not on classpath
- [Phase 03-02]: captureCodeOnSend() helper pattern — returns () -> String lambda; stub with doAnswer during setup, retrieve captured code after HTTP perform; decouples stubbing from code retrieval

### Roadmap Evolution

- Phase 6 added: Restructure project into layered packages — config, user, authentication with model/repository/service/controller/dto/exception subpackages
- Phase 1 (v2): Add LOCAL authentication — email+password and phone+SMS code login
- Phase 2 added: Implement account linking logic — email is globally unique across all providers, one user = one email = one account
- Phase 3 added: Replace Twilio Verify with self-managed SMS code generation and verification

### Pending Todos

None.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 1 | Fix DataSource 'url' not specified error — add default spring profile | 2026-03-01 | cd51f12 | [1-fix-datasource-url-not-specified-error-a](./quick/1-fix-datasource-url-not-specified-error-a/) |
| 2 | Fix 500 on POST /api/v1/auth/refresh — JOIN FETCH User in findByTokenHash, add exception logging | 2026-03-01 | ce3560f | [2-fix-500-internal-server-error-on-api-v1-](./quick/2-fix-500-internal-server-error-on-api-v1-/) |
| 3 | Fix Flyway "Unsupported Database: PostgreSQL 18.2" — add flyway-database-postgresql module | 2026-03-02 | 0cc5bd8 | [3-fix-flyway-unsupported-postgresql-18-2-e](./quick/3-fix-flyway-unsupported-postgresql-18-2-e/) |
| 4 | Fix RsaKeyPair bean NPE on startup — invert isNullOrBlank() conditional in RsaKeyConfig | 2026-03-02 | aef19cc | [4-fix-rsakeypair-bean-creation-failure-in-](./quick/4-fix-rsakeypair-bean-creation-failure-in-/) |

### Blockers/Concerns

- Developer note: If running Postgres.app locally on port 5432, create the `template` role or stop Postgres.app and use Docker only

## Session Continuity

Last session: 2026-03-02
Stopped at: Quick task 4 complete. Fixed inverted conditional in RsaKeyConfig.rsaKeyPair() — NPE on startup eliminated. 23 tests pass.
Resume file: None
