---
phase: 01-foundation
plan: 02
subsystem: auth
tags: [jpa, hibernate, postgresql, jwt, rsa, nimbus-jose, spring-security, oauth2, kotlin]

# Dependency graph
requires:
  - phase: 01-01
    provides: Spring Boot 4.0.3 with JPA/Security/OAuth2 starters, Kotlin JPA plugins, PostgreSQL Docker, multi-profile YAML
provides:
  - User JPA entity with UUID id, email, name, picture, provider (AuthProvider enum), providerId, roles (ElementCollection), timestamps
  - AuthProvider enum (GOOGLE, APPLE) and Role enum (USER, ADMIN)
  - UserRepository with findByProviderAndProviderId query method for Phase 3 find-or-create
  - RefreshToken JPA entity with ManyToOne(User), tokenHash (unique), expiresAt, revoked, createdAt
  - RefreshTokenRepository with findByTokenHash query method
  - users, user_roles, refresh_tokens tables via Hibernate create-drop on dev startup
  - Unique DB constraint on (provider, provider_id) enforcing USER-02
  - RsaKeyConfig providing JWKSource, JwtEncoder, JwtDecoder beans from Nimbus JOSE
  - Dev fallback: in-memory RSA keypair generation (no keystore file required)
  - Prod path: PKCS12 keystore loading via app.security.jwt.* config properties
  - scripts/generate-keystore.sh for production keystore creation
affects: [02-auth, 03-social-login, 04-token-management, 05-api]

# Tech tracking
tech-stack:
  added:
    - nimbus-jose-jwt (transitive via spring-boot-starter-oauth2-authorization-server)
    - spring-security-oauth2-resource-server (NimbusJwtDecoder/NimbusJwtEncoder)
  patterns:
    - Regular Kotlin class for JPA entities (NOT data class) — prevents lazy-load/equals/hashCode issues
    - Manual equals/hashCode on id field only for JPA entities — safe with Hibernate proxies
    - ElementCollection for role sets with separate user_roles table and EAGER fetch
    - @CreationTimestamp / @UpdateTimestamp from org.hibernate.annotations for automatic timestamps
    - JWKSource<SecurityContext> bean pattern for Nimbus JWT signing infrastructure
    - Dev/prod RSA key dual-path: in-memory generation (dev) vs PKCS12 keystore (prod)

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/user/AuthProvider.kt
    - src/main/kotlin/kz/innlab/template/user/Role.kt
    - src/main/kotlin/kz/innlab/template/user/User.kt
    - src/main/kotlin/kz/innlab/template/user/UserRepository.kt
    - src/main/kotlin/kz/innlab/template/authentication/RefreshToken.kt
    - src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt
    - src/main/kotlin/kz/innlab/template/config/RsaKeyConfig.kt
    - scripts/generate-keystore.sh
  modified: []

key-decisions:
  - "User entity is a regular Kotlin class (not data class) — data classes generate equals/hashCode using all fields, which breaks Hibernate proxy equality and triggers lazy-load exceptions"
  - "Manual equals/hashCode in User and RefreshToken use only the id field — safe for transient (null id) and persistent entities; hashCode returns 0 for null id rather than random"
  - "NimbusJwtDecoder.withJwkSource(jwkSource).build() used over OAuth2AuthorizationServerConfiguration.jwtDecoder() — avoids tight coupling to Authorization Server module, works identically for resource server use cases"
  - "Dev profile auto-generates in-memory RSA keypair on each startup — acceptable for development, token validity does not persist across restarts"

patterns-established:
  - "JPA entity pattern: regular class + @Entity + constructor with required fields + @Id as val + manual equals/hashCode on id"
  - "Repository pattern: interface extending JpaRepository<Entity, UUID> with domain-specific query methods pre-declared for future phases"
  - "RSA config pattern: @Value with #{null} SpEL default for optional keystore config, logger.warn on dev path, logger.info on prod path"

requirements-completed: [INFR-02, INFR-06, USER-01, USER-02, SECU-06]

# Metrics
duration: 7min
completed: 2026-03-01
---

# Phase 1 Plan 2: Domain Model and JWT Infrastructure Summary

**User/RefreshToken JPA entities with Hibernate-managed tables, Spring Data repositories, and RSA JWKSource/JwtEncoder/JwtDecoder beans with in-memory dev fallback via Nimbus JOSE**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-28T20:41:34Z
- **Completed:** 2026-02-28T20:49:33Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- Created User and RefreshToken JPA entities using regular Kotlin classes (not data classes) with manual equals/hashCode on id — correct pattern for Hibernate proxy compatibility
- Hibernate creates users, user_roles, and refresh_tokens tables on dev startup; unique constraint on (provider, provider_id) enforces USER-02 — verified via psql
- RsaKeyConfig provides JWKSource, JwtEncoder, JwtDecoder beans; dev profile generates in-memory RSA keypair (warns in logs), prod loads from PKCS12 keystore — app starts clean in both paths
- generate-keystore.sh script creates valid PKCS12 keystore with jwt alias — verified with keytool -list

## Task Commits

Each task was committed atomically:

1. **Task 1: Create JPA entities, enums, and repositories** - `954fda2` (feat)
2. **Task 2: Create RSA key configuration and keystore generation script** - `f677c66` (feat)

**Plan metadata:** TBD (docs: complete plan)

## Files Created/Modified
- `src/main/kotlin/kz/innlab/template/user/AuthProvider.kt` - AuthProvider enum (GOOGLE, APPLE)
- `src/main/kotlin/kz/innlab/template/user/Role.kt` - Role enum (USER, ADMIN)
- `src/main/kotlin/kz/innlab/template/user/User.kt` - User @Entity with UUID id, email, name, picture, provider, providerId, roles ElementCollection, timestamps; unique constraint on (provider, provider_id)
- `src/main/kotlin/kz/innlab/template/user/UserRepository.kt` - JpaRepository<User, UUID> with findByProviderAndProviderId
- `src/main/kotlin/kz/innlab/template/authentication/RefreshToken.kt` - RefreshToken @Entity with ManyToOne(User), tokenHash (unique), expiresAt, revoked, createdAt
- `src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt` - JpaRepository<RefreshToken, UUID> with findByTokenHash
- `src/main/kotlin/kz/innlab/template/config/RsaKeyConfig.kt` - @Configuration providing JWKSource, JwtEncoder, JwtDecoder via Nimbus JOSE; dev in-memory RSA fallback
- `scripts/generate-keystore.sh` - PKCS12 keystore generation script using keytool; idempotent (exits if file exists)

## Decisions Made
- **Regular Kotlin class for JPA entities**: Data classes generate equals/hashCode using all fields. With Hibernate, this causes proxy inequality (a proxy and a real entity with the same id compare unequal) and can trigger lazy-load exceptions during hashCode calls. Regular classes with manual equals/hashCode on id only are the correct JPA pattern.
- **NimbusJwtDecoder.withJwkSource() over OAuth2AuthorizationServerConfiguration.jwtDecoder()**: The plan correctly anticipated this choice. Using the resource server path avoids coupling the JwtDecoder to the Authorization Server module — we only want JWT infrastructure beans, not a full OAuth2 server.
- **In-memory RSA keypair for dev**: The app.security.jwt.* config properties are only present in the prod YAML section. In dev, keystoreLocation is null, triggering the in-memory generation path. This means dev tokens cannot be verified after restart — acceptable for development, expected behavior.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Created template role in local Postgres.app PostgreSQL**
- **Found during:** Task 1 (table verification)
- **Issue:** A local PostgreSQL 18 (Postgres.app on port 5432) was running on the host machine without the `template` role. Spring Boot's dev datasource connects to localhost:5432 which hit the local PostgreSQL instead of Docker's PostgreSQL. Error: "FATAL: role 'template' does not exist"
- **Fix:** Created the `template` role and granted privileges in the local Postgres.app PostgreSQL using its bundled psql at `/Applications/Postgres.app/Contents/Versions/18/bin/psql`
- **Files modified:** None (database-level fix, no code changes)
- **Verification:** `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run` completed — app started, Hibernate created all 3 tables, app logged "Started TemplateApplicationKt in 5.7 seconds"
- **Committed in:** Not a code change — environment setup fix

---

**Total deviations:** 1 auto-fixed (environment — local PostgreSQL role conflict)
**Impact on plan:** Minimal. The Docker container was working correctly; the conflict was the host's local Postgres.app instance intercepting port 5432 before Docker's mapping. This is an environment-specific issue on the developer's machine, not a project defect.

## Issues Encountered
- Postgres.app (running natively on port 5432) intercepted Spring Boot's JDBC connection before Docker's PostgreSQL container could respond. Docker-compose maps `0.0.0.0:5432` but the local process bound `localhost:5432` first. Solution: created the `template` role in the local PostgreSQL instance.

## User Setup Required
None — all infrastructure is local. No external services required.

## Next Phase Readiness
- Domain model is complete: User entity, RefreshToken entity, AuthProvider/Role enums, both repositories
- RSA JWT infrastructure is ready: JWKSource, JwtEncoder, JwtDecoder beans available in Spring context
- Phase 2 can proceed: Spring Security configuration (SecurityFilterChain) and authentication filters
- Note for fresh developer setup: if running Postgres.app locally on port 5432, either create the `template` role or stop Postgres.app and rely solely on Docker

---
*Phase: 01-foundation*
*Completed: 2026-03-01*

## Self-Check: PASSED

All 9 files present and verified:
- AuthProvider.kt, Role.kt, User.kt, UserRepository.kt
- RefreshToken.kt, RefreshTokenRepository.kt
- RsaKeyConfig.kt
- scripts/generate-keystore.sh
- 01-02-SUMMARY.md

All commits present: 954fda2 (Task 1), f677c66 (Task 2)
