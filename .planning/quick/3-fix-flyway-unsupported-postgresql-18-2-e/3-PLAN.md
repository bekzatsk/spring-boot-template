---
phase: quick-3
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - pom.xml
autonomous: true
requirements: [QUICK-3]

must_haves:
  truths:
    - "Application starts successfully against PostgreSQL 18.2"
    - "Flyway runs V1__initial_schema.sql migration without error"
  artifacts:
    - path: "pom.xml"
      provides: "flyway-database-postgresql dependency"
      contains: "flyway-database-postgresql"
  key_links:
    - from: "spring-boot-starter-flyway"
      to: "flyway-database-postgresql"
      via: "classpath discovery"
      pattern: "flyway-database-postgresql"
---

<objective>
Fix "Unsupported Database: PostgreSQL 18.2" error on application startup.

Purpose: The application fails to start because `flyway-database-postgresql` is missing from the classpath. Starting with Flyway 10, database-specific support was modularized into separate artifacts. The `spring-boot-starter-flyway` only brings in `flyway-core` — the PostgreSQL handler module must be added explicitly. Without it, Flyway cannot recognize PostgreSQL at any version.

Output: Updated pom.xml with `flyway-database-postgresql` dependency; application starts and runs migrations against PostgreSQL 18.2.
</objective>

<execution_context>
@./.claude/get-shit-done/workflows/execute-plan.md
@./.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@pom.xml

Key facts:
- Spring Boot 4.0.3 manages `flyway.version` at 11.14.1
- `spring-boot-starter-flyway` pulls in `flyway-core:11.14.1` only
- `flyway-database-postgresql` is managed by the BOM at 11.14.1 — no explicit version needed
- Test profile uses H2 with Flyway disabled (spring.flyway.enabled=false) — no test impact
- Only migration file: V1__initial_schema.sql (uses PostgreSQL-specific DDL: gen_random_uuid, TIMESTAMPTZ)

If `flyway-database-postgresql:11.14.1` still produces the "Unsupported Database: PostgreSQL 18.2" error (i.e., version 11.14.1 genuinely does not support PG 18.x), escalate by overriding `flyway.version` in pom.xml `<properties>` to 11.15.x or 12.0.3 (latest). Try the BOM-managed version first — it is likely sufficient since the real issue is the missing module, not the Flyway version.
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add flyway-database-postgresql dependency to pom.xml</name>
  <files>pom.xml</files>
  <action>
Add `flyway-database-postgresql` dependency to pom.xml immediately after the existing `spring-boot-starter-flyway` dependency block. Do NOT specify a version — the Spring Boot 4.0.3 BOM manages it at 11.14.1 via the `flyway.version` property.

Add this dependency right after the `spring-boot-starter-flyway` closing tag (after line 71):

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

Do NOT:
- Override flyway.version in properties (BOM-managed 11.14.1 should work)
- Remove or modify the existing spring-boot-starter-flyway dependency
- Add any version tag to the new dependency (BOM manages it)

After editing pom.xml, run `./mvnw dependency:tree -Dincludes=org.flywaydb` to confirm `flyway-database-postgresql:11.14.1` appears in the resolved tree.
  </action>
  <verify>
    <automated>cd /Users/bekzat/Workspace/Template/spring-boot-template && ./mvnw dependency:tree -Dincludes=org.flywaydb 2>/dev/null | grep "flyway-database-postgresql"</automated>
  </verify>
  <done>pom.xml contains flyway-database-postgresql dependency; dependency resolves to 11.14.1 via BOM; `mvn dependency:tree` shows both flyway-core and flyway-database-postgresql</done>
</task>

<task type="auto">
  <name>Task 2: Verify application compiles and tests pass</name>
  <files>pom.xml</files>
  <action>
Run the full test suite to confirm the new dependency does not break anything. Tests use H2 with Flyway disabled, so they should be unaffected.

Run: `./mvnw clean test`

All existing tests (22+) must pass. If any test fails, investigate whether it is related to the Flyway dependency change (unlikely given Flyway is disabled in test profile).

Note: Full verification against a live PostgreSQL 18.2 instance happens at runtime (dev profile startup), not in this automated step. The user will verify startup manually.
  </action>
  <verify>
    <automated>cd /Users/bekzat/Workspace/Template/spring-boot-template && ./mvnw clean test -q 2>&1 | tail -5</automated>
  </verify>
  <done>All tests pass (exit code 0); no compilation errors; application is ready to start against PostgreSQL 18.2</done>
</task>

</tasks>

<verification>
1. `./mvnw dependency:tree -Dincludes=org.flywaydb` shows flyway-database-postgresql:11.14.1
2. `./mvnw clean test` passes all tests
3. (Manual) `./mvnw spring-boot:run` starts without "Unsupported Database" error when PostgreSQL 18.2 is running on localhost:5432
</verification>

<success_criteria>
- flyway-database-postgresql is in pom.xml and resolves via BOM (no hardcoded version)
- All existing tests continue to pass
- Application starts successfully against PostgreSQL 18.2 (manual verification by user)
</success_criteria>

<output>
After completion, create `.planning/quick/3-fix-flyway-unsupported-postgresql-18-2-e/3-SUMMARY.md`
</output>
