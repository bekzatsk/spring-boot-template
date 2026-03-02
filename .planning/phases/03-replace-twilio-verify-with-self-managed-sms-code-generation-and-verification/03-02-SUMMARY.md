---
phase: 03-replace-twilio-verify-with-self-managed-sms-code-generation-and-verification
plan: 02
subsystem: auth
tags: [sms, otp, mockito, integration-testing, h2, bcrypt, argcaptor]

requires:
  - phase: 03-replace-twilio-verify-with-self-managed-sms-code-generation-and-verification
    provides: SmsVerificationService, SmsService interface, renamed endpoints (/phone/request, /phone/verify), phone field DTOs

provides:
  - PhoneAuthIntegrationTest with 7 tests using doAnswer-based code capture (not pre-seeded H2)
  - Renamed endpoints confirmed: /phone/request and /phone/verify
  - Renamed DTO fields confirmed: phone (not phoneNumber)
  - Rate limiting test (request OTP twice in 60s -> 409 Conflict)

affects:
  - future SMS integration tests (captureCodeOnSend() pattern for plain Mockito + Kotlin)

tech-stack:
  added: []
  patterns:
    - doAnswer-based code capture for plain Mockito in Kotlin — ArgumentCaptor.capture() returns null in Kotlin (non-null String fails); doAnswer captures args[1] during invocation instead
    - captureCodeOnSend() helper returns () -> String lambda; captured during stubbing setup, retrieved after perform call
    - All 7 tests mock only SmsService (delivery), exercise real SmsVerificationService code gen + BCrypt hash + H2 store + verify path

key-files:
  created: []
  modified:
    - src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt

key-decisions:
  - "doAnswer-based code capture used instead of ArgumentCaptor — ArgumentCaptor.capture() returns null in Kotlin, which fails non-null String parameter; doAnswer captures args[1] during real invocation and stores in local var accessible via lambda"
  - "Task 1 (DTO rename + endpoint rename) was already complete from Phase 03-01 as a deviation fix — no code changes needed; verified via compile + grep"
  - "captureCodeOnSend() pattern: stub smsService.sendCode with doAnswer that writes args[1] (code) to captured var, return () -> String lambda for deferred retrieval after HTTP call"

requirements-completed: [SMS-07, SMS-09]

duration: 3min
completed: 2026-03-02
---

# Phase 03 Plan 02: Rename Endpoints/DTOs and Rewrite Integration Tests Summary

**7-test PhoneAuthIntegrationTest using doAnswer code capture to exercise the full real SmsVerificationService -> H2 -> BCrypt verification path without pre-seeded hashes**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-02T03:30:01Z
- **Completed:** 2026-03-02T03:33:49Z
- **Tasks:** 2 (Task 1 was already complete; Task 2 is the primary deliverable)
- **Files modified:** 1

## Accomplishments

- Rewrote PhoneAuthIntegrationTest from 6 pre-seeded-hash tests to 7 real-flow tests using doAnswer code capture
- Added 7th test: rate limiting returns 409 when second OTP requested within 60-second window
- Tests now exercise the actual SecureRandom code generation path and capture the real code for verification
- Confirmed all endpoint renames and DTO field renames from Phase 03-01 are intact and compile cleanly
- Full test suite: 23 tests, 0 failures

## Task Commits

Each task was committed atomically:

1. **Task 1: Rename DTO fields and endpoint paths** - already complete from Phase 03-01 deviation fix (no new commit needed)
2. **Task 2: Rewrite PhoneAuthIntegrationTest for self-managed SMS verification** - `e937a73` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

### Modified
- `src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt` — Replaced pre-seeded BCrypt hash approach with doAnswer-based code capture; added 7th rate-limiting test; 7 tests, all pass

## Decisions Made

- `doAnswer` chosen over `ArgumentCaptor` — `ArgumentCaptor.capture()` returns null in Kotlin but `SmsService.sendCode(String, String)` requires non-null Strings; null propagation causes NPE before Mockito records the matcher. `doAnswer { invocation -> capturedCode = invocation.arguments[1] as String; null }` captures during actual invocation, avoiding the null capture problem entirely.
- Task 1 verified as already-done from Phase 03-01 — the deviation fix in 03-01 updated DTOs (phoneNumber -> phone) and endpoint paths (/phone/request-otp -> /phone/request, /phone/verify-otp -> /phone/verify) and AuthController. No further changes needed; compile + grep confirm correct state.
- `captureCodeOnSend()` helper pattern: returns a `() -> String` lambda that provides the captured code after the HTTP request has been performed. This decouples stubbing setup from code retrieval.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ArgumentCaptor.capture() NPE in Kotlin — switched to doAnswer-based capture**
- **Found during:** Task 2 (first test run attempt)
- **Issue:** `ArgumentCaptor.capture()` returns null; Kotlin's non-null String parameter causes NPE at call site; `mockitoVerify(smsService).sendCode(anyString(), codeCaptor.capture())` throws NullPointerException before Mockito records the capture, and leftover matchers from the failed call corrupt the next test's verify call (InvalidUseOfMatchersException)
- **Fix:** Replaced ArgumentCaptor pattern with `doAnswer { invocation -> capturedCode = invocation.arguments[1] as String; null }` in `captureCodeOnSend()` helper
- **Files modified:** `src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt`
- **Verification:** All 7 tests pass; 23 total tests pass
- **Committed in:** e937a73 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug in Kotlin/Mockito interaction)
**Impact on plan:** Fix was essential; the spirit of the plan (capture the real generated code rather than pre-seeding) is fully preserved. `doAnswer` captures the same code that `ArgumentCaptor` would have, with identical test semantics.

## Issues Encountered

- ArgumentCaptor.capture() null issue in Kotlin is a well-known Kotlin/Mockito incompatibility. When mockito-kotlin is not on the classpath, `doAnswer` is the idiomatic workaround. The plan recommended ArgumentCaptor (assumes mockito-kotlin semantics) but doAnswer achieves the same goal: capturing the real randomly-generated code during the sendCode invocation.

## User Setup Required

None — no external service configuration required. All tests use H2 and mocked SMS delivery.

## Next Phase Readiness

- Phase 03 fully complete: self-managed SMS OTP with renamed endpoints, clean DTOs, and comprehensive integration tests
- All 23 tests pass with zero failures
- Pattern established: `captureCodeOnSend()` helper for future phone OTP test cases in plain Mockito + Kotlin environments

---
*Phase: 03-replace-twilio-verify-with-self-managed-sms-code-generation-and-verification*
*Completed: 2026-03-02*

## Self-Check: PASSED

- FOUND: src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt
- FOUND: .planning/phases/03-replace-twilio-verify-with-self-managed-sms-code-generation-and-verification/03-02-SUMMARY.md
- FOUND: e937a73 (Task 2 commit)
- FOUND: 13ca43d (docs/metadata commit)
