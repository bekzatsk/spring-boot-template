---
phase: 02-implement-account-linking-logic-email-is-globally-unique-across-all-providers-one-user-one-email-one-account
plan: "01"
subsystem: database
tags: [jpa, hibernate, elementcollection, flyway, account-linking]

requires:
  - phase: 01-add-local-authentication-email-password-and-phone-sms-code-login
    provides: User entity with provider/providerId columns, AuthProvider enum with LOCAL
provides:
  - Multi-provider User entity with @ElementCollection providers set and providerIds map
  - UserRepository with findByEmail() and findByAppleProviderId() queries
  - V2 Flyway migration creating user_providers and user_provider_ids tables
affects: [02-02, service-layer, tests, account-linking]

tech-stack:
  added: []
  patterns: [ElementCollection for multi-provider mapping, partial unique index for conditional email uniqueness]

key-files:
  created:
    - src/main/resources/db/migration/V2__add_account_linking.sql
  modified:
    - src/main/kotlin/kz/innlab/template/user/model/User.kt
    - src/main/kotlin/kz/innlab/template/user/repository/UserRepository.kt

key-decisions:
  - "User constructor takes only email — providers and providerIds are mutable fields, not constructor params"
  - "FetchType.EAGER on both @ElementCollection fields — tiny collections (max 3 entries), needed after transaction closes (open-in-view=false)"
  - "Partial unique index on email WHERE email != '' — allows multiple phone users with empty email"
  - "LOCAL email users NOT migrated to user_provider_ids — LOCAL has no external provider ID"

patterns-established:
  - "Multi-provider user creation: User(email).also { it.providers.add(...); it.providerIds[...] = ... }"
  - "Email-first lookup: findByEmail() is primary, findByAppleProviderId() is fallback for returning Apple users"

requirements-completed: [LINK-01, LINK-02, LINK-03]

duration: 3min
completed: 2026-03-02
---

# Plan 02-01: Entity + Migration Summary

**Multi-provider User entity with @ElementCollection and V2 Flyway migration replacing single-provider model**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-02
- **Completed:** 2026-03-02
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Rewrote User entity from single-provider (provider + providerId) to multi-provider model with @ElementCollection
- UserRepository now uses findByEmail() and findByAppleProviderId() instead of findByProviderAndProviderId()
- V2 Flyway migration creates collection tables, migrates data, adds partial email uniqueness index, drops old columns

## Task Commits

Each task was committed atomically:

1. **Task 1: Rewrite User entity and UserRepository** - `c0a59e9` (feat)
2. **Task 2: Create V2 Flyway migration** - `5b2bacb` (feat)

## Files Created/Modified
- `src/main/kotlin/kz/innlab/template/user/model/User.kt` - Multi-provider User entity with @ElementCollection providers and providerIds
- `src/main/kotlin/kz/innlab/template/user/repository/UserRepository.kt` - Email-based and Apple sub-based user lookup
- `src/main/resources/db/migration/V2__add_account_linking.sql` - Schema migration from single-provider to multi-provider model

## Decisions Made
- User constructor takes only email -- providers and providerIds are body fields, not constructor params
- FetchType.EAGER on both @ElementCollection -- collections are tiny (max 3), needed outside transaction
- Partial unique index WHERE email != '' -- phone users allowed to have empty email
- LOCAL users not stored in providerIds -- no external ID to map

## Deviations from Plan
None - plan executed exactly as written

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Entity and migration ready for Plan 02-02 service layer rewrite
- Project will NOT compile until Plan 02-02 updates all service and test files to use new constructor
- This is intentional -- compile-driven refactoring approach

---
*Phase: 02-implement-account-linking-logic-email-is-globally-unique-across-all-providers-one-user-one-email-one-account*
*Completed: 2026-03-02*
