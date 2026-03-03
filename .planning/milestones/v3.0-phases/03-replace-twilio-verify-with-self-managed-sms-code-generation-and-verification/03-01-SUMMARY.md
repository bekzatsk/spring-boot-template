---
phase: 03-replace-twilio-verify-with-self-managed-sms-code-generation-and-verification
plan: 01
subsystem: auth
tags: [sms, otp, bcrypt, securerandom, spring-scheduling, flyway, jpa]

requires:
  - phase: 01-add-local-authentication-email-password-and-phone-sms-code-login
    provides: PhoneOtpService, passwordEncoder bean (DelegatingPasswordEncoder/BCrypt), TwilioVerifyClient interface, RefreshToken entity pattern

provides:
  - SmsVerification JPA entity with phone/codeHash/expiresAt/used/attempts fields
  - SmsVerificationRepository with JPQL queries for rate limit check, active code lookup, bulk delete
  - SmsService interface and ConsoleSmsService (default dev/test SMS implementation)
  - SmsVerificationService — SecureRandom code generation, BCrypt hashing, rate limiting, attempt tracking
  - SmsSchedulerConfig — @EnableScheduling + 10-minute cleanup job + @Bean @ConditionalOnMissingBean(SmsService)
  - V3 Flyway migration for sms_verifications table
  - Refactored PhoneOtpService delegating to SmsVerificationService (Twilio entirely removed)

affects:
  - phone OTP endpoints (/phone/request and /phone/verify)
  - future SMS provider integration (implement SmsService, @Bean overrides ConsoleSmsService automatically)

tech-stack:
  added: []
  patterns:
    - ConsoleSmsService registered via @Bean @ConditionalOnMissingBean(SmsService) in SmsSchedulerConfig — no @Component; future SMS provider just defines its own SmsService @Bean
    - SmsVerification entity mirrors RefreshToken pattern (UUID @GeneratedValue, manual equals/hashCode on id, @CreationTimestamp)
    - JPQL bulk DELETE via @Modifying @Transactional — same pattern as RefreshTokenRepository.deleteAllByUser (no N+1)
    - Pre-seed H2 with passwordEncoder.encode(knownCode) in tests; verify path exercises real BCrypt + H2 (not mocked)

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/authentication/model/SmsVerification.kt
    - src/main/kotlin/kz/innlab/template/authentication/repository/SmsVerificationRepository.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/SmsService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/ConsoleSmsService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt
    - src/main/kotlin/kz/innlab/template/config/SmsSchedulerConfig.kt
    - src/main/resources/db/migration/V3__add_sms_verifications.sql
  modified:
    - src/main/kotlin/kz/innlab/template/authentication/service/PhoneOtpService.kt
    - src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/PhoneOtpRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/PhoneVerifyRequest.kt
    - src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt
    - pom.xml
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
  deleted:
    - src/main/kotlin/kz/innlab/template/authentication/service/TwilioVerifyClient.kt
    - src/main/kotlin/kz/innlab/template/config/TwilioConfig.kt

key-decisions:
  - "ConsoleSmsService has NO @Component — registered conditionally via @Bean @ConditionalOnMissingBean(SmsService::class) in SmsSchedulerConfig; allows future real SMS provider to just define its own @Bean SmsService"
  - "SmsVerificationService.sendCode uses !! on passwordEncoder.encode() — Spring Security PasswordEncoder.encode() is a Java method that Kotlin infers as String? even though it never returns null; !! is correct here"
  - "Tests pre-seed H2 with passwordEncoder.encode(knownCode)!! and verify with the known plaintext code — BCrypt hash in H2 is opaque so seeding with known code is the right test approach"
  - "Endpoint paths renamed /phone/request-otp -> /phone/request and /phone/verify-otp -> /phone/verify (cleaner REST naming per research)"
  - "DTO fields renamed phoneNumber -> phone (consistent with User.phone field)"
  - "Rate limit IllegalStateException propagates as 409 Conflict via existing AuthExceptionHandler — consistent with TokenGracePeriodException pattern; TODO comment left for dedicated 429"
  - "mvnw clean required after deleting Kotlin source files — incremental compile leaves stale .class files that cause BeanCreationException at test startup"

requirements-completed: [SMS-01, SMS-02, SMS-03, SMS-04, SMS-05, SMS-06, SMS-08]

duration: 12min
completed: 2026-03-02
---

# Phase 03 Plan 01: Replace Twilio Verify with Self-Managed SMS OTP Summary

**Self-managed SMS OTP with SecureRandom 6-digit codes, BCrypt hash storage in sms_verifications table, rate limiting (60s), attempt tracking (max 3), 5-minute expiry, and scheduled cleanup — Twilio completely removed.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-03-02T03:20:28Z
- **Completed:** 2026-03-02T03:32:28Z
- **Tasks:** 1
- **Files modified:** 17 (7 created, 8 modified, 2 deleted)

## Accomplishments

- Replaced Twilio Verify with local OTP generation (SecureRandom), BCrypt hashing (existing passwordEncoder bean), and DB-backed verification (sms_verifications table)
- Implemented SmsService interface + ConsoleSmsService (no @Component — conditionally registered via @Bean @ConditionalOnMissingBean) enabling seamless real SMS provider swap-in
- Removed Twilio SDK, TwilioConfig, TwilioVerifyClient, and all Twilio config entirely — zero Twilio references remain
- Updated PhoneAuthIntegrationTest to pre-seed H2 with known BCrypt hashes, exercising the real verification path against H2

## Task Commits

Each task was committed atomically:

1. **Task 1: Create SMS verification infrastructure, refactor PhoneOtpService, and remove all Twilio artifacts** - `246d964` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

### Created
- `src/main/kotlin/kz/innlab/template/authentication/model/SmsVerification.kt` — JPA entity: phone, codeHash, expiresAt, used, attempts
- `src/main/kotlin/kz/innlab/template/authentication/repository/SmsVerificationRepository.kt` — JPQL queries: findActiveByPhone (attempts < 3), deleteAllByPhone, deleteExpiredOrUsed, existsByPhoneAndCreatedAtAfter
- `src/main/kotlin/kz/innlab/template/authentication/service/SmsService.kt` — interface: sendCode(phone, code)
- `src/main/kotlin/kz/innlab/template/authentication/service/ConsoleSmsService.kt` — dev/test SMS that logs to stdout (no @Component)
- `src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt` — SecureRandom code gen, BCrypt hash, rate limit, attempt tracking, sendCode + verifyCode
- `src/main/kotlin/kz/innlab/template/config/SmsSchedulerConfig.kt` — @EnableScheduling + @Scheduled cleanup + @Bean @ConditionalOnMissingBean(SmsService) factory
- `src/main/resources/db/migration/V3__add_sms_verifications.sql` — sms_verifications DDL with phone index

### Modified
- `src/main/kotlin/kz/innlab/template/authentication/service/PhoneOtpService.kt` — delegates to SmsVerificationService; removed Twilio imports
- `src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt` — renamed endpoints /phone/request-otp -> /phone/request and /phone/verify-otp -> /phone/verify; phoneNumber -> phone field
- `src/main/kotlin/kz/innlab/template/authentication/dto/PhoneOtpRequest.kt` — renamed phoneNumber -> phone
- `src/main/kotlin/kz/innlab/template/authentication/dto/PhoneVerifyRequest.kt` — renamed phoneNumber -> phone
- `src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt` — @MockitoBean SmsService (not TwilioVerifyClient); pre-seed H2; updated endpoints and field names
- `pom.xml` — removed com.twilio.sdk:twilio:11.3.3 dependency
- `src/main/resources/application.yaml` — removed app.auth.twilio.* section
- `src/test/resources/application.yaml` — removed app.auth.twilio.* section

### Deleted
- `src/main/kotlin/kz/innlab/template/authentication/service/TwilioVerifyClient.kt`
- `src/main/kotlin/kz/innlab/template/config/TwilioConfig.kt`

## Decisions Made

- ConsoleSmsService has no @Component — registered conditionally via @Bean @ConditionalOnMissingBean(SmsService::class) in SmsSchedulerConfig; future real SMS provider defines its own @Bean SmsService and ConsoleSmsService is automatically bypassed
- PasswordEncoder.encode() is a Java method inferred as String? in Kotlin; !! operator used since it never returns null
- Tests pre-seed H2 with known code hash (passwordEncoder.encode(knownCode)!!) to exercise real BCrypt verification path without capturing random codes
- Endpoint paths and DTO field names updated: /phone/request-otp -> /phone/request, /phone/verify-otp -> /phone/verify, phoneNumber -> phone
- Rate limit uses IllegalStateException (-> 409 Conflict) consistent with existing AuthExceptionHandler pattern; TODO comment left for dedicated 429 response
- mvnw clean required after deleting Kotlin source files — incremental compile leaves stale .class files in target/ causing BeanCreationException

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added !! operator on passwordEncoder.encode() calls**
- **Found during:** Task 1 (initial compile)
- **Issue:** Spring Security's PasswordEncoder.encode() is a Java method that Kotlin infers as String?; SmsVerification constructor requires non-null String for codeHash parameter
- **Fix:** Added !! (non-null assertion) on all passwordEncoder.encode() calls in SmsVerificationService and PhoneAuthIntegrationTest
- **Files modified:** SmsVerificationService.kt, PhoneAuthIntegrationTest.kt
- **Verification:** ./mvnw compile passes cleanly
- **Committed in:** 246d964 (part of task commit)

**2. [Rule 3 - Blocking] Updated PhoneAuthIntegrationTest, PhoneOtpRequest, PhoneVerifyRequest, AuthController (endpoint rename + field rename)**
- **Found during:** Task 1 (test compilation — TwilioVerifyClient no longer exists)
- **Issue:** PhoneAuthIntegrationTest imported deleted TwilioVerifyClient; tests used old endpoint paths and phoneNumber field; AuthController referenced request.phoneNumber
- **Fix:** Updated test to @MockitoBean SmsService, pre-seed H2 with known hashes, updated endpoint paths (/phone/request, /phone/verify) and DTO field (phone); renamed DTO fields and controller references
- **Files modified:** PhoneAuthIntegrationTest.kt, AuthController.kt, PhoneOtpRequest.kt, PhoneVerifyRequest.kt
- **Verification:** All 22 tests pass
- **Committed in:** 246d964 (part of task commit)

**3. [Rule 3 - Blocking] Clean build required (mvnw clean) to purge stale TwilioConfig.class**
- **Found during:** Task 1 (test execution after compile succeeded)
- **Issue:** Incremental compile left `target/classes/kz/innlab/template/config/TwilioConfig.class` — context tried to create `twilioConfig` bean, which injected ${app.auth.twilio.account-sid} (removed from YAML), causing PlaceholderResolutionException
- **Fix:** Ran ./mvnw clean test to purge compiled artifacts before testing
- **Files modified:** None (build process fix)
- **Verification:** All 22 tests pass after clean build
- **Committed in:** N/A (build process, not code)

---

**Total deviations:** 3 auto-fixed (all Rule 3 — blocking issues)
**Impact on plan:** All fixes necessary for compilation and test execution. No scope creep beyond what the plan's research document specified.

## Issues Encountered

- Stale TwilioConfig.class in target/ after deleting TwilioConfig.kt caused all tests to fail with BeanCreationException. Resolved by running `./mvnw clean test` — this is a known pitfall documented in the research as Pitfall 1.

## User Setup Required

None — no external service configuration required. ConsoleSmsService logs OTP codes to stdout in dev/test mode.

## Next Phase Readiness

- Self-managed SMS OTP infrastructure fully operational; zero Twilio dependencies remain
- Future real SMS provider: implement SmsService interface, declare as @Bean — ConsoleSmsService is automatically bypassed
- All 22 tests pass (including refactored PhoneAuthIntegrationTest with H2-backed verification)

---
*Phase: 03-replace-twilio-verify-with-self-managed-sms-code-generation-and-verification*
*Completed: 2026-03-02*
