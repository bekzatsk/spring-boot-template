---
phase: 05-add-account-management
plan: 01
subsystem: auth
tags: [verification-code, bcrypt, email, jpa, flyway]

requires:
  - phase: 04-uuid-v7
    provides: BaseEntity with UUID v7 generation
provides:
  - VerificationCode entity with purpose discriminator
  - VerificationCodeService for code lifecycle (generate, hash, store, rate-limit, verify)
  - EmailService interface with ConsoleEmailService default
  - V2 Flyway migration for verification_codes table
affects: [05-02-account-management-flows, 05-03-integration-tests]

tech-stack:
  added: []
  patterns:
    - "VerificationCode entity with purpose enum discriminator — shared across forgot-password, change-email, change-phone"
    - "VerificationCodeService mirrors SmsVerificationService pattern — BCrypt hashing, rate limiting, max attempts"
    - "ConsoleEmailService registered via @Bean @ConditionalOnMissingBean — same pattern as ConsoleSmsService"

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/authentication/model/VerificationCode.kt
    - src/main/kotlin/kz/innlab/template/authentication/model/VerificationPurpose.kt
    - src/main/kotlin/kz/innlab/template/authentication/repository/VerificationCodeRepository.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/EmailService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/ConsoleEmailService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/VerificationCodeService.kt
    - src/main/resources/db/migration/V2__add_verification_codes.sql
  modified:
    - src/main/kotlin/kz/innlab/template/config/SmsSchedulerConfig.kt
    - src/main/resources/application.yaml

key-decisions:
  - "VerificationCode uses VARCHAR(50) purpose column (not PostgreSQL ENUM) — H2 compatible, consistent with user_providers pattern"
  - "VerificationCodeService returns Pair<UUID, String> (verificationId, rawCode) — caller handles delivery via EmailService or SmsService"
  - "Dev profile app.auth.verification.dev-code: 123456 — consistent with SMS dev-code pattern"

patterns-established:
  - "Purpose-discriminated verification codes — single entity/table for all verification flows"
  - "Service returns verification ID + raw code; caller decides delivery channel"

requirements-completed: [ACCT-05]

duration: 2 min
completed: 2026-03-03
---

# Phase 05 Plan 01: Verification Code Infrastructure Summary

**Shared verification code lifecycle with VerificationCode entity, VerificationCodeService (generate/hash/rate-limit/verify), EmailService interface, and V2 Flyway migration**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-03T03:44:28Z
- **Completed:** 2026-03-03T03:46:12Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- VerificationCode entity extends BaseEntity with purpose discriminator (FORGOT_PASSWORD, CHANGE_EMAIL, CHANGE_PHONE)
- VerificationCodeService generates 6-digit codes, BCrypt hashes, enforces rate limiting (60s), max attempts (3), and 15-minute expiry
- EmailService interface with ConsoleEmailService registered via @ConditionalOnMissingBean
- V2 Flyway migration creates verification_codes table with composite index

## Task Commits

1. **Task 1: Create VerificationCode entity, VerificationPurpose enum, repository, and V2 migration** - `1637ba5` (feat)
2. **Task 2: Create EmailService, ConsoleEmailService, VerificationCodeService, and config updates** - `935be3d` (feat)

## Files Created/Modified
- `src/main/kotlin/kz/innlab/template/authentication/model/VerificationCode.kt` - JPA entity with identifier, purpose, codeHash, expiresAt, newValue, userId
- `src/main/kotlin/kz/innlab/template/authentication/model/VerificationPurpose.kt` - Enum: FORGOT_PASSWORD, CHANGE_EMAIL, CHANGE_PHONE
- `src/main/kotlin/kz/innlab/template/authentication/repository/VerificationCodeRepository.kt` - Rate-limit check, cleanup, bulk-delete queries
- `src/main/kotlin/kz/innlab/template/authentication/service/EmailService.kt` - Email delivery interface
- `src/main/kotlin/kz/innlab/template/authentication/service/ConsoleEmailService.kt` - Dev/test console logger implementation
- `src/main/kotlin/kz/innlab/template/authentication/service/VerificationCodeService.kt` - Code generation, hashing, rate limiting, verification
- `src/main/resources/db/migration/V2__add_verification_codes.sql` - verification_codes table with VARCHAR(50) purpose
- `src/main/kotlin/kz/innlab/template/config/SmsSchedulerConfig.kt` - Added VerificationCodeRepository cleanup and EmailService bean
- `src/main/resources/application.yaml` - Added dev profile app.auth.verification.dev-code: 123456

## Decisions Made
None - followed plan as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Verification code infrastructure complete, ready for Plan 02 (account management service and REST endpoints)
- VerificationCodeService API: createCode(identifier, purpose, newValue?, userId?) returns Pair<UUID, String>; verifyCode(verificationId, identifier, purpose, code) returns VerificationCode

---
*Phase: 05-add-account-management*
*Completed: 2026-03-03*
