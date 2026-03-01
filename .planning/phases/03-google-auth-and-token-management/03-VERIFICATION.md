---
phase: 03-google-auth-and-token-management
verified: 2026-03-01T06:10:00Z
status: passed
score: 10/10 must-haves verified
re_verification: false
---

# Phase 3: Google Auth and Token Management — Verification Report

**Phase Goal:** A mobile client can exchange a valid Google ID token for JWT access and refresh tokens, rotate the refresh token, revoke it on logout, and retrieve the authenticated user's profile
**Verified:** 2026-03-01T06:10:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                                           | Status     | Evidence                                                                                                       |
|----|-----------------------------------------------------------------------------------------------------------------|------------|----------------------------------------------------------------------------------------------------------------|
| 1  | Opaque refresh tokens generated with 256-bit SecureRandom entropy, stored as SHA-256 hashes                    | VERIFIED   | `RefreshTokenService.generateRawToken()` uses `SecureRandom.nextBytes(32)` + Base64url; `hashToken()` uses `MessageDigest.getInstance("SHA-256")`; only hash persisted in DB |
| 2  | Rotating a valid refresh token returns a new token pair and marks old token revoked with usedAt + replacedByTokenHash | VERIFIED   | `RefreshTokenService.rotate()` lines 72-86: saves new token, sets `stored.revoked=true`, `stored.usedAt=Instant.now()`, `stored.replacedByTokenHash=newHash` |
| 3  | Replaying a used token outside 10-second grace window deletes all tokens for that user                          | VERIFIED   | `rotate()` lines 66-68: when `revoked=true` and outside grace window, calls `refreshTokenRepository.deleteAllByUser(stored.user)` then throws `BadCredentialsException` |
| 4  | Replaying a used token within 10-second grace window returns 409 Conflict (not 401)                            | VERIFIED   | `rotate()` lines 59-65: grace window check with `Instant.now().isBefore(usedAt.plusSeconds(10))` throws `TokenGracePeriodException`; `GlobalExceptionHandler.handleGracePeriod()` returns `HttpStatus.CONFLICT` (409) |
| 5  | BadCredentialsException from service layer returns 401 JSON response (not 500)                                 | VERIFIED   | `GlobalExceptionHandler.handleBadCredentials()` returns `HttpStatus.UNAUTHORIZED` (401) with `ErrorResponse`; placed before catch-all `Exception` handler |
| 6  | POST /api/v1/auth/google with valid Google ID token returns {accessToken, refreshToken} and creates user on first call | VERIFIED   | `AuthController.googleLogin()` delegates to `GoogleAuthService.authenticate()`; service verifies token via `googleIdTokenVerifier.verify()`, calls `userService.findOrCreateGoogleUser()`, returns `AuthResponse(accessToken, refreshToken)` |
| 7  | POST /api/v1/auth/google with same Google account on second call returns tokens without creating a duplicate user | VERIFIED   | `UserService.findOrCreateGoogleUser()` uses `userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId) ?: userRepository.save(...)` — find-before-create by (GOOGLE, sub) composite key |
| 8  | POST /api/v1/auth/refresh with valid refresh token returns new access and refresh tokens                       | VERIFIED   | `AuthController.refresh()` calls `refreshTokenService.rotate()` returning `Pair<User, String>`, then `jwtTokenService.generateAccessToken()`, returns `AuthResponse` |
| 9  | POST /api/v1/auth/revoke invalidates the refresh token and returns 204                                         | VERIFIED   | `AuthController.revoke()` calls `refreshTokenService.revoke()` which sets `stored.revoked=true`; returns `ResponseEntity.noContent().build()` (204) |
| 10 | GET /api/v1/users/me with valid Bearer token returns authenticated user's profile JSON                        | VERIFIED   | `UserController.getCurrentUser()` extracts UUID from `jwt.subject`, calls `userService.findById(userId)`, returns `ResponseEntity.ok(UserProfileResponse.from(user))`; confirmed by passing test `validToken returns 200 with user profile` asserting `$.id` and `$.roles[0]` |

**Score:** 10/10 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/kz/innlab/template/authentication/RefreshToken.kt` | RefreshToken entity with usedAt and replacedByTokenHash fields | VERIFIED | Lines 38-41: `var usedAt: Instant? = null`, `var replacedByTokenHash: String? = null` present after `revoked` field |
| `src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt` | deleteAllByUser and findByReplacedByTokenHash query methods | VERIFIED | Lines 14-17: `@Modifying @Transactional @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user") fun deleteAllByUser`; line 19: `fun findByReplacedByTokenHash` |
| `src/main/kotlin/kz/innlab/template/authentication/RefreshTokenService.kt` | Full refresh token lifecycle — create, rotate, revoke with grace window (min 60 lines) | VERIFIED | 110 lines; three public methods `createToken`, `rotate`, `revoke`; `TokenGracePeriodException` defined at top of same file |
| `src/main/kotlin/kz/innlab/template/config/GoogleAuthConfig.kt` | GoogleIdTokenVerifier singleton bean with audience validation | VERIFIED | Lines 17-20: `@Bean fun googleIdTokenVerifier()` builds with `NetHttpTransport()`, `GsonFactory.getDefaultInstance()`, `.setAudience(listOf(googleClientId))` |
| `src/main/kotlin/kz/innlab/template/authentication/GoogleAuthService.kt` | Google token verification + find-or-create user + token issuance | VERIFIED | Line 20: `googleIdTokenVerifier.verify(idTokenString) ?: throw BadCredentialsException(...)`; delegates to `userService.findOrCreateGoogleUser`, `jwtTokenService.generateAccessToken`, `refreshTokenService.createToken` |
| `src/main/kotlin/kz/innlab/template/authentication/AuthController.kt` | POST /auth/google, /auth/refresh, /auth/revoke endpoints | VERIFIED | Lines 21-38: three `@PostMapping` methods at `/google`, `/refresh`, `/revoke` under `@RequestMapping("/api/v1/auth")` |
| `src/main/kotlin/kz/innlab/template/user/UserService.kt` | findOrCreateGoogleUser and findById methods | VERIFIED | Lines 14-30: both methods present with `@Transactional` annotations; `findById` throws `AccessDeniedException` on miss |
| `src/main/kotlin/kz/innlab/template/user/UserController.kt` | Real GET /users/me with database lookup | VERIFIED | Lines 18-22: `@GetMapping("/me")` injects `@AuthenticationPrincipal jwt: Jwt`, calls `userService.findById(userId)`, returns `UserProfileResponse.from(user)` |
| `src/main/kotlin/kz/innlab/template/user/UserProfileResponse.kt` | User profile DTO with from(User) factory | VERIFIED | Data class with 7 fields (`id`, `email`, `name`, `picture`, `provider`, `roles`, `createdAt`); companion object `fun from(user: User)` factory |
| `pom.xml` | google-api-client 2.9.0 dependency | VERIFIED | Lines 88-92: `<artifactId>google-api-client</artifactId><version>2.9.0</version>` |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AuthController./google` | `GoogleAuthService.authenticate` | Delegates Google token handling | WIRED | `AuthController.kt` line 23: `googleAuthService.authenticate(request.idToken)` |
| `GoogleAuthService` | `GoogleIdTokenVerifier` | Verifies Google ID token | WIRED | `GoogleAuthService.kt` line 20: `googleIdTokenVerifier.verify(idTokenString)` |
| `GoogleAuthService` | `UserService.findOrCreateGoogleUser` | Find-or-create user from Google claims | WIRED | `GoogleAuthService.kt` line 30: `userService.findOrCreateGoogleUser(providerId, email, name, picture)` |
| `GoogleAuthService` | `RefreshTokenService.createToken` | Issues refresh token for authenticated user | WIRED | `GoogleAuthService.kt` line 33: `refreshTokenService.createToken(user)` |
| `AuthController./refresh` | `RefreshTokenService.rotate` | Rotates refresh token | WIRED | `AuthController.kt` line 29: `refreshTokenService.rotate(request.refreshToken)` |
| `UserController./me` | `UserService.findById` | Looks up user by JWT subject UUID | WIRED | `UserController.kt` line 20: `userService.findById(userId)` |
| `RefreshTokenService.rotate()` | `RefreshTokenRepository` | findByTokenHash, save, deleteAllByUser | WIRED | Lines 48, 67, 74, 84: all three repository methods called in rotation path |
| `RefreshTokenService` | `GlobalExceptionHandler` | BadCredentialsException -> 401 JSON | WIRED | `GlobalExceptionHandler.kt` lines 31-35: `@ExceptionHandler(BadCredentialsException::class)` handler present before catch-all |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| AUTH-01 | 03-02 | Client can send Google ID token to POST /api/v1/auth/google and receive JWT access + refresh tokens | SATISFIED | `AuthController` + `GoogleAuthService` implement full flow; `AuthResponse(accessToken, refreshToken)` returned |
| AUTH-03 | 03-02 | Backend verifies Google ID token directly with Google (via google-api-client) including `aud` claim validation | SATISFIED | `GoogleAuthConfig` builds `GoogleIdTokenVerifier` with `.setAudience(listOf(googleClientId))`; `google-api-client 2.9.0` in pom.xml |
| TOKN-02 | 03-01 | Refresh tokens are opaque, stored in DB as SHA-256 hashes, with single-use rotation | SATISFIED | `createToken()` stores only hash; `rotate()` marks old token revoked; raw token never persisted |
| TOKN-03 | 03-01 | Reuse detection revokes all refresh tokens for the user when a used token is replayed | SATISFIED | `rotate()` calls `refreshTokenRepository.deleteAllByUser(stored.user)` on replay outside grace window |
| TOKN-04 | 03-01 | 10-second grace window on refresh token rotation to handle concurrent mobile retries | SATISFIED | `rotate()` checks `Instant.now().isBefore(usedAt.plusSeconds(graceWindowSeconds))` where `graceWindowSeconds = 10L`; throws `TokenGracePeriodException` mapped to 409 |
| TOKN-05 | 03-02 | POST /api/v1/auth/refresh rotates refresh token and returns new access + refresh tokens | SATISFIED | `AuthController.refresh()` fully implemented; returns `AuthResponse` with new `accessToken` and `refreshToken` |
| TOKN-06 | 03-02 | POST /api/v1/auth/revoke invalidates the refresh token (logout) | SATISFIED | `AuthController.revoke()` calls `refreshTokenService.revoke()`; returns 204 No Content |
| USER-03 | 03-02 | GET /api/v1/users/me returns the current authenticated user's profile | SATISFIED | `UserController.getCurrentUser()` returns `UserProfileResponse` from DB; test `validToken returns 200 with user profile` passes |
| USER-04 | 03-02 | New users are created automatically on first successful authentication (find-or-create) | SATISFIED | `UserService.findOrCreateGoogleUser()` creates user on first call with `userRepository.save(...)` when not found by `(GOOGLE, providerId)` |

**All 9 required IDs accounted for. No orphaned requirements.**

REQUIREMENTS.md traceability table marks AUTH-01, AUTH-03, TOKN-02 through TOKN-06, USER-03, USER-04 as Phase 3 / Complete — consistent with implementation.

---

### Anti-Patterns Found

No anti-patterns detected across all phase 3 files.

- No TODO/FIXME/HACK/PLACEHOLDER comments in any modified file
- No stub implementations (`return null`, empty bodies, `console.log`-only handlers)
- No empty handlers — all three controller endpoints fully delegate to services
- `UserController` fully replaces the prior JWT-claims stub with a real DB-backed implementation

---

### Human Verification Required

#### 1. End-to-end Google login with real token

**Test:** Obtain a real Google ID token from a test Google account; POST it to `POST /api/v1/auth/google` on a running instance (with `GOOGLE_CLIENT_ID` set)
**Expected:** HTTP 200 with `{"accessToken": "...", "refreshToken": "..."}` where the access token is a valid RS256 JWT and the refresh token is a 43-character Base64url string
**Why human:** GoogleIdTokenVerifier hits Google's public endpoint for signature verification — cannot mock this in a unit test without a real credential; requires a live Google Cloud OAuth2 Client ID configured in the environment

#### 2. Refresh token rotation flow (happy path)

**Test:** After a successful Google login, POST the received refresh token to `POST /api/v1/auth/refresh`; then POST the new refresh token to `/refresh` again; finally attempt to replay the first refresh token against `/refresh`
**Expected:** First rotation returns new token pair; second rotation works on the new token; replay of the first token returns 401 (reuse detected, all tokens deleted)
**Why human:** Requires a live token pair from step 1; the 401-on-reuse path involves `deleteAllByUser` which needs a real DB to verify all tokens are gone

#### 3. Grace window concurrent retry

**Test:** Perform a token rotation; within 10 seconds, POST the same (now-rotated) refresh token to `/refresh` again
**Expected:** HTTP 409 Conflict with `{"error": "Conflict", "message": "Token already rotated, retry with new token", "status": 409}`
**Why human:** Timing-sensitive; requires coordinating two requests within the 10-second window; cannot be reliably tested in a unit test without mocking `Instant.now()`

---

### Gaps Summary

No gaps. All 10 truths verified, all 9 requirements satisfied, all key links wired, build compiles clean, and all 5 tests pass (`BUILD SUCCESS` — 4 SecurityIntegrationTest + 1 TemplateApplicationTests).

The three human verification items are noted for completeness but do not block the phase goal — they require a live environment with a real Google Cloud OAuth2 Client ID, which is an expected operational prerequisite documented in `.env.example`.

---

_Verified: 2026-03-01T06:10:00Z_
_Verifier: Claude (gsd-verifier)_
