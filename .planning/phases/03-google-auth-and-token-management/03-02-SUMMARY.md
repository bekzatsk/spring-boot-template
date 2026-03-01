---
phase: 03-google-auth-and-token-management
plan: 02
subsystem: auth
tags: [google-oauth, jwt, refresh-token, kotlin, spring-boot, google-api-client]

# Dependency graph
requires:
  - phase: 03-01
    provides: RefreshTokenService with rotation, grace window, reuse detection
  - phase: 02
    provides: SecurityConfig, JwtTokenService, GlobalExceptionHandler, User entity, UserRepository

provides:
  - GoogleIdTokenVerifier bean with audience validation (GoogleAuthConfig)
  - Google ID token verification + find-or-create user + token issuance (GoogleAuthService)
  - POST /api/v1/auth/google — authenticates mobile client with Google ID token, returns JWT pair
  - POST /api/v1/auth/refresh — rotates refresh token, returns new JWT pair
  - POST /api/v1/auth/revoke — invalidates refresh token, returns 204
  - GET /api/v1/users/me — real database-backed user profile (replaces JWT-claims stub)
  - UserService with findOrCreateGoogleUser and findById
  - UserProfileResponse DTO with from(User) factory
  - AuthResponse and RefreshRequest DTOs

affects: [04-apple-auth, 05-feature-development]

# Tech tracking
tech-stack:
  added: [google-api-client 2.9.0]
  patterns:
    - GoogleIdTokenVerifier singleton bean with audience validation for Google ID token verification
    - Null-return verification pattern (verify() returns null on failure, not exception)
    - find-or-create user by (GOOGLE, sub) composite key to prevent duplicates
    - @AuthenticationPrincipal Jwt injection in UserController for typed JWT access

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/config/GoogleAuthConfig.kt
    - src/main/kotlin/kz/innlab/template/authentication/GoogleAuthService.kt
    - src/main/kotlin/kz/innlab/template/authentication/AuthController.kt
    - src/main/kotlin/kz/innlab/template/user/UserService.kt
    - src/main/kotlin/kz/innlab/template/user/UserProfileResponse.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/AuthResponse.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/RefreshRequest.kt
  modified:
    - pom.xml
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - .env.example
    - src/main/kotlin/kz/innlab/template/user/UserController.kt
    - src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt

key-decisions:
  - "GoogleIdTokenVerifier.verify() returns null on invalid token — checked explicitly, throws BadCredentialsException caught by GlobalExceptionHandler"
  - "test/resources/application.yaml must include app.auth config — test YAML overrides main YAML and was missing the auth section"
  - "SecurityIntegrationTest /users/me test creates a real User in H2 DB — required because UserController now does a real DB lookup"

patterns-established:
  - "GoogleAuth flow: verify -> extract claims -> findOrCreateGoogleUser -> generateAccessToken + createRefreshToken -> AuthResponse"
  - "Controllers delegate to services; no try/catch in controllers — GlobalExceptionHandler handles all exceptions"
  - "@AuthenticationPrincipal Jwt used over Authentication cast — cleaner typed injection"

requirements-completed: [AUTH-01, AUTH-03, TOKN-05, TOKN-06, USER-03, USER-04]

# Metrics
duration: 5min
completed: 2026-03-01
---

# Phase 03 Plan 02: Google Auth and Token Management — Wiring Summary

**Google OAuth2 login wired end-to-end: GoogleIdTokenVerifier validates tokens, UserService finds-or-creates users by (GOOGLE, sub), AuthController exposes /google, /refresh, /revoke endpoints, and /users/me returns full DB-backed UserProfileResponse**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-01T00:54:48Z
- **Completed:** 2026-03-01T00:59:48Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- Added `google-api-client 2.9.0` dependency and `GoogleAuthConfig` bean producing `GoogleIdTokenVerifier` with audience validation
- Created `GoogleAuthService` implementing full Google auth flow: verify token -> extract claims -> find-or-create user -> issue JWT + refresh token pair
- Created `AuthController` exposing three stateless endpoints: POST /google (login), POST /refresh (rotate), POST /revoke (invalidate)
- Replaced stub `UserController` (returning JWT claims map) with real database-backed implementation using `UserService.findById` and `UserProfileResponse.from(user)`
- All 5 tests pass including updated `validToken returns 200 with user profile` test that creates a real H2 user

## Task Commits

Each task was committed atomically:

1. **Task 1: Add google-api-client, GoogleAuthConfig, UserService, GoogleAuthService, and DTOs** - `8f15cd9` (feat)
2. **Task 2: Create AuthController endpoints and replace stub UserController with real /users/me** - `ede23af` (feat)

**Plan metadata:** (docs commit — below)

## Files Created/Modified
- `src/main/kotlin/kz/innlab/template/config/GoogleAuthConfig.kt` - GoogleIdTokenVerifier singleton bean with audience validation
- `src/main/kotlin/kz/innlab/template/authentication/GoogleAuthService.kt` - Google token verification + find-or-create user + token issuance
- `src/main/kotlin/kz/innlab/template/authentication/AuthController.kt` - POST /google, /refresh, /revoke endpoints
- `src/main/kotlin/kz/innlab/template/user/UserService.kt` - findOrCreateGoogleUser and findById methods
- `src/main/kotlin/kz/innlab/template/user/UserProfileResponse.kt` - User profile DTO with from(User) factory
- `src/main/kotlin/kz/innlab/template/authentication/dto/AuthResponse.kt` - Response DTO with accessToken and refreshToken
- `src/main/kotlin/kz/innlab/template/authentication/dto/RefreshRequest.kt` - Validated request DTO for refresh/revoke
- `pom.xml` - Added google-api-client 2.9.0
- `src/main/resources/application.yaml` - Added app.auth.google.client-id and app.auth.refresh-token.expiry-days
- `src/test/resources/application.yaml` - Added app.auth config for test context initialization
- `.env.example` - Documented GOOGLE_CLIENT_ID and REFRESH_TOKEN_EXPIRY_DAYS
- `src/main/kotlin/kz/innlab/template/user/UserController.kt` - Replaced stub with real UserService-backed implementation
- `src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt` - Updated assertions ($.sub -> $.id), creates real DB user

## Decisions Made
- `GoogleIdTokenVerifier.verify()` returns null on failure (not exception) — checked explicitly and converted to `BadCredentialsException` caught by `GlobalExceptionHandler`
- Test `application.yaml` must duplicate `app.auth` config because the test YAML overrides the main YAML; the `${GOOGLE_CLIENT_ID:test-client-id}` default in main YAML is not available in test context without this
- `validToken returns 200 with user profile` test now creates a real `User` in H2 before calling the endpoint — necessary because `UserController` performs a real DB lookup and would throw `AccessDeniedException` for a random UUID

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added app.auth config to test application.yaml**
- **Found during:** Task 2 (test run)
- **Issue:** Test context failed to initialize because `app.auth.google.client-id` placeholder was unresolvable; test `application.yaml` overrides the main YAML but didn't have the `auth` section
- **Fix:** Added `app.auth.google.client-id: test-client-id` and `app.auth.refresh-token.expiry-days: 30` to `src/test/resources/application.yaml`
- **Files modified:** src/test/resources/application.yaml
- **Verification:** All 5 tests pass after fix
- **Committed in:** ede23af (Task 2 commit)

**2. [Rule 1 - Bug] Updated SecurityIntegrationTest to create real DB user for /users/me**
- **Found during:** Task 2 (test analysis)
- **Issue:** The test generated a random UUID for the JWT subject, but `UserController` now calls `userService.findById(userId)` which would throw `AccessDeniedException` for a UUID with no matching DB record
- **Fix:** Test now saves a real `User` to H2 via `userRepository`, uses its actual `id` for the JWT subject
- **Files modified:** src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt
- **Verification:** `validToken returns 200 with user profile` passes, returning `$.id` and `$.roles[0]`
- **Committed in:** ede23af (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes necessary for tests to pass after the UserController replacement. No scope creep.

## Issues Encountered
- Spring Boot test context uses the `default` profile and loads `src/test/resources/application.yaml` which overrides the main YAML — the `app.auth` config section must be present in the test YAML explicitly (discovered and fixed automatically)

## User Setup Required

**External services require manual configuration before the `/auth/google` endpoint will work with real tokens:**

- `GOOGLE_CLIENT_ID` — Get from Google Cloud Console -> APIs & Services -> Credentials -> OAuth 2.0 Client IDs
- The app runs without this key in dev/test (defaults to `test-client-id`), but real Google ID tokens will be rejected

See `.env.example` for all environment variables.

## Next Phase Readiness
- Complete Google auth flow is working: POST /auth/google, /auth/refresh, /auth/revoke, GET /users/me
- Phase 4 (Apple Auth) can follow the same pattern: AppleJwksConfig bean, AppleAuthService, add /auth/apple endpoint to AuthController
- Existing SecurityConfig already permits `/api/v1/auth/**` — no security config changes needed for Apple auth

---
*Phase: 03-google-auth-and-token-management*
*Completed: 2026-03-01*
