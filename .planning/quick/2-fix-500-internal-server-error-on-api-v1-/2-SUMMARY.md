---
phase: quick
plan: 2
subsystem: auth
tags: [hibernate, jpa, lazy-loading, refresh-token, exception-handling, slf4j]

# Dependency graph
requires:
  - phase: 03-google-auth-and-token-management
    provides: RefreshToken entity and RefreshTokenRepository with rotation logic
provides:
  - JOIN FETCH query on findByTokenHash — User entity eagerly loaded with RefreshToken
  - Exception logging in GlobalExceptionHandler catch-all handler
affects: [refresh token flow, 500 error diagnostics]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Use JOIN FETCH in JPQL when entity associations are LAZY and must be accessed outside transaction"
    - "All catch-all exception handlers must log with logger.error() before returning 500"

key-files:
  created: []
  modified:
    - src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt
    - src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt

key-decisions:
  - "JOIN FETCH rt.user on findByTokenHash — fixes LazyInitializationException when AuthController accesses user.roles outside @Transactional scope"
  - "SLF4J logger added to GlobalExceptionHandler companion object — ensures 500 errors log full stack trace for future diagnosis"

patterns-established:
  - "JPQL JOIN FETCH for LAZY associations accessed outside transaction boundary"

requirements-completed: []

# Metrics
duration: 5min
completed: 2026-03-01
---

# Quick Task 2: Fix 500 Internal Server Error on POST /api/v1/auth/refresh Summary

**Fixed LazyInitializationException on refresh endpoint by eagerly loading User via JOIN FETCH, and added SLF4J error logging to GlobalExceptionHandler so 500s are now diagnosable**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-01T17:50:00Z
- **Completed:** 2026-03-01T17:55:00Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments
- `RefreshTokenRepository.findByTokenHash` now uses `JOIN FETCH rt.user` — User entity is fully initialized when returned, so `AuthController.refresh()` can safely access `user.roles` outside the `@Transactional` scope
- `GlobalExceptionHandler.handleGeneral()` now logs `logger.error("Unhandled exception: {}", ex.message, ex)` before returning 500 — future unhandled exceptions expose their class, message, and full stack trace in server logs
- All 9 existing tests (SecurityIntegrationTest x4, AppleAuthIntegrationTest x4, TemplateApplicationTests x1) pass with zero modifications

## Task Commits

Each task was committed atomically:

1. **Task 1: Add JOIN FETCH to findByTokenHash and add exception logging** - `ce3560f` (fix)

**Plan metadata:** (see final commit below)

## Files Created/Modified
- `src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt` — Replaced derived `findByTokenHash` with `@Query` using `JOIN FETCH rt.user` to eagerly load User entity
- `src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt` — Added `LoggerFactory` import, `companion object` with SLF4J logger, and `logger.error()` call in `handleGeneral()`

## Decisions Made
- JOIN FETCH at the repository level is the correct fix because `open-in-view=false` means the persistence context closes before the controller layer; fetching eagerly is cleaner than re-opening a transaction in AuthController
- Logger added as companion object (not instance field) following Kotlin/JVM best practice — one logger per class shared across all instances

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Refresh endpoint is now functional; LazyInitializationException will no longer occur
- All future unhandled exceptions will produce diagnosable server logs with full stack traces

---
*Phase: quick*
*Completed: 2026-03-01*
