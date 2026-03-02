---
phase: quick
plan: 5
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt
  - src/main/kotlin/kz/innlab/template/authentication/service/PhoneOtpService.kt
  - src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt
  - src/main/kotlin/kz/innlab/template/authentication/dto/PhoneVerifyRequest.kt
  - src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt
autonomous: true
requirements: []

must_haves:
  truths:
    - "POST /phone/request returns 200 with JSON containing verificationId UUID"
    - "POST /phone/verify requires verificationId in request body alongside phone and code"
    - "Verification lookup uses verificationId (not phone-based scan) to find the SmsVerification record"
    - "Phone mismatch between verificationId record and request body returns 401"
    - "All existing phone auth integration tests pass with the new flow"
  artifacts:
    - path: "src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt"
      provides: "sendCode returns UUID, verifyCode accepts UUID+phone+code"
    - path: "src/main/kotlin/kz/innlab/template/authentication/dto/PhoneVerifyRequest.kt"
      provides: "verificationId UUID field added"
    - path: "src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt"
      provides: "requestPhoneOtp returns 200 JSON with verificationId"
  key_links:
    - from: "SmsVerificationService.sendCode()"
      to: "SmsVerificationRepository.save()"
      via: "returns saved entity's UUID"
      pattern: "return.*\\.id"
    - from: "SmsVerificationService.verifyCode()"
      to: "SmsVerificationRepository.findById()"
      via: "lookup by UUID instead of findActiveByPhone"
      pattern: "findById"
    - from: "AuthController.requestPhoneOtp()"
      to: "PhoneOtpService.sendOtp()"
      via: "captures returned UUID, returns in JSON body"
      pattern: "verificationId"
---

<objective>
Bind OTP verification to a specific SmsVerification record by returning its UUID from the request endpoint and requiring it in the verify endpoint.

Purpose: Prevent brute-force attacks. Currently POST /phone/verify looks up the active record by phone number alone, meaning an attacker only needs a valid phone number. By requiring the verificationId UUID (a random 128-bit value), the attacker must know both the phone AND the specific record ID.

Output: Modified service/controller/DTO layer + updated integration tests.
</objective>

<execution_context>
@./.claude/get-shit-done/workflows/execute-plan.md
@./.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md

<interfaces>
<!-- Current contracts that will be modified -->

From SmsVerification.kt (entity — no changes needed):
```kotlin
@Entity
class SmsVerification(val phone: String, val codeHash: String, val expiresAt: Instant) {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null
    var used: Boolean = false
    var attempts: Int = 0
}
```

From SmsVerificationRepository.kt (findActiveByPhone will be replaced by findById):
```kotlin
interface SmsVerificationRepository : JpaRepository<SmsVerification, UUID> {
    fun findActiveByPhone(phone: String, now: Instant): SmsVerification?
    fun deleteAllByPhone(phone: String)
    fun deleteExpiredOrUsed(cutoff: Instant)
    fun existsByPhoneAndCreatedAtAfter(phone: String, since: Instant): Boolean
}
```

From SmsVerificationService.kt (current signatures):
```kotlin
fun sendCode(phoneE164: String): Unit  // -> will return UUID
fun verifyCode(phoneE164: String, code: String): Boolean  // -> will take (verificationId: UUID, phoneE164: String, code: String)
```

From PhoneOtpService.kt (current signatures):
```kotlin
fun sendOtp(rawPhone: String): Unit  // -> will return UUID
fun verifyOtp(rawPhone: String, code: String): AuthResponse  // -> will take (verificationId: UUID, rawPhone: String, code: String)
```

From PhoneVerifyRequest.kt (current DTO):
```kotlin
data class PhoneVerifyRequest(val phone: String, val code: String)
```

From AuthController.kt (current endpoints):
```kotlin
fun requestPhoneOtp(request: PhoneOtpRequest): ResponseEntity<Void>  // 204 -> will become 200 with JSON
fun verifyPhoneOtp(request: PhoneVerifyRequest): ResponseEntity<AuthResponse>
```
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Add verificationId to service layer and DTO</name>
  <files>
    src/main/kotlin/kz/innlab/template/authentication/service/SmsVerificationService.kt,
    src/main/kotlin/kz/innlab/template/authentication/service/PhoneOtpService.kt,
    src/main/kotlin/kz/innlab/template/authentication/dto/PhoneVerifyRequest.kt
  </files>
  <behavior>
    - sendCode(phoneE164) returns UUID of the saved SmsVerification record
    - verifyCode(verificationId, phoneE164, code) looks up by findById(verificationId), checks phone matches, checks not expired, checks not used, checks attempts < 3, increments attempts, validates BCrypt
    - verifyCode returns false if no record found by ID, phone mismatch, expired, used, or max attempts
    - PhoneOtpService.sendOtp returns UUID from SmsVerificationService
    - PhoneOtpService.verifyOtp accepts (verificationId, rawPhone, code)
    - PhoneVerifyRequest has verificationId: UUID field with @NotNull validation
  </behavior>
  <action>
1. **SmsVerificationService.sendCode()** — Change return type from Unit to UUID. After `smsVerificationRepository.save(...)`, capture the returned entity and return `entity.id!!`. The save() call already returns the persisted entity with generated UUID.

2. **SmsVerificationService.verifyCode()** — Change signature to `verifyCode(verificationId: UUID, phoneE164: String, code: String): Boolean`.
   - Replace `findActiveByPhone(phoneE164, Instant.now())` with `smsVerificationRepository.findById(verificationId).orElse(null) ?: return false`
   - After findById, check: `if (record.phone != phoneE164) return false` (phone mismatch = reject)
   - Keep existing checks: `if (record.used) return false`, `if (record.expiresAt <= Instant.now()) return false`, `if (record.attempts >= 3) return false`
   - Keep existing attempts increment + BCrypt match + mark used logic unchanged
   - NOTE: findActiveByPhone query in repository can remain (it is not called elsewhere but keeping it avoids unnecessary churn; it may be useful for cleanup jobs)

3. **PhoneOtpService.sendOtp()** — Change return type from Unit to UUID. Return `smsVerificationService.sendCode(phoneE164)`.

4. **PhoneOtpService.verifyOtp()** — Change signature to `verifyOtp(verificationId: UUID, rawPhone: String, code: String): AuthResponse`. Pass `verificationId` to `smsVerificationService.verifyCode(verificationId, phoneE164, code)`.

5. **PhoneVerifyRequest** — Add field:
```kotlin
@field:NotNull(message = "Verification ID is required")
val verificationId: UUID,
```
Import `jakarta.validation.constraints.NotNull` and `java.util.UUID`. Keep phone and code fields unchanged.
  </action>
  <verify>
    <automated>cd /Users/bekzat/Workspace/Template/spring-boot-template && ./mvnw compile -q 2>&1 | tail -5</automated>
  </verify>
  <done>SmsVerificationService.sendCode returns UUID, verifyCode takes (UUID, phone, code), PhoneOtpService signatures updated, PhoneVerifyRequest has verificationId field. Project compiles.</done>
</task>

<task type="auto">
  <name>Task 2: Update controller response and integration tests</name>
  <files>
    src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt,
    src/test/kotlin/kz/innlab/template/PhoneAuthIntegrationTest.kt
  </files>
  <action>
1. **AuthController.requestPhoneOtp()** — Change return type from `ResponseEntity<Void>` to `ResponseEntity<Map<String, Any>>`. Capture UUID from `phoneOtpService.sendOtp(request.phone)`. Return `ResponseEntity.ok(mapOf("verificationId" to verificationId))` instead of 204 no-content. The response JSON will be `{"verificationId": "uuid-string"}`.

2. **AuthController.verifyPhoneOtp()** — Update the service call to pass verificationId: `phoneOtpService.verifyOtp(request.verificationId, request.phone, request.code)`.

3. **PhoneAuthIntegrationTest — update ALL test methods:**

   a. **`request OTP success returns 204`** — Rename to `request OTP success returns 200 with verificationId`. Change expectation from `status().isNoContent` to `status().isOk` and add `.andExpect(jsonPath("$.verificationId").exists())`.

   b. **`verify OTP success for new user creates user and returns tokens`** — After the /phone/request call, change status expectation to `isOk`. Extract verificationId from the response using `.andReturn()` + Jackson ObjectMapper to parse `$.verificationId` from response body. In the /phone/verify call, include `"verificationId": "$verificationId"` in the JSON body.

   c. **`verify OTP success for returning user finds existing user and returns tokens`** — Same pattern: capture verificationId from /phone/request response, include in /phone/verify body.

   d. **`verify OTP failure with wrong code returns 401`** — Capture verificationId from /phone/request response (change to `isOk`), include in /phone/verify body with wrong code.

   e. **`request OTP with empty phone returns 400`** — No change needed (still expects 400).

   f. **`verify OTP with invalid phone format returns 400`** — Add a dummy verificationId UUID to the JSON body: `"verificationId": "${UUID.randomUUID()}"`. Import `java.util.UUID`.

   g. **`request OTP rate limited returns 409`** — Change first request expectation from `isNoContent` to `isOk`.

   **Helper for extracting verificationId from response:** Add a private helper method:
   ```kotlin
   private fun extractVerificationId(result: MvcResult): String {
       val body = result.response.contentAsString
       // Parse {"verificationId":"uuid"} — use simple regex or Jackson
       val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
       val tree = mapper.readTree(body)
       return tree.get("verificationId").asText()
   }
   ```
   Use `.andReturn()` on the /phone/request perform call to get MvcResult, then call this helper.

   **For tests that use captureCodeOnSend() + /phone/request:** Chain `.andExpect(status().isOk).andReturn()` (not just `.andExpect(status().isOk)`), then extract verificationId from the returned MvcResult.
  </action>
  <verify>
    <automated>cd /Users/bekzat/Workspace/Template/spring-boot-template && ./mvnw test -Dtest=PhoneAuthIntegrationTest -q 2>&1 | tail -10</automated>
  </verify>
  <done>POST /phone/request returns 200 with verificationId JSON. POST /phone/verify requires verificationId in body. All 7 PhoneAuthIntegrationTest tests pass with the new flow.</done>
</task>

</tasks>

<verification>
Run the full test suite to ensure no regressions:
```bash
./mvnw test -q
```
All tests pass (currently 23 tests across all test classes).
</verification>

<success_criteria>
- POST /api/v1/auth/phone/request returns HTTP 200 with `{"verificationId": "uuid"}` (not 204)
- POST /api/v1/auth/phone/verify requires `verificationId` UUID in request body
- SmsVerification lookup uses findById(verificationId) + phone match (not findActiveByPhone)
- All 7 PhoneAuthIntegrationTest tests pass
- Full test suite (23 tests) passes with no regressions
</success_criteria>

<output>
After completion, create `.planning/quick/5-add-smsverification-id-to-phone-otp-flow/5-SUMMARY.md`
</output>
