---
phase: quick-7
plan: 7
subsystem: database
tags: [flyway, postgresql, migration, uuid]

# Dependency graph
requires: []
provides:
  - Single V1 Flyway migration with complete final schema (6 tables)
  - No gen_random_uuid() DB-side UUID generation — all UUIDs generated app-side via BaseEntity
  - Flyway clean-on-validation-error in dev profile for schema rebuild on history mismatch
affects: [all phases that depend on DB schema — future migrations should continue from V1 baseline]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single consolidated V1 migration for greenfield projects — no migration history from dev iterations"
    - "UUID PKs without DEFAULT clause — application generates UUID v7 via BaseEntity + uuid-creator"
    - "Flyway clean-on-validation-error: true in dev only — prod never uses this flag"

key-files:
  created: []
  modified:
    - src/main/resources/db/migration/V1__initial_schema.sql
    - src/main/resources/application.yaml
  deleted:
    - src/main/resources/db/migration/V2__add_account_linking.sql
    - src/main/resources/db/migration/V3__add_sms_verifications.sql

key-decisions:
  - "Consolidated V1 migration is the correct pattern for greenfield — no prod data means no incremental migrations needed"
  - "UUID PKs have no DEFAULT clause — gen_random_uuid() removed since uuid-creator in BaseEntity generates UUID v7 app-side"
  - "clean-on-validation-error: true only in dev profile — prod migration history must never be destroyed"
  - "DEFAULT NOW() retained on timestamp columns — these are still DB-generated, only UUID defaults removed"

patterns-established:
  - "Flyway clean-on-validation-error: true in dev profile — allows clean schema rebuild when migration checksums change"

requirements-completed: []

# Metrics
duration: 5min
completed: 2026-03-02
---

# Quick Task 7: Consolidate Flyway Migrations Summary

**Three Flyway migrations (V1+V2+V3) collapsed into single V1 with complete 6-table schema; gen_random_uuid() removed from all UUID PKs; dev profile gets clean-on-validation-error for graceful rebuild**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-02T06:45:00Z
- **Completed:** 2026-03-02T06:50:00Z
- **Tasks:** 2
- **Files modified:** 2 modified, 2 deleted

## Accomplishments
- Rewrote V1 to contain all 6 tables: users, user_providers, user_provider_ids, user_roles, refresh_tokens, sms_verifications
- Removed all `DEFAULT gen_random_uuid()` from UUID primary keys — UUIDs generated app-side via BaseEntity + uuid-creator (UUID v7)
- Deleted V2__add_account_linking.sql and V3__add_sms_verifications.sql
- Added `clean-on-validation-error: true` to dev Flyway profile only (prod unchanged)
- All 23 tests pass after consolidation

## Task Commits

Each task was committed atomically:

1. **Task 1: Rewrite V1 migration as consolidated final schema, delete V2/V3** - `c7562b5` (chore)
2. **Task 2: Add Flyway clean-on-validation-error to dev profile and verify startup** - `e89675a` (chore)

**Plan metadata:** (in final docs commit)

## Files Created/Modified

- `src/main/resources/db/migration/V1__initial_schema.sql` - Complete final schema: users (no provider/provider_id columns, no gen_random_uuid), user_providers, user_provider_ids, user_roles (all three from V2), refresh_tokens, sms_verifications (from V3) with all indexes
- `src/main/resources/application.yaml` - Dev profile flyway section: added `clean-on-validation-error: true`
- ~~`src/main/resources/db/migration/V2__add_account_linking.sql`~~ - Deleted
- ~~`src/main/resources/db/migration/V3__add_sms_verifications.sql`~~ - Deleted

## Decisions Made

- Retained `DEFAULT NOW()` on all timestamp columns — these are still DB-generated; only UUID defaults removed
- Partial unique index `uq_users_email ON users(email) WHERE email != ''` included in V1 — allows multiple phone users with empty email string (NOT NULL column)
- `clean-on-validation-error` is a Flyway dev convenience, not a prod safety net — prod profile intentionally excluded

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Self-Check

Files verified:
- `src/main/resources/db/migration/V1__initial_schema.sql` - EXISTS, contains all 6 tables, 0 occurrences of gen_random_uuid
- V2 and V3 files - CONFIRMED DELETED
- `src/main/resources/application.yaml` dev profile - CONFIRMED has clean-on-validation-error: true
- Prod profile - CONFIRMED no clean-on-validation-error

Commits verified:
- `c7562b5` - Task 1 commit (V1 rewrite + V2/V3 deletion)
- `e89675a` - Task 2 commit (application.yaml dev profile update)

Test results: 23 tests, 0 failures, 0 errors — BUILD SUCCESS

## Self-Check: PASSED

## Next Phase Readiness
- Flyway migration history is now clean for a fresh dev DB setup
- Any new developer running the project will apply a single V1 migration covering the full schema
- If running against an existing dev DB, Flyway will detect the changed V1 checksum and missing V2/V3 — clean-on-validation-error will wipe and rebuild automatically

---
*Phase: quick-7*
*Completed: 2026-03-02*
