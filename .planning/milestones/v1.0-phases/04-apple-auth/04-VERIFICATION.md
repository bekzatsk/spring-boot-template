---
phase: 04-apple-auth
verified: 2026-03-01T06:52:00Z
status: passed
score: 4/4 must-haves verified
re_verification: false
---

# Phase 4: Apple Auth Verification Report

**Phase Goal:** A mobile client can exchange a valid Apple ID token for JWT access and refresh tokens, with first-login user data persisted atomically and private relay emails accepted
**Verified:** 2026-03-01T06:52:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                                           | Status     | Evidence                                                                                  |
|----|----------------------------------------------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------------|
| 1  | POST /api/v1/auth/apple with a valid Apple ID token on first sign-in creates the user with name and email persisted and returns accessToken + refreshToken | VERIFIED   | AuthController.appleLogin() wired to AppleAuthService.authenticate(); integration test `first sign-in with name creates user and returns tokens` asserts email, name="Jane Doe", provider=APPLE in DB; all pass |
| 2  | POST /api/v1/auth/apple on a subsequent sign-in (no email in token) finds the existing user by (APPLE, sub) and returns tokens without error | VERIFIED   | UserService.findOrCreateAppleUser checks existing user first; integration test `subsequent sign-in without email finds existing user and returns tokens` confirms 200 + no duplicate; passes |
| 3  | POST /api/v1/auth/apple with a privaterelay.appleid.com email creates the user successfully — not a validation error | VERIFIED   | No @Email annotation on AppleAuthRequest; no email validation anywhere in creation path; integration test `private relay email accepted on first sign-in` stores user@privaterelay.appleid.com and asserts 200; passes |
| 4  | POST /api/v1/auth/apple with an invalid/expired/wrong-audience token returns 401 JSON error                    | VERIFIED   | AppleAuthService wraps JwtException as BadCredentialsException; GlobalExceptionHandler returns 401 JSON; integration test `invalid token returns 401` asserts status 401 + $.error exists + $.status=401; passes |

**Score:** 4/4 truths verified

---

## Required Artifacts

| Artifact                                                                              | Expected                                                    | Status   | Details                                                                                                              |
|---------------------------------------------------------------------------------------|-------------------------------------------------------------|----------|----------------------------------------------------------------------------------------------------------------------|
| `src/main/kotlin/kz/innlab/template/config/AppleAuthConfig.kt`                        | NimbusJwtDecoder bean for Apple JWKS with iss/aud/exp validators | VERIFIED | File exists (48 lines). Contains `@Bean(name = ["appleJwtDecoder"])`, `NimbusJwtDecoder.withJwkSetUri(APPLE_JWKS_URI)`, `JwtTimestampValidator`, `JwtIssuerValidator("https://appleid.apple.com")`, `JwtClaimValidator` for aud. Substantive — no stubs.                                          |
| `src/main/kotlin/kz/innlab/template/authentication/dto/AppleAuthRequest.kt`           | Request DTO with idToken + optional givenName/familyName    | VERIFIED | File exists (13 lines). `data class AppleAuthRequest` with `@NotBlank idToken`, nullable `givenName` and `familyName`. No @Email on any field. Substantive.                                                              |
| `src/main/kotlin/kz/innlab/template/authentication/AppleAuthService.kt`               | Apple token verification + find-or-create user + token issuance | VERIFIED | File exists (55 lines). `@Service` class with `@Qualifier("appleJwtDecoder")`, `@Transactional authenticate()` that decodes JWT, extracts sub + email, builds fullName, calls userService.findOrCreateAppleUser, generates access + refresh tokens. Substantive. Wired via AuthController.                  |
| `src/main/kotlin/kz/innlab/template/user/UserService.kt`                              | findOrCreateAppleUser with nullable email handling           | VERIFIED | File exists (63 lines). `findOrCreateAppleUser(providerId, email: String?, name: String?)` looks up by (APPLE, sub) first; returns existing user immediately; requires email only for new users; saves with `@Transactional`. Substantive.                                                              |
| `src/main/kotlin/kz/innlab/template/authentication/AuthController.kt`                 | POST /apple endpoint                                        | VERIFIED | File exists (47 lines). `appleLogin(@Valid @RequestBody request: AppleAuthRequest)` at `@PostMapping("/apple")` delegates to `appleAuthService.authenticate(request)`. Wired — called from real HTTP path under `/api/v1/auth`.                                                                    |
| `src/test/kotlin/kz/innlab/template/AppleAuthIntegrationTest.kt`                     | 4 integration tests with @MockitoBean                       | VERIFIED | File exists (166 lines). 4 `@Test` methods: first sign-in, subsequent sign-in, private relay, invalid token. Uses `@MockitoBean(name = "appleJwtDecoder")` from correct Spring 7.x package. All 4 pass in CI run (9 total tests, 0 failures). |

---

## Key Link Verification

| From                                  | To                                        | Via                                          | Status   | Details                                                                                    |
|---------------------------------------|-------------------------------------------|----------------------------------------------|----------|--------------------------------------------------------------------------------------------|
| `AuthController.kt`                   | `AppleAuthService.authenticate()`         | Constructor injection + appleLogin() call    | VERIFIED | Line 31: `val response = appleAuthService.authenticate(request)` confirmed by grep          |
| `AppleAuthService.kt`                 | `appleJwtDecoder.decode()`                | @Qualifier("appleJwtDecoder") injection      | VERIFIED | Line 25: `appleJwtDecoder.decode(request.idToken)` confirmed by grep                       |
| `AppleAuthService.kt`                 | `userService.findOrCreateAppleUser()`     | Constructor injection                        | VERIFIED | Line 44: `val user = userService.findOrCreateAppleUser(...)` confirmed by grep              |
| `UserService.kt`                      | `userRepository.findByProviderAndProviderId(AuthProvider.APPLE, providerId)` | JPA repository call | VERIFIED | Line 36: `userRepository.findByProviderAndProviderId(AuthProvider.APPLE, providerId)` confirmed by grep |

---

## Requirements Coverage

| Requirement | Description                                                                     | Status    | Evidence                                                                                                                                                     |
|-------------|---------------------------------------------------------------------------------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AUTH-02     | Client can send Apple ID token to POST /api/v1/auth/apple and receive JWT access + refresh tokens | SATISFIED | AuthController `/apple` endpoint exists and wired; AppleAuthService returns `AuthResponse(accessToken, refreshToken)`; integration test asserts both fields in 200 response |
| AUTH-04     | Backend verifies Apple ID token via Apple's JWKS endpoint with iss, aud, exp claim validation | SATISFIED | AppleAuthConfig builds NimbusJwtDecoder with JWKS URI `https://appleid.apple.com/auth/keys`, DelegatingOAuth2TokenValidator with JwtTimestampValidator (exp), JwtIssuerValidator (iss), JwtClaimValidator (aud) |
| AUTH-05     | Apple first-sign-in user data (name, email) is persisted atomically — never lost | SATISFIED | `UserService.findOrCreateAppleUser` is `@Transactional`; email from JWT + name from request body both saved in single `userRepository.save()` call; integration test asserts name="Jane Doe" in DB |
| AUTH-06     | Apple private relay emails (*@privaterelay.appleid.com) are accepted without failure | SATISFIED | No `@Email` annotation on any field in AppleAuthRequest; no email format validation in UserService create path; integration test stores `user@privaterelay.appleid.com` and asserts 200 |

All 4 required requirement IDs (AUTH-02, AUTH-04, AUTH-05, AUTH-06) are satisfied. No orphaned requirements from REQUIREMENTS.md for Phase 4 — REQUIREMENTS.md traceability table maps exactly AUTH-02, AUTH-04, AUTH-05, AUTH-06 to Phase 4.

---

## Config and Infrastructure Verification

| File                                    | Expected                                   | Status   | Details                                                                                     |
|-----------------------------------------|--------------------------------------------|----------|---------------------------------------------------------------------------------------------|
| `src/main/resources/application.yaml`   | app.auth.apple.bundle-id with env var       | VERIFIED | Line 24: `bundle-id: ${APPLE_BUNDLE_ID:com.example.app}` present under `app.auth.apple`    |
| `src/test/resources/application.yaml`  | app.auth.apple.bundle-id: test-bundle-id   | VERIFIED | Line 22: `bundle-id: test-bundle-id` present under `app.auth.apple`                        |
| `.env.example`                          | APPLE_BUNDLE_ID documented                 | VERIFIED | Lines 21-23: section with iOS bundle ID vs Services ID note and `APPLE_BUNDLE_ID=com.example.myapp` |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| —    | —    | None    | —        | No TODOs, FIXMEs, placeholder returns, or empty handlers found in any Phase 4 source file |

Grep scans across all modified `.kt` files in `src/main/kotlin/kz/innlab/template` found zero matches for: `TODO`, `FIXME`, `XXX`, `HACK`, `PLACEHOLDER`, `return null`, `return {}`, `return []`.

---

## Test Run Results

```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0  — AppleAuthIntegrationTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0  — SecurityIntegrationTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0  — TemplateApplicationTests
Total: 9, Failures: 0, Errors: 0
BUILD SUCCESS
```

No regressions in existing tests.

---

## Human Verification Required

### 1. Live Apple JWKS Validation

**Test:** Send a real (recently issued) Apple ID token to POST /api/v1/auth/apple against a running server with a valid APPLE_BUNDLE_ID
**Expected:** 200 response with accessToken and refreshToken
**Why human:** Tests mock the JwtDecoder — real HTTPS call to `https://appleid.apple.com/auth/keys` and cryptographic RS256 signature verification cannot be exercised in automated tests without a live Apple account and iOS device

### 2. Expired Apple Token Rejection at Real JWKS Level

**Test:** Send an expired Apple ID token (not mocked) to POST /api/v1/auth/apple
**Expected:** 401 JSON response with error message containing "expired" or similar
**Why human:** Automated tests mock JwtException; actual JwtTimestampValidator behavior against a real token requires a token past its `exp` claim

### 3. Wrong Audience Rejection at Real JWKS Level

**Test:** Send a valid Apple ID token with a different bundle ID configured as APPLE_BUNDLE_ID
**Expected:** 401 JSON response
**Why human:** Audience validator requires a live Apple token and real JWKS resolution to confirm the aud check fires correctly against real data

---

## Gaps Summary

No gaps. All 4 observable truths are verified. All 5 source artifacts exist, contain substantive implementation, and are correctly wired. All 4 key links are confirmed by grep. All 4 requirement IDs (AUTH-02, AUTH-04, AUTH-05, AUTH-06) are satisfied. 9 tests pass with 0 failures. No anti-patterns found. Config files correctly updated.

The three human verification items are live-environment tests requiring a real Apple Developer account and iOS device — they do not block phase completion because the mocked integration tests cover the same code paths at the unit level, and the validator configuration is verified to be correct by code inspection.

---

**Commits verified:**
- `84c5f64` — feat(04-01): add AppleAuthConfig, AppleAuthRequest DTO, AppleAuthService, and extend UserService
- `2aeee2f` — feat(04-01): wire AuthController /apple endpoint, update configs, and add integration tests
- `aa468d1` — docs(04-01): complete Apple auth plan — SUMMARY, STATE, ROADMAP, REQUIREMENTS updated

---

_Verified: 2026-03-01T06:52:00Z_
_Verifier: Claude (gsd-verifier)_
