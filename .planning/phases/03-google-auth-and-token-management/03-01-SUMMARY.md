---
phase: 03-google-auth-and-token-management
plan: 01
subsystem: auth
tags: [refresh-token, sha256, secure-random, opaque-token, token-rotation, reuse-detection, grace-window, jpa, spring-security]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: RefreshToken entity and RefreshTokenRepository base, User entity
  - phase: 02-security-wiring
    provides: GlobalExceptionHandler, ErrorResponse, SecurityFilterChain

provides:
  - RefreshTokenService with create/rotate/revoke lifecycle (opaque tokens, SHA-256 hash storage)
  - 10-second grace window for concurrent mobile retry detection (409 Conflict response)
  - Reuse detection deleting all user tokens on replay outside grace window (401 response)
  - TokenGracePeriodException for grace window signaling
  - BadCredentialsException -> 401 JSON handler in GlobalExceptionHandler
  - TokenGracePeriodException -> 409 JSON handler in GlobalExceptionHandler
  - deleteAllByUser JPQL bulk-delete repository method
  - findByReplacedByTokenHash derived query repository method

affects:
  - 03-02 (AuthController uses RefreshTokenService.rotate and revoke)
  - 03-03 (GoogleAuthService uses RefreshTokenService.createToken)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Opaque refresh tokens: 32-byte SecureRandom + Base64url, stored as SHA-256 hash
    - Grace window state machine: usedAt + replacedByTokenHash fields track rotation state
    - JPQL @Modifying bulk DELETE avoids N+1 on family revocation
    - TokenGracePeriodException as domain exception for 409 signaling (not a security error)
    - Exception handlers ordered before catch-all in GlobalExceptionHandler

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/authentication/RefreshTokenService.kt
  modified:
    - src/main/kotlin/kz/innlab/template/authentication/RefreshToken.kt
    - src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt
    - src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt

key-decisions:
  - "Grace window replay returns 409 Conflict (not the replacement raw token) — raw token not stored per TOKN-02, so 409 + Retry-After is the correct mobile UX signal"
  - "TokenGracePeriodException defined in same file as RefreshTokenService for simplicity (no separate file needed)"
  - "deleteAllByUser uses JPQL DELETE (not derived deleteBy) to avoid N+1 entity loading on family revocation"
  - "revoke() does NOT set usedAt — logout is distinguishable from rotation in state machine"

patterns-established:
  - "Opaque token pattern: generate raw -> return to client -> store hash only"
  - "State machine: Active (revoked=false, usedAt=null) -> Used (revoked=true, usedAt set, replacedByTokenHash set) -> Logout-revoked (revoked=true, usedAt=null)"

requirements-completed: [TOKN-02, TOKN-03, TOKN-04]

# Metrics
duration: 3min
completed: 2026-03-01
---

# Phase 3 Plan 01: Refresh Token Lifecycle Summary

**Opaque refresh token service with SHA-256 hash storage, single-use rotation, 10-second grace window for concurrent mobile retries, and reuse-detection that revokes all user tokens on replay**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-01T00:48:25Z
- **Completed:** 2026-03-01T00:51:44Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- RefreshToken entity extended with `usedAt` and `replacedByTokenHash` fields enabling full grace window state machine
- RefreshTokenService implements create/rotate/revoke with 256-bit SecureRandom entropy, SHA-256 hash storage, 10-second grace window (409 Conflict), and reuse detection (deleteAllByUser + 401)
- GlobalExceptionHandler updated with BadCredentialsException -> 401 and TokenGracePeriodException -> 409 handlers before catch-all

## Task Commits

Each task was committed atomically:

1. **Task 1: Add grace window fields to RefreshToken entity and new repository methods** - `f2ac8e5` (feat)
2. **Task 2: Create RefreshTokenService with full lifecycle and add BadCredentialsException handler** - `4388a75` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified

- `src/main/kotlin/kz/innlab/template/authentication/RefreshToken.kt` - Added `usedAt: Instant?` and `replacedByTokenHash: String?` fields after `revoked`
- `src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt` - Added `deleteAllByUser` (@Modifying @Query JPQL) and `findByReplacedByTokenHash` methods
- `src/main/kotlin/kz/innlab/template/authentication/RefreshTokenService.kt` - Created: full token lifecycle (createToken, rotate, revoke), TokenGracePeriodException, SecureRandom + SHA-256 helpers
- `src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt` - Added BadCredentialsException (401) and TokenGracePeriodException (409) handlers

## Decisions Made

- Grace window replay returns 409 Conflict (not the replacement raw token) — raw tokens are not stored per TOKN-02, so 409 with Retry-After semantics is the correct mobile UX signal. The mobile client retries with the new token it already received from the first successful rotation.
- `TokenGracePeriodException` defined in same file as `RefreshTokenService` for simplicity — no architectural need for a separate file.
- `deleteAllByUser` uses JPQL `DELETE FROM RefreshToken rt WHERE rt.user = :user` (not derived `deleteBy`) to execute a single bulk statement and avoid N+1 entity loading.
- `revoke()` does NOT set `usedAt` — this distinguishes logout from rotation in the state machine. Logout-revoked tokens have `revoked=true, usedAt=null`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Maven wrapper (.mvnw) was broken (missing `.mvn/wrapper/maven-wrapper.properties`). Used system `mvn` directly as a workaround. This is a pre-existing issue not caused by this plan.

## User Setup Required

None - no external service configuration required. The `app.auth.refresh-token.expiry-days` property defaults to 30 days via `@Value("\${app.auth.refresh-token.expiry-days:30}")`.

## Next Phase Readiness

- RefreshTokenService is ready for use by AuthController (Plan 03-02) and GoogleAuthService (Plan 03-03)
- `createToken(user)` returns raw token for client; `rotate(rawToken)` returns `Pair<User, String>` for controller
- All existing tests (5) still pass after changes

---
*Phase: 03-google-auth-and-token-management*
*Completed: 2026-03-01*

## Self-Check: PASSED

- RefreshTokenService.kt: FOUND
- RefreshToken.kt: FOUND
- RefreshTokenRepository.kt: FOUND
- GlobalExceptionHandler.kt: FOUND
- Commit f2ac8e5 (Task 1): FOUND
- Commit 4388a75 (Task 2): FOUND
