---
status: complete
phase: 05-add-account-management
source: 05-01-SUMMARY.md, 05-02-SUMMARY.md, 05-03-SUMMARY.md
started: 2026-03-03T04:10:00Z
updated: 2026-03-03T04:20:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Forgot Password — Request Code
expected: POST /api/v1/auth/forgot-password with registered email returns 202 Accepted with verificationId. Code printed to console.
result: pass

### 2. Forgot Password — Anti-Enumeration
expected: POST /api/v1/auth/forgot-password with a non-existent email still returns 202 Accepted (verificationId may be null). No error or 404.
result: pass

### 3. Forgot Password — Reset Password
expected: POST /api/v1/auth/reset-password with verificationId, email, code, and newPassword returns 200 OK. User can now login with new password. All existing refresh tokens revoked.
result: pass

### 4. Change Password
expected: POST /api/v1/users/me/change-password with Bearer token, currentPassword, and newPassword returns 200 OK. Old password no longer works. All refresh tokens revoked.
result: pass

### 5. Change Email — Request Code
expected: POST /api/v1/users/me/change-email/request with Bearer token and {"newEmail":"new@test.com"} returns 200 with verificationId. Code printed to console.
result: pass

### 6. Change Email — Verify
expected: POST /api/v1/users/me/change-email/verify with Bearer token, verificationId, and code returns 200 OK. User's email is now updated.
result: pass

### 7. Change Phone — Request Code
expected: POST /api/v1/users/me/change-phone/request with Bearer token and {"phone":"+77001234567"} returns 200 with verificationId. Code printed to console (ConsoleSmsService).
result: pass

### 8. Change Phone — Verify
expected: POST /api/v1/users/me/change-phone/verify with Bearer token, verificationId, phone, and code returns 200 OK. User's phone is now updated.
result: pass

### 9. Rate Limiting
expected: Sending two forgot-password or change-email requests within 60 seconds for the same identifier returns 409 Conflict on the second request.
result: pass

### 10. All Tests Pass
expected: ./mvnw clean test runs all 37 tests with 0 failures.
result: pass

## Summary

total: 10
passed: 10
issues: 0
pending: 0
skipped: 0

## Gaps

[none]
