---
phase: quick-6
plan: 6
subsystem: auth
tags: [sms, otp, spring-profiles, configuration]

requires:
  - phase: 03-self-managed-sms
    provides: SmsVerificationService with SecureRandom code generation and BCrypt hash storage

provides:
  - Config-driven dev code: app.auth.sms.dev-code property bypasses SecureRandom when set
  - Dev profile always uses OTP code "123456" for local testing convenience

affects: [sms-auth, dev-experience]

tech-stack:
  added: []
  patterns: ["@Value with empty-string default for profile-conditional config (${prop:} idiom)"]

key-files:
  created: []
  modified:
    - src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt
    - src/main/resources/application.yaml

key-decisions:
  - "Quick-6: @Value with empty-string default (app.auth.sms.dev-code:) — property absent in test/prod resolves to blank, triggering SecureRandom path; dev profile sets 123456"
  - "Quick-6: devCode.isNotBlank() conditional — blank string (absent property) uses SecureRandom; non-blank (dev-code set) uses config value directly"
  - "Quick-6: println(code) debug line removed — ConsoleSmsService already logs via SLF4J; dev profile known code makes println redundant"

requirements-completed: []

duration: 3min
completed: 2026-03-02
---

# Quick Task 6: Dev Profile Uses Hardcoded SMS Code Summary

**Config-driven SMS OTP: dev profile always generates "123456" via @Value injection; prod/test profiles use SecureRandom — println debug line removed**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-03-02T09:53:00Z
- **Completed:** 2026-03-02T09:56:30Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments

- Added `@Value("\${app.auth.sms.dev-code:}")` constructor parameter to `SmsVerificationService` — empty-string default means property is optional
- Replaced `String.format("%06d", random.nextInt(CODE_BOUND))` with conditional: use `devCode` when set (non-blank), otherwise SecureRandom
- Added `app.auth.sms.dev-code: "123456"` under dev profile section in `application.yaml` only (not common or prod sections)
- Removed `println(code)` debug leftover — `ConsoleSmsService` already logs codes via SLF4J
- All 23 existing tests pass — test profile has no `dev-code` set, exercises the real SecureRandom path

## Task Commits

1. **Task 1: Add dev-code config property and conditional code generation** - `3a8d486` (feat)

**Plan metadata:** see state update commit

## Files Created/Modified

- `src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt` - Added @Value devCode param, conditional code generation, removed println
- `src/main/resources/application.yaml` - Added app.auth.sms.dev-code: "123456" under dev profile

## Decisions Made

- Used `@Value("\${app.auth.sms.dev-code:}")` (colon with no default) which resolves to empty string when property absent — cleanly separates dev from prod/test without needing Spring profiles in the service class itself
- `isNotBlank()` check handles both absent (empty string) and whitespace-only values

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Local phone auth testing simplified: developer calls `/phone/request`, then uses code `123456` in `/phone/verify` without reading logs
- Production SecureRandom path unchanged and verified via existing test suite

---
*Phase: quick-6*
*Completed: 2026-03-02*
