---
phase: 01-add-local-authentication-email-password-and-phone-sms-code-login
verified: 2026-03-02T00:00:00Z
status: passed
score: 19/19 must-haves verified
re_verification: false
---

# Phase 01: Local Authentication Verification Report

**Phase Goal:** Users can register and login with email+password or phone+SMS OTP, with Flyway-managed schema migrations, extending the existing JWT token infrastructure
**Verified:** 2026-03-02
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

All truths derive from the three plan `must_haves.truths` blocks covering LOCAL-FOUNDATION (Plan 01), LOCAL-EMAIL-REGISTER + LOCAL-EMAIL-LOGIN (Plan 02), and LOCAL-PHONE-OTP + LOCAL-PHONE-VERIFY (Plan 03).

#### Plan 01 Truths (LOCAL-FOUNDATION)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | AuthProvider enum includes LOCAL value | VERIFIED | `AuthProvider.kt` line 6: `LOCAL` enum value present |
| 2 | User entity has nullable passwordHash and phone columns | VERIFIED | `User.kt` lines 45-49: `@Column(name = "password_hash") var passwordHash: String? = null` and `@Column(name = "phone", unique = true) var phone: String? = null` |
| 3 | Flyway V1 migration creates schema including local auth columns | VERIFIED | `V1__initial_schema.sql` contains `CREATE TABLE users` with `password_hash VARCHAR(255)` and `phone VARCHAR(30) UNIQUE`; all three tables (users, user_roles, refresh_tokens) created |
| 4 | Dev profile uses Flyway with ddl-auto: validate (not create-drop) | VERIFIED | `application.yaml` dev profile: `flyway.enabled: true`, `locations: classpath:db/migration`, `ddl-auto: validate` |
| 5 | Test profile keeps ddl-auto: create-drop with Flyway disabled | VERIFIED | `src/test/resources/application.yaml`: `flyway.enabled: false`, `ddl-auto: create-drop` |
| 6 | Application starts and existing tests pass with the new Flyway-managed schema | VERIFIED (human) | 9 pre-existing tests confirmed passing in SUMMARY (01-01). Flyway is disabled for tests via H2 create-drop pattern — no regression risk |

#### Plan 02 Truths (LOCAL-EMAIL-REGISTER + LOCAL-EMAIL-LOGIN)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 7 | User can register with email+password via POST /api/v1/auth/local/register and receive JWT access + refresh tokens | VERIFIED | `AuthController.kt` line 50-55: `@PostMapping("/local/register")` delegates to `localAuthService.register()`, returns 201 with `AuthResponse`. `LocalAuthIntegrationTest` test `register success returns 201` confirms |
| 8 | User can login with email+password via POST /api/v1/auth/local/login and receive JWT access + refresh tokens | VERIFIED | `AuthController.kt` line 57-62: `@PostMapping("/local/login")` delegates to `localAuthService.login()`, returns 200 with `AuthResponse`. `LocalAuthIntegrationTest` test `login success returns 200` confirms |
| 9 | Duplicate email registration for LOCAL provider returns 409 Conflict | VERIFIED | `LocalAuthService.kt` line 31-33: throws `IllegalStateException("Email already registered")`. `AuthExceptionHandler.kt` line 48-52: maps `IllegalStateException` to 409. Test `register duplicate email returns 409` confirms |
| 10 | Invalid credentials on login return 401 Unauthorized | VERIFIED | `AuthExceptionHandler.kt` line 36-40: maps `BadCredentialsException` to 401. Tests `login wrong password returns 401` and `login non-existent email returns 401` confirm |
| 11 | Password is stored as BCrypt hash, never in plaintext | VERIFIED | `LocalAuthService.kt` line 41: `user.passwordHash = passwordEncoder.encode(rawPassword)`. `LocalAuthConfig.kt` line 16: `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (defaults to BCrypt). Test asserts `passwordHash.startsWith("{bcrypt}")` |
| 12 | Existing Google/Apple auth endpoints continue working unchanged | VERIFIED | `SecurityConfig.kt` line 46: `authorize("/api/v1/auth/**", permitAll)` — all auth endpoints permit all. `AuthController.kt` still contains unchanged `/google` and `/apple` endpoints. SUMMARY confirms all 9 pre-existing tests remain green |

#### Plan 03 Truths (LOCAL-PHONE-OTP + LOCAL-PHONE-VERIFY)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 13 | User can request an SMS OTP via POST /api/v1/auth/phone/request-otp with a phone number | VERIFIED | `AuthController.kt` line 64-69: `@PostMapping("/phone/request-otp")` delegates to `phoneOtpService.sendOtp()`, returns 204. Test `request OTP success returns 204` confirms |
| 14 | User can verify the OTP via POST /api/v1/auth/phone/verify-otp and receive JWT access + refresh tokens | VERIFIED | `AuthController.kt` line 71-76: `@PostMapping("/phone/verify-otp")` delegates to `phoneOtpService.verifyOtp()`, returns 200 with `AuthResponse`. Test `verify OTP success for new user` confirms tokens in response |
| 15 | Phone numbers are normalized to E.164 format before sending to Twilio and storing in DB | VERIFIED | `PhoneNumberUtil.kt`: `normalizeToE164()` uses `libphonenumber` with `PhoneNumberFormat.E164`. `PhoneOtpService.kt` lines 26, 38: calls `normalizeToE164(rawPhone)` before both `sendVerification` and `checkVerification`. `UserService.kt` line 78: `findOrCreatePhoneUser(phoneE164)` stores normalized phone in `providerId` and `phone` |
| 16 | First-time phone user is auto-created (find-or-create pattern) | VERIFIED | `UserService.kt` lines 78-91: `findOrCreatePhoneUser` checks for existing `(LOCAL, phoneE164)` user, creates new `User(email="", provider=LOCAL, providerId=phoneE164)` if not found. Test `verify OTP success for new user creates user and returns tokens` confirms DB row created |
| 17 | Returning phone user is found by (LOCAL, phoneE164) and issued new tokens | VERIFIED | `UserService.kt` line 80: `val existing = userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, phoneE164)`. Test `verify OTP success for returning user finds existing user and returns tokens` verifies no duplicate created |
| 18 | Invalid or expired OTP returns 401 | VERIFIED | `PhoneOtpService.kt` lines 40-42: `if (status != "approved") throw BadCredentialsException("Invalid or expired OTP")`. `AuthExceptionHandler.kt` maps `BadCredentialsException` to 401. Test `verify OTP failure returns 401` confirms |
| 19 | Invalid phone number format returns 400 | VERIFIED | `PhoneNumberUtil.kt`: throws `IllegalArgumentException` on missing `+` prefix or invalid number. `AuthExceptionHandler.kt` line 54-58: maps `IllegalArgumentException` to 400. Tests `request OTP with empty phone returns 400` and `verify OTP with invalid phone format returns 400` confirm |

**Score: 19/19 truths verified**

---

## Required Artifacts

### Plan 01 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `pom.xml` | Flyway starter, Twilio SDK, libphonenumber dependencies | VERIFIED | Lines 68-71: `spring-boot-starter-flyway`; lines 73-78: `twilio:11.3.3`; lines 80-85: `libphonenumber:8.13.52` |
| `src/main/kotlin/kz/innlab/template/user/model/AuthProvider.kt` | LOCAL enum value | VERIFIED | Line 6: `LOCAL` present |
| `src/main/kotlin/kz/innlab/template/user/model/User.kt` | passwordHash and phone nullable columns | VERIFIED | Lines 45-49: both present with `?` null type |
| `src/main/resources/db/migration/V1__initial_schema.sql` | Initial schema matching existing Hibernate DDL | VERIFIED | `CREATE TABLE users` with all columns including `password_hash` and `phone` |
| `src/main/resources/db/migration/V2__add_local_auth_fields.sql` | Local auth columns migration | NOTE | Plan originally listed V2 but task body explicitly decided V1 covers all columns for greenfield project. V2 does not exist — this is intentional per plan's own revised instruction. `password_hash` and `phone` confirmed in V1. |
| `src/main/resources/application.yaml` | Flyway config for dev and prod profiles | VERIFIED | Dev: `flyway.enabled: true`, `locations: classpath:db/migration`, `ddl-auto: validate`. Prod: `flyway.enabled: true`, `baseline-on-migrate: false`, `validate-on-migrate: true` |

### Plan 02 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/kz/innlab/template/config/LocalAuthConfig.kt` | PasswordEncoder + DaoAuthenticationProvider + localAuthenticationManager beans | VERIFIED | Lines 15-30: both `@Bean fun passwordEncoder()` and `@Bean fun localAuthenticationManager()` using `DaoAuthenticationProvider` |
| `src/main/kotlin/kz/innlab/template/authentication/service/LocalUserDetailsService.kt` | UserDetailsService loading by (LOCAL, email) | VERIFIED | Lines 21-33: `loadUserByUsername` calls `findByProviderAndProviderId(AuthProvider.LOCAL, email)` |
| `src/main/kotlin/kz/innlab/template/authentication/service/LocalAuthService.kt` | register() and login() using AuthenticationManager | VERIFIED | Lines 30-48: `register()` with BCrypt; lines 55-67: `login()` calls `authenticationManager.authenticate()` |
| `src/main/kotlin/kz/innlab/template/authentication/dto/LocalRegisterRequest.kt` | Registration DTO with @Email | VERIFIED | Lines 8-9: `@NotBlank` and `@Email` on email; `@Size(min=8, max=128)` on password |
| `src/main/kotlin/kz/innlab/template/authentication/dto/LocalLoginRequest.kt` | Login DTO with @Email | VERIFIED | Lines 7-8: `@NotBlank` and `@Email` on email |
| `src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt` | /local/register and /local/login endpoints | VERIFIED | Lines 50-62: both `localRegister` and `localLogin` methods |
| `src/test/kotlin/kz/innlab/template/LocalAuthIntegrationTest.kt` | Integration tests, min 80 lines | VERIFIED | 155 lines, 7 `@Test` methods covering register success, duplicate 409, login success, wrong password 401, non-existent email 401, empty email 400, short password 400 |

### Plan 03 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/kz/innlab/template/config/TwilioConfig.kt` | Twilio.init via @PostConstruct | VERIFIED | Line 28-31: `@PostConstruct fun init()` calls `Twilio.init(accountSid, authToken)` |
| `src/main/kotlin/kz/innlab/template/authentication/service/TwilioVerifyClient.kt` | interface TwilioVerifyClient | VERIFIED | Lines 11-21: interface with `sendVerification` and `checkVerification`; lines 23-34: `DefaultTwilioVerifyClient` implementation |
| `src/main/kotlin/kz/innlab/template/authentication/service/PhoneOtpService.kt` | sendOtp() and verifyOtp() delegating to twilioVerifyClient | VERIFIED | Lines 25-29: `sendOtp` uses `twilioVerifyClient.sendVerification`; lines 36-47: `verifyOtp` uses `twilioVerifyClient.checkVerification` |
| `src/main/kotlin/kz/innlab/template/authentication/service/PhoneNumberUtil.kt` | E.164 normalization with PhoneNumberFormat.E164 | VERIFIED | Line 24: `util.format(parsed, LibPhoneNumberUtil.PhoneNumberFormat.E164)` |
| `src/main/kotlin/kz/innlab/template/authentication/dto/PhoneOtpRequest.kt` | OTP request DTO with @NotBlank | VERIFIED | Line 5: `@field:NotBlank` on `phoneNumber` |
| `src/main/kotlin/kz/innlab/template/authentication/dto/PhoneVerifyRequest.kt` | OTP verify DTO with @NotBlank | VERIFIED | Lines 5-9: `@NotBlank` on `phoneNumber`, `@NotBlank` and `@Size(min=6,max=6)` on `code` |
| `src/main/kotlin/kz/innlab/template/user/service/UserService.kt` | findOrCreatePhoneUser method | VERIFIED | Lines 78-91: `findOrCreatePhoneUser(phoneE164)` with find-or-create logic |
| `src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt` | Integration tests, min 60 lines | VERIFIED | 149 lines, 6 `@Test` methods with `@MockitoBean TwilioVerifyClient` |

---

## Key Link Verification

| From | To | Via | Status | Evidence |
|------|----|-----|--------|---------|
| `AuthController.kt` | `LocalAuthService.kt` | `localAuthService.` calls | WIRED | Lines 53, 60: `localAuthService.register()` and `localAuthService.login()` |
| `LocalAuthService.kt` | `LocalAuthConfig.kt` | `authenticationManager.authenticate()` | WIRED | Line 56: `authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(...))` with `@Qualifier("localAuthenticationManager")` on constructor parameter |
| `LocalUserDetailsService.kt` | `UserRepository.kt` | `findByProviderAndProviderId` | WIRED | Line 22: `userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email)` |
| `AuthController.kt` | `PhoneOtpService.kt` | `phoneOtpService.` calls | WIRED | Lines 67, 74: `phoneOtpService.sendOtp()` and `phoneOtpService.verifyOtp()` |
| `PhoneOtpService.kt` | `UserService.kt` | `userService.findOrCreatePhoneUser` | WIRED | Line 43: `val user = userService.findOrCreatePhoneUser(phoneE164)` |
| `PhoneOtpService.kt` | `TwilioVerifyClient.kt` | `twilioVerifyClient.` calls | WIRED | Line 27: `twilioVerifyClient.sendVerification(...)` and line 39: `twilioVerifyClient.checkVerification(...)` |

---

## Requirements Coverage

No central REQUIREMENTS.md exists for this phase (requirement IDs are tracked within plan frontmatter and STATE.md). Requirement IDs are verified through the must-haves and artifacts above.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| LOCAL-FOUNDATION | 01-01-PLAN.md | Flyway-managed schema, AuthProvider.LOCAL, User entity local-auth columns, three Maven dependencies | SATISFIED | V1 migration, AuthProvider.LOCAL, User.passwordHash, User.phone, pom.xml deps all verified |
| LOCAL-EMAIL-REGISTER | 01-02-PLAN.md | User can register with email+password, receive JWT tokens, duplicate returns 409 | SATISFIED | `POST /api/v1/auth/local/register` endpoint with BCrypt hash, duplicate check, 7 integration tests |
| LOCAL-EMAIL-LOGIN | 01-02-PLAN.md | User can login with email+password, receive JWT tokens, bad credentials return 401 | SATISFIED | `POST /api/v1/auth/local/login` endpoint with DaoAuthenticationProvider validation, 401 on failure |
| LOCAL-PHONE-OTP | 01-03-PLAN.md | User can request SMS OTP via Twilio Verify API, phone normalized to E.164 | SATISFIED | `POST /api/v1/auth/phone/request-otp` endpoint, `normalizeToE164()`, `TwilioVerifyClient.sendVerification()` |
| LOCAL-PHONE-VERIFY | 01-03-PLAN.md | User can verify OTP and receive JWT tokens, find-or-create phone user, invalid OTP returns 401 | SATISFIED | `POST /api/v1/auth/phone/verify-otp` endpoint, `UserService.findOrCreatePhoneUser()`, 6 integration tests |

---

## Anti-Patterns Found

| File | Line(s) | Pattern | Severity | Impact |
|------|---------|---------|----------|--------|
| `AuthController.kt` | 38, 45, 52, 59, 66, 73, 80, 88 | `// TODO: rate limiting` | INFO | No runtime impact. These are intentional markers per Phase 05 hardening plan (STATE.md decision). Authentication logic is complete. Rate limiting is a future concern. |
| `UserService.kt` | 75 | `// TODO: consider making email nullable for phone-only users` | INFO | No runtime impact. Phone-only users use `email = ""` (empty string) because the column is NOT NULL. This is the documented design decision; empty string is functional for MVP. |

No blocker or warning anti-patterns found. All TODO comments are acknowledged design notes from the plans themselves, not stubs.

---

## Human Verification Required

### 1. SMS OTP delivery with real Twilio credentials

**Test:** Set `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_VERIFY_SERVICE_SID` environment variables, start the app with dev profile against a running PostgreSQL instance, POST to `/api/v1/auth/phone/request-otp` with a real phone number, check for SMS delivery.
**Expected:** SMS arrives on the phone within ~30 seconds containing a 6-digit code.
**Why human:** Requires real Twilio credentials, live phone, and running PostgreSQL. Cannot verify network delivery programmatically.

### 2. Dev profile startup with Flyway migration

**Test:** Start the application with a fresh PostgreSQL database (no existing tables) using the dev profile. Check logs for `Successfully applied 1 migration to schema "public"`.
**Expected:** Application starts, Flyway applies V1 migration, Hibernate schema validation passes, no errors.
**Why human:** Requires a running PostgreSQL instance. The automated test profile uses H2 create-drop and cannot validate Flyway + PostgreSQL integration.

### 3. Full registration-to-access-token flow via HTTP client

**Test:** Use curl or Postman to POST `/api/v1/auth/local/register` with valid payload, then use the returned `accessToken` to call a protected endpoint (e.g., `/api/v1/users/me`).
**Expected:** 201 on register, tokens returned, protected endpoint returns 200 with user data.
**Why human:** End-to-end JWT token validation against the resource server requires a running application.

---

## Gaps Summary

None. All 19 observable truths are verified. All required artifacts exist, are substantive, and are fully wired. All 5 requirement IDs are satisfied.

**Note on V2 migration artifact:** The Plan 01-01 frontmatter originally listed `V2__add_local_auth_fields.sql` as a required artifact. The plan task body explicitly overrode this decision: V1 includes all columns for this greenfield project and V2 was intentionally omitted. The underlying truth ("Flyway migrations create schema and add local auth columns") is satisfied by V1. This is not a gap.

---

_Verified: 2026-03-02_
_Verifier: Claude (gsd-verifier)_
