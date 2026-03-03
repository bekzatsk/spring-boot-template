---
phase: 05-add-account-management
plan: 02
subsystem: auth
tags: [account-management, forgot-password, change-password, change-email, change-phone, rest-api]

requires:
  - phase: 05-01-verification-code-infrastructure
    provides: VerificationCodeService, EmailService, VerificationPurpose enum
provides:
  - AccountManagementService with 7 methods for all 4 account flows
  - AccountManagementController with 5 authenticated endpoints
  - AuthController extended with 2 unauthenticated forgot-password endpoints
  - 7 DTO data classes with jakarta.validation
affects: [05-03-integration-tests]

tech-stack:
  added: []
  patterns:
    - "Anti-enumeration: forgot-password always returns 202 regardless of email existence"
    - "Two-step verification: request sends code, verify confirms code and applies change"
    - "Race condition protection: uniqueness re-checked at verify step"
    - "Session revocation: all refresh tokens deleted after password reset/change"

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/authentication/dto/ForgotPasswordRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/ResetPasswordRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/ChangePasswordRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/ChangeEmailRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/VerifyChangeEmailRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/ChangePhoneRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/VerifyChangePhoneRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/AccountManagementService.kt
    - src/main/kotlin/kz/innlab/template/authentication/controller/AccountManagementController.kt
  modified:
    - src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt

key-decisions:
  - "userId.toString() as identifier for CHANGE_EMAIL and CHANGE_PHONE — stable during email/phone change flow, rate limiting is per-user"
  - "forgot-password returns 202 Accepted with nullable verificationId — null when email not found (anti-enumeration)"
  - "Password reset/change revokes all refresh tokens — security best practice per research"

patterns-established:
  - "Anti-enumeration pattern: always return same HTTP status regardless of entity existence"
  - "Two-step verification with uniqueness re-check at confirm step for race condition protection"

requirements-completed: [ACCT-01, ACCT-02, ACCT-03, ACCT-04]

duration: 2 min
completed: 2026-03-03
---

# Phase 05 Plan 02: Account Management Service and Endpoints Summary

**AccountManagementService with 7 methods orchestrating forgot-password, change-password, change-email, change-phone flows via REST endpoints with anti-enumeration and race condition protection**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-03T03:48:16Z
- **Completed:** 2026-03-03T03:50:30Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments
- AccountManagementService orchestrates all 4 account flows with proper validation, uniqueness checks, and token revocation
- 7 new REST endpoints: 2 unauthenticated (forgot/reset password) + 5 authenticated (change-*)
- Anti-enumeration: forgot-password always returns 202 regardless of email existence
- Race condition protection: change-email and change-phone re-verify uniqueness at confirm step

## Task Commits

1. **Task 1: Create DTOs and AccountManagementService** - `ac9e14e` (feat)
2. **Task 2: Create AccountManagementController and extend AuthController** - `c0e7d69` (feat)

## Files Created/Modified
- `src/main/kotlin/kz/innlab/template/authentication/dto/*.kt` - 7 DTO data classes with jakarta.validation
- `src/main/kotlin/kz/innlab/template/authentication/service/AccountManagementService.kt` - Service with 7 methods
- `src/main/kotlin/kz/innlab/template/authentication/controller/AccountManagementController.kt` - 5 authenticated endpoints
- `src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt` - Extended with forgot-password and reset-password

## Decisions Made
- userId.toString() as identifier for CHANGE_EMAIL/CHANGE_PHONE (stable during flow, per-user rate limiting)
- forgot-password returns 202 with nullable verificationId (anti-enumeration)
- Password reset/change revokes all refresh tokens (security best practice)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 7 endpoints implemented, ready for Plan 03 (integration tests)
- All existing 23 tests pass

---
*Phase: 05-add-account-management*
*Completed: 2026-03-03*
