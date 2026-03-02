---
phase: quick
plan: 5
subsystem: authentication/phone-otp
tags: [security, phone-otp, brute-force-prevention, sms-verification]
dependency_graph:
  requires: []
  provides: [verificationId in /phone/request response, UUID-based OTP lookup]
  affects: [SmsVerificationService, PhoneOtpService, AuthController, PhoneVerifyRequest]
tech_stack:
  added: []
  patterns: [UUID-bound OTP verification, findById over findActiveByPhone]
key_files:
  created: []
  modified:
    - src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/PhoneOtpService.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/PhoneVerifyRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt
    - src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt
decisions:
  - "findById(verificationId) replaces findActiveByPhone — binds OTP lookup to specific record UUID"
  - "Phone mismatch check after findById prevents UUID from one session being reused for a different phone"
  - "POST /phone/request returns 200 with verificationId JSON; 204 no-content retired"
metrics:
  duration: 6 min
  completed: 2026-03-02
  tasks_completed: 2
  files_modified: 5
---

# Quick Task 5: Add verificationId to Phone OTP Flow — Summary

**One-liner:** OTP verification now bound to a specific SmsVerification UUID returned from /phone/request, preventing brute-force attacks that exploited phone-only lookups.

## What Was Built

POST /api/v1/auth/phone/request now returns HTTP 200 with `{"verificationId": "uuid"}`. The client must include this UUID in the subsequent POST /api/v1/auth/phone/verify body. The service layer uses `findById(verificationId)` instead of `findActiveByPhone()`, which means an attacker must know both the phone number AND the specific 128-bit record UUID to attempt a brute-force attack.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Service layer + DTO: sendCode returns UUID, verifyCode takes (UUID, phone, code), PhoneVerifyRequest adds verificationId field, controller updated | 835d679 | SmsVerificationService.kt, PhoneOtpService.kt, PhoneVerifyRequest.kt, AuthController.kt |
| 2 | Integration tests updated for new flow: 200+JSON from /phone/request, verificationId in all /phone/verify calls | 673106f | PhoneAuthIntegrationTest.kt |

## Changes

### SmsVerificationService

- `sendCode()` return type changed from `Unit` to `UUID` — returns `saved.id!!` after `smsVerificationRepository.save()`
- `verifyCode()` signature changed to `(verificationId: UUID, phoneE164: String, code: String): Boolean`
- Lookup changed from `findActiveByPhone(phoneE164, now)` to `findById(verificationId).orElse(null)`
- Phone mismatch guard added: `if (record.phone != phoneE164) return false`
- Existing expiry, used, and max-attempts checks preserved
- `findActiveByPhone` query kept in repository (not removed — may be useful for cleanup jobs)

### PhoneOtpService

- `sendOtp()` return type changed from `Unit` to `UUID`; returns result of `smsVerificationService.sendCode()`
- `verifyOtp()` signature changed to `(verificationId: UUID, rawPhone: String, code: String): AuthResponse`

### PhoneVerifyRequest

- Added `@field:NotNull val verificationId: UUID` field
- Imports added: `jakarta.validation.constraints.NotNull`, `java.util.UUID`

### AuthController

- `requestPhoneOtp()` return type changed from `ResponseEntity<Void>` to `ResponseEntity<Map<String, Any>>`
- Returns `ResponseEntity.ok(mapOf("verificationId" to verificationId))` instead of `ResponseEntity.noContent().build()`
- `verifyPhoneOtp()` passes `request.verificationId` to `phoneOtpService.verifyOtp()`

### PhoneAuthIntegrationTest

- `request OTP success` renamed and now expects `status().isOk` + `jsonPath("$.verificationId").exists()`
- Added `extractVerificationId(MvcResult): String` private helper using Jackson 3.x `JsonMapper`
- All tests that call `/phone/verify` now capture verificationId from `/phone/request` and include it in the body
- `verify OTP with invalid phone format` adds `UUID.randomUUID()` as dummy verificationId
- Rate-limit test first request expectation changed from `isNoContent` to `isOk`

## Verification

Full test suite: 23 tests, 0 failures, 0 errors.

```
[INFO] Tests run: 7, Failures: 0, Errors: 0 -- PhoneAuthIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0 -- AppleAuthIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0 -- SecurityIntegrationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0 -- LocalAuthIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0 -- TemplateApplicationTests
[INFO] Tests run: 23, Failures: 0, Errors: 0 -- BUILD SUCCESS
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Controller updated in Task 1 (not Task 2)**
- **Found during:** Task 1
- **Issue:** AuthController.verifyPhoneOtp() called `phoneOtpService.verifyOtp(request.phone, request.code)` with old signature; this caused a compile error after PhoneOtpService signature was changed
- **Fix:** Controller changes applied during Task 1 compilation step rather than waiting for Task 2
- **Files modified:** AuthController.kt
- **Commit:** 835d679 (included with Task 1)

## Self-Check: PASSED

All 6 files present. Both commits (835d679, 673106f) verified in git log. 23 tests pass.
