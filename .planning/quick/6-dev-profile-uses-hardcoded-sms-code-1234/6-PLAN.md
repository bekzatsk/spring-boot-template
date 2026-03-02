---
phase: quick
plan: 6
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt
  - src/main/resources/application.yaml
autonomous: true
requirements: []
must_haves:
  truths:
    - "Dev profile OTP code is always 123456"
    - "Prod profile OTP code is SecureRandom-generated 6 digits"
    - "Test profile behavior unchanged — existing 23 tests pass"
  artifacts:
    - path: "src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt"
      provides: "Conditional code generation — uses dev-code property when set, SecureRandom otherwise"
      contains: "devCode"
    - path: "src/main/resources/application.yaml"
      provides: "app.auth.sms.dev-code=123456 under dev profile"
      contains: "dev-code"
  key_links:
    - from: "application.yaml (dev profile)"
      to: "SmsVerificationService.devCode"
      via: "@Value injection"
      pattern: "app\\.auth\\.sms\\.dev-code"
---

<objective>
When spring.profiles.active=dev, the SMS OTP code should always be "123456" instead of a SecureRandom-generated 6-digit code.

Purpose: Simplifies local phone auth testing — developer knows the code without reading console logs.
Output: Modified SmsVerificationService with config-driven dev code, updated application.yaml with dev-profile property.
</objective>

<execution_context>
@./.claude/get-shit-done/workflows/execute-plan.md
@./.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt
@src/main/resources/application.yaml
@src/test/resources/application.yaml

<interfaces>
From SmsVerificationService.kt:
- `sendCode(phoneE164: String): UUID` — generates code, hashes with BCrypt, saves SmsVerification, sends via SmsService
- `verifyCode(verificationId: UUID, phoneE164: String, code: String): Boolean` — BCrypt-matches input against stored hash
- Code generation: `String.format("%06d", random.nextInt(CODE_BOUND))` on line 33
- Uses `PasswordEncoder` for BCrypt hash — dev code still gets hashed the same way

From application.yaml:
- Dev profile section starts at line 30 (`on-profile: dev`)
- Existing `app.auth` namespace used for google, apple, refresh-token configs
- Pattern for dev defaults: `${ENV_VAR:default}` with `:default` suffix

From test application.yaml:
- No `app.auth.sms` section — tests mock SmsService via @MockitoBean
- Test profile does NOT set `app.auth.sms.dev-code` — tests exercise real SecureRandom path
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add dev-code config property and conditional code generation</name>
  <files>
    src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt,
    src/main/resources/application.yaml
  </files>
  <action>
1. In `SmsVerificationService.kt`:
   - Add constructor parameter: `@Value("\${app.auth.sms.dev-code:}") private val devCode: String = ""`
   - The `:` with empty default means the property resolves to empty string when not set (dev profile sets it, prod/test do not)
   - Replace line 33 (`val code = String.format(...)`) with conditional logic:
     ```kotlin
     val code = if (devCode.isNotBlank()) devCode else String.format("%06d", random.nextInt(CODE_BOUND))
     ```
   - Remove the `println(code)` on line 34 — it was a debug leftover; dev profile now has a known code, and ConsoleSmsService already logs the code via SLF4J

2. In `application.yaml`:
   - Under the dev profile section (after line 44, inside the `on-profile: dev` document), add:
     ```yaml
     app:
       auth:
         sms:
           dev-code: "123456"
     ```
   - Do NOT add this property to the common section or prod section — only dev profile
   - Do NOT add anything to test application.yaml — tests use @MockitoBean for SmsService and capture the real generated code via doAnswer; the dev-code property is irrelevant to tests
  </action>
  <verify>
    <automated>cd /Users/bekzat/Workspace/Template/spring-boot-template && ./mvnw clean test -q 2>&1 | tail -5</automated>
  </verify>
  <done>
    - SmsVerificationService uses "123456" when app.auth.sms.dev-code is set (dev profile)
    - SmsVerificationService uses SecureRandom when property is blank/missing (prod, test profiles)
    - println(code) debug line removed
    - All existing tests pass (23 tests, test profile has no dev-code set)
  </done>
</task>

</tasks>

<verification>
- `./mvnw clean test` — all 23 tests pass
- `grep -n "devCode\|dev-code" src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt` shows @Value injection and conditional
- `grep -A2 "dev-code" src/main/resources/application.yaml` shows "123456" under dev profile only
- `grep "dev-code" src/test/resources/application.yaml` returns nothing (test profile unaffected)
</verification>

<success_criteria>
- Dev profile: /phone/request always generates code "123456" (verifiable by reading ConsoleSmsService log output)
- Prod profile: /phone/request generates random 6-digit codes (SecureRandom, same as before)
- Test profile: unchanged behavior, all 23 tests pass
- No println debug line in SmsVerificationService
</success_criteria>

<output>
After completion, create `.planning/quick/6-dev-profile-uses-hardcoded-sms-code-1234/6-SUMMARY.md`
</output>
