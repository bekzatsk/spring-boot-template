---
phase: 04-replace-all-uuid-generation-with-uuid-v7-time-ordered-ids-for-chronological-sorting-and-cursor-based-pagination
plan: 01
subsystem: database
tags: [uuid-v7, uuid-creator, persistable, jpa, base-entity]

requires:
  - phase: 03-replace-twilio-verify-with-self-managed-sms-code-generation-and-verification
    provides: SmsVerification entity and all auth services using entity id

provides:
  - BaseEntity @MappedSuperclass with UUID v7 generation and Persistable<UUID>
  - All 3 entities (User, RefreshToken, SmsVerification) using time-ordered UUIDs
  - Non-null entity IDs available before persist (no more id!! force-unwraps)

affects: [cursor-pagination, entity-ordering, future-entities]

tech-stack:
  added: [uuid-creator 6.1.1]
  patterns: [BaseEntity with Persistable<UUID>, application-assigned UUID v7 ids]

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/shared/model/BaseEntity.kt
  modified:
    - pom.xml
    - src/main/kotlin/kz/innlab/template/user/model/User.kt
    - src/main/kotlin/kz/innlab/template/authentication/model/RefreshToken.kt
    - src/main/kotlin/kz/innlab/template/authentication/model/SmsVerification.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt
    - src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/PhoneOtpService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/GoogleOAuth2Service.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/AppleOAuth2Service.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/LocalAuthService.kt
    - src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt

key-decisions:
  - "BaseEntity uses private _id field to avoid Kotlin getId() JVM signature clash with Persistable<UUID>"
  - "No Flyway migration needed — UUID column type accepts any UUID version"

patterns-established:
  - "BaseEntity pattern: all JPA entities extend BaseEntity for UUID v7 ids with Persistable<UUID>"
  - "@PostPersist/@PostLoad callbacks for isNew() detection — prevents merge() on new entities"

requirements-completed: [UUID7-01, UUID7-02, UUID7-03]

duration: 3min
completed: 2026-03-02
---

# Phase 04-01: UUID v7 Migration Summary

**All JPA entities migrated to UUID v7 via shared BaseEntity with Persistable<UUID> — time-ordered IDs, non-null before persist, all 23 tests passing**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-02T10:18:00+05:00
- **Completed:** 2026-03-02T10:23:00+05:00
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- Created BaseEntity @MappedSuperclass with UUID v7 (UuidCreator.getTimeOrderedEpoch()) and Persistable<UUID> for correct isNew detection
- Migrated all 3 entities (User, RefreshToken, SmsVerification) to extend BaseEntity — removed @Id, @GeneratedValue, nullable id
- Removed all 9 `.id!!` force-unwrap operators across 7 source and test files
- All 23 existing tests pass without modification to test logic

## Task Commits

Each task was committed atomically:

1. **Task 1: Add uuid-creator dependency, create BaseEntity, migrate all 3 entities** - `6fffb1d` (feat)
2. **Task 2: Remove all id!! force-unwrap operators and verify tests pass** - `84a03fe` (refactor)

## Files Created/Modified
- `src/main/kotlin/kz/innlab/template/shared/model/BaseEntity.kt` - NEW: Shared @MappedSuperclass with UUID v7 id + Persistable<UUID>
- `pom.xml` - Added uuid-creator 6.1.1 dependency
- `src/main/kotlin/kz/innlab/template/user/model/User.kt` - Extends BaseEntity, removed @Id/@GeneratedValue
- `src/main/kotlin/kz/innlab/template/authentication/model/RefreshToken.kt` - Extends BaseEntity, removed @Id/@GeneratedValue
- `src/main/kotlin/kz/innlab/template/authentication/model/SmsVerification.kt` - Extends BaseEntity, removed @Id/@GeneratedValue
- `src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt` - Removed .id!!
- `src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt` - Removed .id!!
- `src/main/kotlin/kz/innlab/template/authentication/service/PhoneOtpService.kt` - Removed .id!!
- `src/main/kotlin/kz/innlab/template/authentication/service/GoogleOAuth2Service.kt` - Removed .id!!
- `src/main/kotlin/kz/innlab/template/authentication/service/AppleOAuth2Service.kt` - Removed .id!!
- `src/main/kotlin/kz/innlab/template/authentication/service/LocalAuthService.kt` - Removed .id!! (2 occurrences)
- `src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt` - Removed .id!! (2 occurrences)

## Decisions Made
- BaseEntity uses private `_id` field with `override fun getId(): UUID = _id` to avoid Kotlin JVM signature clash — Kotlin property `id` generates its own `getId()` which clashes with Persistable<UUID>.getId()
- No Flyway migration created — UUID column type accepts any UUID version, only the generation strategy changed

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] BaseEntity id property JVM signature clash**
- **Found during:** Task 1 (BaseEntity creation)
- **Issue:** Plan specified `val id: UUID` as a Kotlin property alongside `override fun getId(): UUID = id` from Persistable<UUID>. This creates two methods with the same JVM signature `getId()Ljava/util/UUID;`
- **Fix:** Changed to `private val _id: UUID` with `override fun getId(): UUID = _id`. The Persistable interface's `getId()` method is still exposed as `id` property access in Kotlin.
- **Files modified:** src/main/kotlin/kz/innlab/template/shared/model/BaseEntity.kt
- **Verification:** `./mvnw compile` succeeds, all entity subclasses can access `id` via inherited getter
- **Committed in:** 6fffb1d (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Minor implementation detail fix. No scope creep.

## Issues Encountered
None beyond the JVM signature clash resolved above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- UUID v7 foundation complete for all entities
- Future entities should extend BaseEntity for consistent time-ordered IDs
- Cursor-based pagination can now use `id` for ordering (UUID v7 is time-sorted)

---
*Phase: 04-replace-all-uuid-generation-with-uuid-v7-time-ordered-ids-for-chronological-sorting-and-cursor-based-pagination*
*Completed: 2026-03-02*
