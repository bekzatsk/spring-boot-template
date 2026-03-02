---
phase: 02-implement-account-linking-logic-email-is-globally-unique-across-all-providers-one-user-one-email-one-account
plan: "02"
subsystem: auth
tags: [account-linking, jpa, spring-security, integration-tests]

requires:
  - phase: 02-implement-account-linking-logic-email-is-globally-unique-across-all-providers-one-user-one-email-one-account
    provides: Multi-provider User entity with @ElementCollection providers and providerIds
provides:
  - Account linking logic in UserService (findByEmail-first, then link or create)
  - LocalAuthService linking LOCAL credentials to existing social accounts
  - LocalUserDetailsService checking LOCAL in providers set
  - All 22 tests passing with new User(email) constructor pattern
affects: [future-phases, api-consumers]

tech-stack:
  added: []
  patterns: [findByEmail-first account linking, provider set membership check]

key-files:
  created: []
  modified:
    - src/main/kotlin/kz/innlab/template/user/service/UserService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/LocalAuthService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/LocalUserDetailsService.kt
    - src/main/kotlin/kz/innlab/template/user/repository/UserRepository.kt
    - src/main/kotlin/kz/innlab/template/user/dto/UserProfileResponse.kt
    - src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt
    - src/test/kotlin/kz/innlab/template/AppleAuthIntegrationTest.kt
    - src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt
    - src/test/kotlin/kz/innlab/template/LocalAuthIntegrationTest.kt

key-decisions:
  - "UserService.findOrCreateGoogleUser uses findByEmail first — links GOOGLE provider to existing account if email matches"
  - "UserService.findOrCreateAppleUser uses findByAppleProviderId first (returning users), then findByEmail (first login) — handles both cases"
  - "LocalAuthService.register links LOCAL credentials to existing social accounts if email exists but passwordHash is null"
  - "LocalUserDetailsService checks AuthProvider.LOCAL in user.providers — prevents social-only users from local auth"
  - "UserRepository.findByPhone added for phone user lookup — replaces old findByProviderAndProviderId(LOCAL, phone)"
  - "UserProfileResponse changed from single provider to providers list — API returns all linked providers"
  - "Name/picture only set if currently null when linking — never overwrite existing user data"

patterns-established:
  - "Account linking: findByEmail() first, then add provider to existing or create new user"
  - "Provider check: AuthProvider.X in user.providers instead of user.provider == X"

requirements-completed: [LINK-04, LINK-05, LINK-06]

duration: 4min
completed: 2026-03-02
---

# Plan 02-02: Service Layer + Tests Summary

**Account linking logic in all service layers with findByEmail-first pattern, all 22 tests green**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-02
- **Completed:** 2026-03-02
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- UserService implements findByEmail-first account linking for Google, Apple, and phone users
- LocalAuthService supports linking LOCAL credentials to existing social accounts (Google/Apple user can add password)
- LocalUserDetailsService checks LOCAL membership in providers set instead of old provider == LOCAL
- All 22 integration tests pass with new User(email) constructor pattern

## Task Commits

Each task was committed atomically:

1. **Task 1: Rewrite service layers for account linking logic** - `fbac7ef` (feat)
2. **Task 2: Update all tests for new User constructor** - `9e315c4` (test)

## Files Created/Modified
- `src/main/kotlin/kz/innlab/template/user/service/UserService.kt` - Account linking in findOrCreate* methods
- `src/main/kotlin/kz/innlab/template/authentication/service/LocalAuthService.kt` - Email-based register/login with linking
- `src/main/kotlin/kz/innlab/template/authentication/service/LocalUserDetailsService.kt` - LOCAL provider membership check
- `src/main/kotlin/kz/innlab/template/user/repository/UserRepository.kt` - Added findByPhone()
- `src/main/kotlin/kz/innlab/template/user/dto/UserProfileResponse.kt` - providers list instead of single provider
- `src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt` - New User(email) constructor
- `src/test/kotlin/kz/innlab/template/AppleAuthIntegrationTest.kt` - New constructor + findByEmail assertions
- `src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt` - New constructor + findByPhone assertions
- `src/test/kotlin/kz/innlab/template/LocalAuthIntegrationTest.kt` - findByEmail assertions

## Decisions Made
- UserProfileResponse changed from single `provider` field to `providers` list -- API-breaking but correct for multi-provider model
- findByPhone() added to UserRepository in this plan (not in 02-01) since phone lookup was only used by UserService
- Name/picture only updated if currently null when linking providers -- preserves user's existing profile data

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] UserProfileResponse.provider reference to removed field**
- **Found during:** Task 1 (compile check)
- **Issue:** UserProfileResponse.kt referenced `user.provider` which no longer exists after User entity rewrite
- **Fix:** Changed to `providers = user.providers.map { it.name }` returning list of all linked providers
- **Files modified:** src/main/kotlin/kz/innlab/template/user/dto/UserProfileResponse.kt
- **Verification:** Compilation passes, tests pass
- **Committed in:** fbac7ef (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Essential fix for compilation. API field changed from `provider: String` to `providers: [String]`.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Account linking fully operational: one email = one user = N providers
- All 22 tests pass with new multi-provider model
- API change: `provider` field replaced with `providers` array in /users/me response

---
*Phase: 02-implement-account-linking-logic-email-is-globally-unique-across-all-providers-one-user-one-email-one-account*
*Completed: 2026-03-02*
