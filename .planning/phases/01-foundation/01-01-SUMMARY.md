---
phase: 01-foundation
plan: 01
subsystem: infra
tags: [spring-boot, kotlin, jpa, postgresql, docker, oauth2, security, virtual-threads]

# Dependency graph
requires: []
provides:
  - Spring Boot 4.0.3 project with JPA, Security, OAuth2 authorization server, and Validation starters
  - Kotlin JPA compiler plugins (jpa, all-open, noarg) configured for jakarta.persistence annotations
  - PostgreSQL 18 Docker container via docker-compose.yml
  - Multi-profile application.yaml (common/dev/prod) with Virtual Threads enabled
  - .env.example documenting all required environment variables
  - .gitignore excluding keystore files and .env
affects: [02-auth, 03-social-login, 04-token-management, 05-api]

# Tech tracking
tech-stack:
  added:
    - spring-boot-starter-data-jpa
    - spring-boot-starter-security
    - spring-boot-starter-oauth2-authorization-server
    - spring-boot-starter-validation
    - kotlin-maven-noarg (compiler plugin)
    - postgres:18 (Docker image)
  patterns:
    - Multi-document YAML for Spring profiles (common/dev/prod)
    - Bare ${ENV_VAR} for prod secrets (no defaults), :default syntax for dev convenience
    - OAuth2AuthorizationServerAutoConfiguration excluded — use only JWKSource/NimbusJwtEncoder beans

key-files:
  created:
    - docker-compose.yml
    - .env.example
  modified:
    - pom.xml
    - src/main/resources/application.yaml
    - src/main/kotlin/kz/innlab/template/TemplateApplication.kt
    - .gitignore

key-decisions:
  - "OAuth2AuthorizationServerAutoConfiguration excluded from TemplateApplication to prevent competing SecurityFilterChain — we use only JWT infrastructure beans (JWKSource, NimbusJwtEncoder), not the full authorization server"
  - "Dev profile uses :default values for DB credentials (convenience), prod profile uses bare ${ENV_VAR} with no defaults (SECU-09 compliance)"
  - "Virtual Threads enabled in common profile section (spring.threads.virtual.enabled=true) for all environments"
  - "Kotlin all-open plugin configured for jakarta.persistence.Entity, MappedSuperclass, Embeddable — required for Hibernate lazy loading proxies"
  - "Auto-config class for Spring Boot 4.0.3 is org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration (different package from Spring Boot 3.x)"

patterns-established:
  - "YAML multi-document profiles: common section has no on-profile, dev/prod sections use spring.config.activate.on-profile"
  - "Env var pattern: ${VAR:default} in dev, ${VAR} (no default) in prod for sensitive values"
  - "All Jakarta persistence annotations open via all-open compiler plugin"

requirements-completed: [INFR-01, INFR-03, INFR-04, INFR-05, SECU-09]

# Metrics
duration: 5min
completed: 2026-03-01
---

# Phase 1 Plan 1: Build Infrastructure Summary

**Spring Boot 4.0.3 project with JPA/Security/OAuth2 starters, Kotlin JPA plugins, PostgreSQL 18 via Docker, and multi-profile YAML with Virtual Threads enabled**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-28T20:33:05Z
- **Completed:** 2026-03-01T01:38:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Added 4 Spring Boot starters (data-jpa, security, oauth2-authorization-server, validation) and configured Kotlin JPA compiler plugins (jpa, all-open, noarg) for Jakarta persistence annotations
- Created docker-compose.yml running PostgreSQL 18 with healthcheck — verified accepting connections
- Created multi-document application.yaml (common/dev/prod) with Virtual Threads enabled and zero hardcoded secrets in prod profile (SECU-09)
- Created .env.example documenting all 7 required environment variables, updated .gitignore to exclude keystore and .env files

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Maven dependencies and Kotlin JPA compiler plugins** - `e143ba6` (feat)
2. **Task 2: Create Docker Compose, application.yaml profiles, and .env.example** - `0c2d613` (feat)

**Plan metadata:** TBD (docs: complete plan)

## Files Created/Modified
- `pom.xml` - Added JPA/Security/OAuth2/Validation starters; configured kotlin-maven-plugin with jpa, all-open, spring plugins and noarg dependency
- `src/main/kotlin/kz/innlab/template/TemplateApplication.kt` - Added @SpringBootApplication exclude for OAuth2AuthorizationServerAutoConfiguration
- `docker-compose.yml` - PostgreSQL 18 service with health check, named volume, port 5432
- `src/main/resources/application.yaml` - Multi-document YAML: common (Virtual Threads, jpa.open-in-view=false), dev (create-drop, show-sql), prod (validate, bare env vars)
- `.env.example` - Documents DB_NAME, DB_USERNAME, DB_PASSWORD, JWT_KEYSTORE_LOCATION, JWT_KEYSTORE_PASSWORD, JWT_KEY_ALIAS, SPRING_PROFILES_ACTIVE
- `.gitignore` - Added *.p12, *.jks, jwt-keystore.*, .env exclusions with !.env.example exception

## Decisions Made
- **OAuth2AuthorizationServerAutoConfiguration exclusion**: In Spring Boot 4.0.3, the auto-config class package changed to `org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet` (verified by inspecting the JAR). Excluding it prevents Spring from setting up a full OAuth2 authorization server SecurityFilterChain; we only need JWT infrastructure beans.
- **Dev profile defaults**: Dev uses `:default` colon syntax (`${DB_NAME:template}`) for convenience while working locally. Prod uses bare `${ENV_VAR}` with no defaults, which causes startup failure if not set — enforcing the requirement that secrets must come from environment.
- **Virtual Threads in common section**: Enabled in the common (top) document so both dev and prod benefit. Spring MVC + Virtual Threads is the established architecture decision from research.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Resolved missing .mvn/wrapper directory for mvnw**
- **Found during:** Task 1 (dependency verification)
- **Issue:** `./mvnw` script failed because `.mvn/wrapper/maven-wrapper.properties` was missing from the repository
- **Fix:** Used system `mvn` command directly for all verification steps — no wrapper fix needed, system Maven 3.9.11 is sufficient for this project
- **Files modified:** None (workaround, not a file fix)
- **Verification:** `mvn dependency:resolve` → BUILD SUCCESS; `mvn compile` → BUILD SUCCESS with all three Kotlin plugins applied
- **Committed in:** Part of normal execution flow

---

**Total deviations:** 1 auto-fixed (blocking — missing Maven wrapper)
**Impact on plan:** Minimal. System Maven used for all verification; this does not affect the shipped artifacts or future phases.

## Issues Encountered
- The `./mvnw` wrapper script lacked `.mvn/wrapper/maven-wrapper.properties` — switched to system `mvn` for verification. All compilation and dependency resolution succeeded.
- Spring Boot 4.0.3 moved the OAuth2 Authorization Server auto-config to a new package (`org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet`). The plan anticipated this might happen and correctly instructed to check the JAR.

## User Setup Required
None — all infrastructure is local Docker + Maven. No external services.

## Next Phase Readiness
- All required starters are on the classpath and compile correctly
- PostgreSQL 18 starts with `docker compose up -d` and is ready in under 30 seconds
- Dev profile is ready: run with `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run` (after starting Docker)
- Plan 02 can proceed: JPA entities, Spring Security config, JWT keypair generation

---
*Phase: 01-foundation*
*Completed: 2026-03-01*

## Self-Check: PASSED

All files present: pom.xml, docker-compose.yml, src/main/resources/application.yaml, .env.example, .gitignore, TemplateApplication.kt, 01-01-SUMMARY.md
All commits present: e143ba6 (Task 1), 0c2d613 (Task 2)
