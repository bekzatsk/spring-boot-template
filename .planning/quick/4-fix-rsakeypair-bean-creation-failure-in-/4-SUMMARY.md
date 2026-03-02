---
phase: quick-4
plan: 1
subsystem: auth
tags: [jwt, rsa, spring-security, kotlin]

# Dependency graph
requires: []
provides:
  - "rsaKeyPair bean creates in-memory keypair in dev (no keystore configured)"
  - "rsaKeyPair bean loads from PKCS12 keystore in prod (keystore properties present)"
affects: [jwkSource, jwtEncoder, jwtDecoder, tokenService]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Conditional bean logic: !isNullOrBlank() guards keystore path; else falls through to in-memory generation"]

key-files:
  created: []
  modified:
    - src/main/kotlin/kz/innlab/template/config/RsaKeyConfig.kt

key-decisions:
  - "Single-character fix: add ! negation to both isNullOrBlank() calls on line 37 — no structural changes needed"

patterns-established: []

requirements-completed: []

# Metrics
duration: 3min
completed: 2026-03-02
---

# Quick Task 4: Fix RsaKeyPair Bean Creation Failure Summary

**Fixed inverted conditional in RsaKeyConfig.rsaKeyPair() that caused NPE on startup when keystore is not configured — single `!` negation added to both `isNullOrBlank()` checks**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-03-02T04:12:00Z
- **Completed:** 2026-03-02T04:15:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Fixed NullPointerException ("Factory method 'rsaKeyPair' threw exception with message: null") on application startup in dev profile
- Corrected conditional logic so the keystore-loading branch runs only when keystore properties ARE configured
- In-memory RSA keypair generation now correctly runs when keystore properties are null/blank (dev default)
- All 23 existing tests pass after the fix

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix inverted conditional in rsaKeyPair() factory method** - `aef19cc` (fix)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/kotlin/kz/innlab/template/config/RsaKeyConfig.kt` - Changed `isNullOrBlank()` to `!isNullOrBlank()` on both keystore property checks (line 37)

## Decisions Made

None - followed plan as specified. The bug was exactly as described: the if/else branches were inverted, causing the keystore-loading branch (which force-unwraps `keystoreLocation!!`) to execute when both values were null.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Root cause was clear from the plan description: `keystoreLocation.isNullOrBlank()` returned `true` (null is blank), entered the keystore-loading branch, then immediately crashed on `keystoreLocation!!` (force-unwrap of null).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Application now starts successfully in dev profile without any keystore configuration
- All 23 integration tests pass
- Prod path (PKCS12 keystore loading) is correct: only runs when `!keystoreLocation.isNullOrBlank() && !keystorePassword.isNullOrBlank()`

---
*Phase: quick-4*
*Completed: 2026-03-02*
