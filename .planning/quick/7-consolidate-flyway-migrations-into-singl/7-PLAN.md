---
phase: quick
plan: 7
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/resources/db/migration/V1__initial_schema.sql
  - src/main/resources/db/migration/V2__add_account_linking.sql
  - src/main/resources/db/migration/V3__add_sms_verifications.sql
  - src/main/resources/application.yaml
autonomous: true
requirements: []
must_haves:
  truths:
    - "Single V1 migration contains the complete final schema"
    - "No gen_random_uuid() DEFAULT clauses exist in any migration"
    - "V2 and V3 migration files are deleted"
    - "Flyway clean-on-validation-error allows dev DB to rebuild from consolidated V1"
    - "Application starts and Hibernate validate passes against the new schema"
  artifacts:
    - path: "src/main/resources/db/migration/V1__initial_schema.sql"
      provides: "Complete consolidated schema — users, user_providers, user_provider_ids, user_roles, refresh_tokens, sms_verifications"
      contains: "CREATE TABLE sms_verifications"
  key_links:
    - from: "V1__initial_schema.sql"
      to: "JPA entities (User, RefreshToken, SmsVerification)"
      via: "Hibernate validate mode"
      pattern: "ddl-auto: validate"
---

<objective>
Consolidate three Flyway migrations (V1, V2, V3) into a single V1 migration containing the complete final schema. Remove all gen_random_uuid() DEFAULT clauses since UUID v7 is generated application-side via BaseEntity + uuid-creator. Delete V2 and V3 files. Add Flyway clean-on-validation-error to dev profile so existing dev databases rebuild cleanly.

Purpose: Greenfield project has no production data — a single V1 is cleaner and avoids carrying migration history from development iterations.
Output: One consolidated V1 migration file, V2/V3 deleted, Flyway dev config updated.
</objective>

<execution_context>
@./.claude/get-shit-done/workflows/execute-plan.md
@./.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@src/main/resources/db/migration/V1__initial_schema.sql
@src/main/resources/db/migration/V2__add_account_linking.sql
@src/main/resources/db/migration/V3__add_sms_verifications.sql
@src/main/resources/application.yaml
@src/main/kotlin/kz/innlab/template/user/model/User.kt
@src/main/kotlin/kz/innlab/template/authentication/model/RefreshToken.kt
@src/main/kotlin/kz/innlab/template/authentication/model/SmsVerification.kt
@src/main/kotlin/kz/innlab/template/shared/model/BaseEntity.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Rewrite V1 migration as consolidated final schema, delete V2/V3</name>
  <files>
    src/main/resources/db/migration/V1__initial_schema.sql,
    src/main/resources/db/migration/V2__add_account_linking.sql,
    src/main/resources/db/migration/V3__add_sms_verifications.sql
  </files>
  <action>
Rewrite V1__initial_schema.sql to contain the FINAL schema state (the result of V1+V2+V3 applied in sequence). This means:

1. **users table** — NO `provider` or `provider_id` columns (those were in old V1 but dropped in V2). NO `DEFAULT gen_random_uuid()` on id. NO `uq_provider_provider_id` constraint. Include: id (UUID PK), email (VARCHAR 255 NOT NULL), name (VARCHAR 255), picture (VARCHAR 512), password_hash (VARCHAR 255), phone (VARCHAR 30 UNIQUE), created_at (TIMESTAMPTZ NOT NULL DEFAULT NOW()), updated_at (TIMESTAMPTZ NOT NULL DEFAULT NOW()). Add partial unique index: `CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE email != '';`

2. **user_providers table** — user_id (UUID NOT NULL REFERENCES users(id)), provider (VARCHAR 50 NOT NULL), CONSTRAINT uq_user_providers UNIQUE (user_id, provider).

3. **user_provider_ids table** — user_id (UUID NOT NULL REFERENCES users(id)), provider (VARCHAR 50 NOT NULL), provider_id (VARCHAR 255 NOT NULL), CONSTRAINT uq_user_provider_ids UNIQUE (user_id, provider).

4. **user_roles table** — user_id (UUID NOT NULL REFERENCES users(id)), role (VARCHAR 50 NOT NULL).

5. **refresh_tokens table** — id (UUID PK, NO DEFAULT), user_id (UUID NOT NULL REFERENCES users(id)), token_hash (VARCHAR 512 NOT NULL UNIQUE), expires_at (TIMESTAMPTZ NOT NULL), revoked (BOOLEAN NOT NULL DEFAULT FALSE), used_at (TIMESTAMPTZ), replaced_by_token_hash (VARCHAR 512), created_at (TIMESTAMPTZ NOT NULL DEFAULT NOW()).

6. **sms_verifications table** — id (UUID PK, NO DEFAULT), phone (VARCHAR 30 NOT NULL), code_hash (VARCHAR 255 NOT NULL), expires_at (TIMESTAMPTZ NOT NULL), used (BOOLEAN NOT NULL DEFAULT FALSE), attempts (INTEGER NOT NULL DEFAULT 0), created_at (TIMESTAMPTZ NOT NULL DEFAULT NOW()). Add index: `CREATE INDEX idx_sms_verifications_phone ON sms_verifications(phone);`

After writing V1, DELETE V2__add_account_linking.sql and V3__add_sms_verifications.sql.

IMPORTANT: Keep `DEFAULT NOW()` on timestamp columns — those are still DB-generated. Only remove `DEFAULT gen_random_uuid()` from UUID primary keys.
  </action>
  <verify>
    <automated>ls src/main/resources/db/migration/ | sort && echo "---" && ! test -f src/main/resources/db/migration/V2__add_account_linking.sql && echo "V2 deleted OK" && ! test -f src/main/resources/db/migration/V3__add_sms_verifications.sql && echo "V3 deleted OK" && echo "---" && grep -c "gen_random_uuid" src/main/resources/db/migration/V1__initial_schema.sql || echo "No gen_random_uuid found (GOOD)"</automated>
  </verify>
  <done>Single V1 migration contains all 6 tables (users, user_providers, user_provider_ids, user_roles, refresh_tokens, sms_verifications) with indexes. No gen_random_uuid() anywhere. V2 and V3 files deleted.</done>
</task>

<task type="auto">
  <name>Task 2: Add Flyway clean-on-validation-error to dev profile and verify startup</name>
  <files>src/main/resources/application.yaml</files>
  <action>
In the dev profile section of application.yaml, add `clean-on-validation-error: true` under `spring.flyway`. This tells Flyway to drop and rebuild the schema when it detects the migration history table is inconsistent (which it will be, since we replaced V1's checksum and removed V2/V3).

The dev profile flyway section should become:
```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
    clean-on-validation-error: true
```

Do NOT touch the prod profile — prod should never have clean-on-validation-error.

After updating the config, run `./mvnw clean compile -q` to verify the project compiles with the new migration in the classpath. Then run the full test suite with `./mvnw test -q` to confirm H2 tests still pass (tests use create-drop, not Flyway, so they should be unaffected).
  </action>
  <verify>
    <automated>cd /Users/bekzat/Workspace/Template/spring-boot-template && ./mvnw clean test -q</automated>
  </verify>
  <done>Dev profile has clean-on-validation-error: true. All tests pass. Project compiles cleanly with consolidated migration.</done>
</task>

</tasks>

<verification>
1. Only V1__initial_schema.sql exists in db/migration/ — V2 and V3 are gone
2. V1 contains CREATE TABLE for all 6 tables: users, user_providers, user_provider_ids, user_roles, refresh_tokens, sms_verifications
3. Zero occurrences of gen_random_uuid() in V1
4. Partial unique index on users.email and index on sms_verifications.phone are present
5. Dev profile has flyway.clean-on-validation-error: true
6. Prod profile does NOT have clean-on-validation-error
7. All tests pass (./mvnw test)
</verification>

<success_criteria>
- Single V1 migration represents complete final schema matching all JPA entities
- No DB-side UUID generation (gen_random_uuid removed)
- V2 and V3 migration files deleted
- Flyway dev profile handles schema history mismatch gracefully
- Full test suite passes
</success_criteria>

<output>
After completion, create `.planning/quick/7-consolidate-flyway-migrations-into-singl/7-SUMMARY.md`
</output>
