---
phase: 01-add-local-authentication-email-password-and-phone-sms-code-login
plan: 02
subsystem: auth
tags: [spring-security, bcrypt, dao-authentication-provider, jwt, local-auth, email-password]

# Dependency graph
requires:
  - phase: 01-01
    provides: AuthProvider.LOCAL enum value, User.passwordHash field, and UserRepository.findByProviderAndProviderId()

provides:
  - LocalAuthConfig with BCrypt PasswordEncoder and separate localAuthenticationManager bean
  - LocalUserDetailsService scoped to (LOCAL, email) for DaoAuthenticationProvider
  - LocalAuthService with register() and login() methods
  - POST /api/v1/auth/local/register (201) endpoint
  - POST /api/v1/auth/local/login (200) endpoint
  - 409 Conflict handler for duplicate email registration

affects: [01-03-phone-sms-auth, future-auth-plans]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Separate localAuthenticationManager bean (ProviderManager wrapping DaoAuthenticationProvider) keeps LOCAL auth isolated from resource server JWT auth manager
    - LocalUserDetailsService scopes DB lookup to (LOCAL, email) - not just by email - to prevent cross-provider credential leakage
    - providerId = email for LOCAL email users (consistent with (provider, providerId) composite key design)
    - IllegalStateException thrown on duplicate registration, mapped to 409 Conflict in AuthExceptionHandler

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/config/LocalAuthConfig.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/LocalUserDetailsService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/LocalAuthService.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/LocalRegisterRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/LocalLoginRequest.kt
    - src/test/kotlin/kz/innlab/template/LocalAuthIntegrationTest.kt
  modified:
    - src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt
    - src/main/kotlin/kz/innlab/template/authentication/exception/AuthExceptionHandler.kt

key-decisions:
  - "localAuthenticationManager bean uses ProviderManager(DaoAuthenticationProvider) - separate from resource server's JWT auth to avoid interference"
  - "LocalUserDetailsService.loadUserByUsername scopes lookup to (LOCAL, email) not just email - prevents Google/Apple users from being returned for local auth"
  - "LocalAuthService login() fetches user after authenticationManager.authenticate() succeeds - avoids redundant DB lookup on failure path"
  - "IllegalStateException -> 409 Conflict via AuthExceptionHandler.handleConflict() - consistent with existing TokenGracePeriodException -> 409 pattern"

patterns-established:
  - "Separate AuthenticationManager per auth mechanism: each provider (LOCAL, future phone) gets its own ProviderManager bean"
  - "Scoped UserDetailsService: always look up by (provider, providerId) never by email alone"

requirements-completed: [LOCAL-EMAIL-REGISTER, LOCAL-EMAIL-LOGIN]

# Metrics
duration: 2min
completed: 2026-03-02
---

# Phase 01 Plan 02: Email+Password Authentication Summary

**Spring Security DaoAuthenticationProvider with BCrypt email+password auth via /local/register and /local/login, with 7 integration tests covering happy path and error cases**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-01T19:53:07Z
- **Completed:** 2026-03-01T19:55:14Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- LocalAuthConfig registers BCrypt PasswordEncoder and a dedicated localAuthenticationManager (DaoAuthenticationProvider) separate from the resource server JWT auth
- LocalUserDetailsService loads LOCAL users by (provider, email) preventing cross-provider credential leakage from Google/Apple users with same email
- LocalAuthService register() and login() methods return JWT access + refresh token pairs, with BCrypt hash stored
- POST /api/v1/auth/local/register (201) and POST /api/v1/auth/local/login (200) endpoints added to AuthController
- AuthExceptionHandler extended with IllegalStateException -> 409 Conflict for duplicate email registration
- 7 integration tests pass: register success, duplicate 409, login success, wrong password 401, non-existent email 401, empty email 400, short password 400

## Task Commits

Each task was committed atomically:

1. **Task 1: Create LocalAuthConfig, LocalUserDetailsService, LocalAuthService, and DTOs** - `6dbcacd` (feat)
2. **Task 2: Add AuthController endpoints, exception handler, and integration tests** - `63f8336` (feat)

## Files Created/Modified

- `src/main/kotlin/kz/innlab/template/config/LocalAuthConfig.kt` - BCrypt PasswordEncoder bean + localAuthenticationManager (DaoAuthenticationProvider)
- `src/main/kotlin/kz/innlab/template/authentication/service/LocalUserDetailsService.kt` - UserDetailsService loading by (LOCAL, email)
- `src/main/kotlin/kz/innlab/template/authentication/service/LocalAuthService.kt` - register() and login() using authenticationManager.authenticate()
- `src/main/kotlin/kz/innlab/template/authentication/dto/LocalRegisterRequest.kt` - @Email, @NotBlank, @Size(8-128) validation
- `src/main/kotlin/kz/innlab/template/authentication/dto/LocalLoginRequest.kt` - @Email, @NotBlank validation
- `src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt` - added /local/register and /local/login endpoints
- `src/main/kotlin/kz/innlab/template/authentication/exception/AuthExceptionHandler.kt` - added IllegalStateException -> 409 handler
- `src/test/kotlin/kz/innlab/template/LocalAuthIntegrationTest.kt` - 7 integration tests

## Decisions Made

- localAuthenticationManager bean is a separate ProviderManager(DaoAuthenticationProvider) — not the global AuthenticationManager — to avoid interfering with the resource server's JWT-based auth
- LocalUserDetailsService scopes lookup to (LOCAL, email) not just email, consistent with (provider, providerId) composite key design
- IllegalStateException thrown on duplicate registration, mapped to 409 in AuthExceptionHandler — consistent with existing TokenGracePeriodException -> 409 pattern
- providerId = email for LOCAL email users — same composite key design as Google/Apple

## Deviations from Plan

None - plan executed exactly as written. All 16 tests pass (9 pre-existing + 7 new).

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Email+password auth complete end-to-end with BCrypt and integration tests
- AuthController, LocalAuthService, and LocalAuthConfig ready to extend for Plan 03 (phone+SMS OTP auth)
- All existing Google/Apple tests still pass (unaffected)

---
*Phase: 01-add-local-authentication-email-password-and-phone-sms-code-login*
*Completed: 2026-03-02*

## Self-Check: PASSED

- FOUND: src/main/kotlin/kz/innlab/template/config/LocalAuthConfig.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/service/LocalUserDetailsService.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/service/LocalAuthService.kt
- FOUND: src/test/kotlin/kz/innlab/template/LocalAuthIntegrationTest.kt
- FOUND: commit 6dbcacd (Task 1)
- FOUND: commit 63f8336 (Task 2)
