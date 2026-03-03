# Quick Task 8: Fix _id column mapping in BaseEntity

## Problem

Hibernate 7.x maps the Kotlin `private val _id` field to a database column named `_id` by default. The Flyway V1 migration creates the column as `id`. This mismatch causes `SchemaManagementException: missing column [_id] in table [refresh_tokens]` on application startup with `ddl-auto: validate`.

## Fix

Added `@Column(name = "id")` to `BaseEntity._id` field so Hibernate maps to the correct column name.

## Files Changed

- `src/main/kotlin/kz/innlab/template/shared/model/BaseEntity.kt` — added `@Column(name = "id")` annotation

## Verification

- 37/37 tests pass
- All tables affected (users, refresh_tokens, sms_verifications, verification_codes) use `BaseEntity`
