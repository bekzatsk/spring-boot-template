---
phase: quick-4
plan: 1
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/kotlin/kz/innlab/template/config/RsaKeyConfig.kt
autonomous: true
must_haves:
  truths:
    - "Application starts successfully in dev profile (no keystore configured)"
    - "rsaKeyPair bean generates in-memory RSA keypair when keystore properties are absent"
    - "rsaKeyPair bean loads from PKCS12 keystore when keystore properties are present"
  artifacts:
    - path: "src/main/kotlin/kz/innlab/template/config/RsaKeyConfig.kt"
      provides: "RSA keypair bean with correct conditional logic"
      contains: "!keystoreLocation.isNullOrBlank"
  key_links:
    - from: "RsaKeyConfig.rsaKeyPair()"
      to: "jwkSource -> jwtEncoder -> tokenService"
      via: "Spring bean dependency injection"
      pattern: "fun rsaKeyPair"
---

<objective>
Fix inverted conditional in RsaKeyConfig.rsaKeyPair() that causes NullPointerException on startup.

Purpose: The condition on line 37 checks `isNullOrBlank()` and then immediately force-unwraps `keystoreLocation!!` inside that branch, causing NPE. The if/else branches are swapped — keystore loading runs when values are null, and in-memory generation runs when values are present.

Output: Working RsaKeyConfig that generates in-memory keypair in dev and loads from keystore in prod.
</objective>

<execution_context>
@./.claude/get-shit-done/workflows/execute-plan.md
@./.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@src/main/kotlin/kz/innlab/template/config/RsaKeyConfig.kt
@src/main/resources/application.yaml

<interfaces>
From src/main/kotlin/kz/innlab/template/config/RsaKeyConfig.kt:
```kotlin
// Current BUGGY condition (line 37):
if (keystoreLocation.isNullOrBlank() && keystorePassword.isNullOrBlank()) {
    // BUG: tries to load keystore when values are null
    val resource = ClassPathResource(keystoreLocation!!)  // NPE here
    ...
}
// Falls through to in-memory generation when values ARE present
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Fix inverted conditional in rsaKeyPair() factory method</name>
  <files>src/main/kotlin/kz/innlab/template/config/RsaKeyConfig.kt</files>
  <action>
In RsaKeyConfig.kt, fix the rsaKeyPair() method by inverting the conditional logic:

1. Change line 37 from:
   `if (keystoreLocation.isNullOrBlank() && keystorePassword.isNullOrBlank())`
   to:
   `if (!keystoreLocation.isNullOrBlank() && !keystorePassword.isNullOrBlank())`

2. This single change fixes the logic so that:
   - When keystore properties ARE configured (prod) -> loads from PKCS12 keystore
   - When keystore properties are null/blank (dev) -> generates in-memory RSA keypair

3. No other changes needed. The log messages, the keystore loading code, and the in-memory generation code are all correct — only the branching condition was inverted.

Root cause: The original code entered the keystore-loading branch when both values were null/blank, then crashed on `keystoreLocation!!` (force-unwrap of null). The "message: null" in the error is the NPE message.
  </action>
  <verify>
    <automated>cd /Users/bekzat/Workspace/Template/spring-boot-template && ./mvnw clean test -q 2>&1 | tail -20</automated>
  </verify>
  <done>rsaKeyPair() condition is `!isNullOrBlank` (not `isNullOrBlank`), all tests pass, application context loads without bean creation failure.</done>
</task>

</tasks>

<verification>
- `./mvnw clean test` passes (application context initializes, rsaKeyPair bean created successfully)
- RsaKeyConfig.kt line 37 reads `if (!keystoreLocation.isNullOrBlank() && !keystorePassword.isNullOrBlank())`
</verification>

<success_criteria>
Application starts without "Factory method 'rsaKeyPair' threw exception with message: null" error. All existing tests pass. The fix is a single-character change (adding `!` negation to both conditions).
</success_criteria>

<output>
After completion, create `.planning/quick/4-fix-rsakeypair-bean-creation-failure-in-/4-SUMMARY.md`
</output>
