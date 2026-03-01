---
phase: 01-add-local-authentication-email-password-and-phone-sms-code-login
plan: "01"
subsystem: database
tags: [flyway, postgresql, twilio, libphonenumber, jpa, kotlin]

# Dependency graph
requires: []
provides:
  - Flyway-managed PostgreSQL schema with complete users/user_roles/refresh_tokens tables
  - AuthProvider.LOCAL enum value enabling local authentication flows
  - Nullable passwordHash and phone columns on User entity
  - Twilio SDK 11.3.3 and libphonenumber 8.13.52 on classpath
  - Dev profile switched to flyway+validate (not create-drop)
affects:
  - 01-02: email+password registration/login service layer
  - 01-03: phone+SMS OTP service layer

# Tech tracking
tech-stack:
  added:
    - spring-boot-starter-flyway (schema migration)
    - com.twilio.sdk:twilio:11.3.3 (SMS OTP delivery)
    - com.googlecode.libphonenumber:libphonenumber:8.13.52 (E.164 normalization)
  patterns:
    - Flyway single-file V1 migration captures complete target schema for greenfield projects
    - Test profile disables Flyway (H2 incompatible with PostgreSQL DDL); H2 create-drop preserved
    - Twilio config uses ${ENV_VAR:placeholder} pattern in dev/test, bare ${ENV_VAR} in prod

key-files:
  created:
    - src/main/resources/db/migration/V1__initial_schema.sql
  modified:
    - pom.xml
    - src/main/kotlin/kz/innlab/template/user/model/AuthProvider.kt
    - src/main/kotlin/kz/innlab/template/user/model/User.kt
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml

key-decisions:
  - "V1 migration includes password_hash and phone columns directly — no V2 needed for greenfield project with no production data"
  - "spring-boot-starter-flyway used (not bare flyway-core) — required for Spring Boot 4.x auto-configuration"
  - "Test profile keeps H2 create-drop with Flyway disabled — PostgreSQL SQL (gen_random_uuid, TIMESTAMPTZ) not compatible with H2"
  - "Twilio config added as placeholder in common section — Plans 02 and 03 will wire TwilioConfig @ConfigurationProperties bean"

patterns-established:
  - "Flyway migration naming: V{N}__{description}.sql in classpath:db/migration"
  - "Dev/prod profiles have flyway.enabled=true; test profile has flyway.enabled=false"

requirements-completed:
  - LOCAL-FOUNDATION

# Metrics
duration: 8min
completed: 2026-03-02
---

# Phase 01 Plan 01: Data Model Foundation and Dependencies Summary

**Flyway schema migration, AuthProvider.LOCAL enum value, User entity local-auth columns, and three new Maven dependencies (Flyway, Twilio SDK, libphonenumber) added — all 9 existing tests pass unchanged**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-01T19:48:30Z
- **Completed:** 2026-03-01T19:56:30Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Added three Maven dependencies: spring-boot-starter-flyway, Twilio SDK 11.3.3, libphonenumber 8.13.52
- Extended AuthProvider enum with LOCAL value and User entity with nullable passwordHash and phone columns
- Created V1__initial_schema.sql capturing the complete PostgreSQL schema (users, user_roles, refresh_tokens) including local-auth columns
- Updated dev and prod profiles to use Flyway with ddl-auto:validate; test profile retains H2 create-drop with Flyway disabled
- Added Twilio configuration placeholders in application.yaml for Plans 02 and 03 to wire

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Maven dependencies and extend User entity + AuthProvider enum** - `aa435d1` (feat)
2. **Task 2: Create Flyway migrations and update application.yaml profiles** - `80e6ef9` (feat)

**Plan metadata:** (docs commit to follow)

## Files Created/Modified

- `pom.xml` - Added spring-boot-starter-flyway, twilio 11.3.3, libphonenumber 8.13.52
- `src/main/kotlin/kz/innlab/template/user/model/AuthProvider.kt` - Added LOCAL enum value
- `src/main/kotlin/kz/innlab/template/user/model/User.kt` - Added nullable passwordHash and phone columns
- `src/main/resources/db/migration/V1__initial_schema.sql` - Complete schema: users, user_roles, refresh_tokens with all local-auth columns
- `src/main/resources/application.yaml` - Dev profile: Flyway enabled + ddl-auto:validate; prod profile: Flyway config added; Twilio placeholders in common section
- `src/test/resources/application.yaml` - Added spring.flyway.enabled=false and Twilio placeholder config for tests

## Decisions Made

- **V1 as single complete migration:** Since this is a greenfield template with no production data (previously using create-drop), combining the initial schema and local-auth columns into V1 is cleaner than creating a V2 immediately. Plans 02 and 03 can add V2/V3 if they require new tables.
- **spring-boot-starter-flyway over bare flyway-core:** Boot 4.x starter provides Flyway auto-configuration integrated with Spring's DataSource.
- **Flyway disabled in test profile:** H2 does not support PostgreSQL-specific syntax (gen_random_uuid(), TIMESTAMPTZ). The existing create-drop approach with H2 is preserved for test isolation.
- **Twilio config as placeholder:** Added in common section now so Plans 02/03 can reference `app.auth.twilio.*` properties without touching application.yaml again.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required for this plan. Twilio credentials will be needed when Plan 03 (phone SMS) is implemented.

## Next Phase Readiness

- Data model foundation complete. Plan 02 can implement email+password registration/login service layer.
- Plan 03 can implement phone+SMS OTP using the Twilio SDK already on classpath.
- No blockers or concerns.

---
*Phase: 01-add-local-authentication-email-password-and-phone-sms-code-login*
*Completed: 2026-03-02*

## Self-Check: PASSED

- FOUND: src/main/resources/db/migration/V1__initial_schema.sql
- FOUND: src/main/resources/application.yaml
- FOUND: src/test/resources/application.yaml
- FOUND: 01-01-SUMMARY.md
- FOUND commit: aa435d1 (Task 1)
- FOUND commit: 80e6ef9 (Task 2)
