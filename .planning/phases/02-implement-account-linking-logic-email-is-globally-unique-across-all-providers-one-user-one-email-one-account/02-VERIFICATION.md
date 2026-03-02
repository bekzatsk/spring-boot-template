---
status: passed
verified: 2026-03-02
phase: 02-implement-account-linking-logic-email-is-globally-unique-across-all-providers-one-user-one-email-one-account
---

# Phase 02 Verification: Account Linking Logic

## Goal
Migrate from single-provider (provider, providerId) model to multi-provider model (providers Set + providerIds Map via @ElementCollection), with email as the universal identity key and account linking across LOCAL, GOOGLE, and APPLE providers.

## Must-Have Verification

### Plan 02-01: Entity + Migration

| # | Must-Have | Status | Evidence |
|---|-----------|--------|----------|
| 1 | User entity has providers (Set<AuthProvider>) and providerIds (Map<AuthProvider, String>) replacing single provider/providerId | PASS | User.kt has @ElementCollection providers and providerIds fields |
| 2 | UserRepository has findByEmail() and findByAppleProviderId(), findByProviderAndProviderId removed | PASS | UserRepository.kt verified; 0 references to findByProviderAndProviderId in entire codebase |
| 3 | V2 Flyway migration creates user_providers and user_provider_ids tables and migrates existing data | PASS | V2__add_account_linking.sql exists with CREATE TABLE, INSERT INTO, and ALTER TABLE DROP statements |

### Plan 02-02: Service Layer + Tests

| # | Must-Have | Status | Evidence |
|---|-----------|--------|----------|
| 4 | Google sign-in with existing LOCAL email links GOOGLE provider | PASS | UserService.findOrCreateGoogleUser uses findByEmail first, then adds GOOGLE provider |
| 5 | LOCAL register with existing GOOGLE email adds LOCAL provider and sets password | PASS | LocalAuthService.register checks findByEmail, links LOCAL if passwordHash is null |
| 6 | Apple returning user (email absent) found by Apple sub in providerIds map | PASS | UserService.findOrCreateAppleUser calls findByAppleProviderId first |
| 7 | Phone user creation still works with empty string email | PASS | UserService.findOrCreatePhoneUser uses findByPhone, creates User(email="") |
| 8 | All 22+ existing tests pass after refactoring | PASS | `./mvnw clean test` — Tests run: 22, Failures: 0, Errors: 0 |

## Requirement Coverage

| Requirement | Plan | Status |
|-------------|------|--------|
| LINK-01 | 02-01 | Covered |
| LINK-02 | 02-01 | Covered |
| LINK-03 | 02-01 | Covered |
| LINK-04 | 02-02 | Covered |
| LINK-05 | 02-02 | Covered |
| LINK-06 | 02-02 | Covered |

## Test Results

```
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Score

**8/8 must-haves verified. All requirements covered. Status: PASSED.**
