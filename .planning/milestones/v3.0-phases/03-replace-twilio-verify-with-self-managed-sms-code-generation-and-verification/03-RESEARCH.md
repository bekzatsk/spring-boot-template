# Phase 3: Replace Twilio Verify with Self-Managed SMS Code Generation and Verification - Research

**Researched:** 2026-03-02
**Domain:** SMS OTP — code generation (SecureRandom), BCrypt hashing, DB-backed verification, Spring Scheduling
**Confidence:** HIGH

## Summary

The current phone OTP flow delegates code generation, storage, and verification entirely to Twilio Verify. The TwilioVerifyClient interface + DefaultTwilioVerifyClient abstraction was deliberately added in Phase 01-03 to isolate the Twilio boundary, making this replacement clean: the interface is swapped out, not the surrounding architecture.

This phase replaces Twilio Verify with a self-managed loop: generate a 6-digit code with `SecureRandom`, BCrypt-hash it, persist the hash with an expiry timestamp in a new `sms_verifications` table (V3 migration), and verify locally using `passwordEncoder.matches()`. A `ConsoleSmsService` is introduced as the default `SmsService` implementation (prints code to stdout for dev/test). A scheduled job cleans up expired records. Two endpoint renames and one DTO field rename complete the changes.

The existing `passwordEncoder` bean (`DelegatingPasswordEncoder` from `LocalAuthConfig`) can be reused for BCrypt hashing — no new bean is needed. The entire Twilio SDK dependency, `TwilioConfig`, and `DefaultTwilioVerifyClient` are removed. The `TwilioVerifyClient` interface is also removed (replaced by `SmsService`). Tests switch from `@MockitoBean TwilioVerifyClient` to `@MockitoBean SmsService`.

**Primary recommendation:** Model `SmsVerification` exactly like `RefreshToken` (entity + repository + JPQL delete), introduce `SmsService` interface with `ConsoleSmsService`, and wire the new `SmsVerificationService` into the refactored `PhoneOtpService`.

---

## Standard Stack

### Core (no new dependencies needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `java.security.SecureRandom` | JDK built-in | Cryptographically strong 6-digit code generation | Standard — no external dep; `ThreadLocalRandom` must NOT be used for security-sensitive codes |
| `org.springframework.security.crypto.password.PasswordEncoder` | Spring Security 7 (already in pom) | BCrypt encode + matches for OTP hash | Already in classpath as `passwordEncoder` bean; `DelegatingPasswordEncoder` defaults to bcrypt |
| Spring Data JPA / Hibernate 7 | Already in pom | Persist `SmsVerification` entity | Already wired |
| `@EnableScheduling` / `@Scheduled` | Spring Framework 7 | Cleanup expired sms_verifications rows | Built-in, zero dependency |
| Flyway | Already in pom | V3 migration for `sms_verifications` table | Already wired |

### Supporting (already present)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `BCryptPasswordEncoder` (strength 4) | Spring Security 7 | Test-only encoder — low cost for fast tests | Only in test config or as constructor param in test; production uses strength 10 via existing `passwordEncoder` bean |
| `libphonenumber` | 8.13.52 | E.164 normalization (unchanged) | Already used in `normalizeToE164()` |

### Remove (cleanup)

| Artifact | Reason |
|----------|--------|
| `com.twilio.sdk:twilio:11.3.3` | No longer needed — remove from pom.xml |
| `TwilioConfig.kt` | Calls `Twilio.init()` at startup — delete |
| `TwilioVerifyClient.kt` (interface + `DefaultTwilioVerifyClient`) | Replaced by `SmsService` — delete |
| `app.auth.twilio.*` in `application.yaml` and test `application.yaml` | Dead config — remove all three keys |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| BCrypt via existing `passwordEncoder` bean | HMAC-SHA256 with a server secret | HMAC is faster but requires secure key management; BCrypt is self-contained and consistent with how passwords are handled in this project |
| `@Scheduled(fixedRate)` | Quartz or DB-backed scheduler | Quartz needed only for clustering/persistence; `@Scheduled` is sufficient for single-instance cleanup of expired rows |
| `ConsoleSmsService` printing to stdout | Actual SMS gateway (future) | `ConsoleSmsService` is the placeholder for dev/test; future real SMS provider implements the same `SmsService` interface |

---

## Architecture Patterns

### New File Structure (additions only)

```
src/main/kotlin/kz/innlab/template/
├── authentication/
│   ├── model/
│   │   └── SmsVerification.kt          # NEW — JPA entity (phone, codeHash, expiresAt, used)
│   ├── repository/
│   │   └── SmsVerificationRepository.kt # NEW — JPQL delete expired, find by phone
│   ├── service/
│   │   ├── SmsService.kt               # NEW — interface (sendCode(phone, code): Unit)
│   │   ├── ConsoleSmsService.kt        # NEW — @Component, logs "SMS to $phone: $code"
│   │   ├── SmsVerificationService.kt   # NEW — generate, hash, persist, verify
│   │   └── PhoneOtpService.kt          # MODIFY — inject SmsService + SmsVerificationService
│   ├── dto/
│   │   ├── PhoneOtpRequest.kt          # MODIFY — phoneNumber -> phone field
│   │   └── PhoneVerifyRequest.kt       # MODIFY — phoneNumber -> phone field
│   └── controller/
│       └── AuthController.kt           # MODIFY — endpoint paths + field refs
└── config/
    └── SmsSchedulerConfig.kt           # NEW — @EnableScheduling + cleanup @Scheduled bean
                                        # (or add @EnableScheduling to TemplateApplication)
```

Files to delete:
- `authentication/service/TwilioVerifyClient.kt`
- `config/TwilioConfig.kt`

### Pattern 1: SmsVerification Entity (mirrors RefreshToken)

**What:** JPA entity for one pending OTP record per phone number. Stores BCrypt hash (not plaintext code), expiry instant, and a `used` flag.
**When to use:** Anytime an OTP is issued; old record for same phone is deleted/replaced before insert.

```kotlin
// authentication/model/SmsVerification.kt
@Entity
@Table(name = "sms_verifications")
class SmsVerification(
    @Column(name = "phone", nullable = false)
    val phone: String,

    @Column(name = "code_hash", nullable = false)
    val codeHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null

    @Column(nullable = false)
    var used: Boolean = false

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmsVerification) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
```

Key decisions baked in:
- `phone` is NOT a unique column — multiple rows can exist (one current + expired ones until cleanup); look up by phone + not used + not expired
- `used` flag marks consumed codes — prevents replay
- No FK to `users` table — phone might not have a user yet at request-OTP time

### Pattern 2: SmsVerificationRepository with JPQL (mirrors RefreshTokenRepository)

```kotlin
// authentication/repository/SmsVerificationRepository.kt
interface SmsVerificationRepository : JpaRepository<SmsVerification, UUID> {

    // Find active (unused, not expired) record for phone
    @Query("""
        SELECT sv FROM SmsVerification sv
        WHERE sv.phone = :phone
          AND sv.used = false
          AND sv.expiresAt > :now
    """)
    fun findActiveByPhone(
        @Param("phone") phone: String,
        @Param("now") now: Instant
    ): SmsVerification?

    // Delete all records for a phone before issuing a new code (prevents accumulation)
    @Modifying
    @Transactional
    @Query("DELETE FROM SmsVerification sv WHERE sv.phone = :phone")
    fun deleteAllByPhone(@Param("phone") phone: String)

    // Cleanup scheduled job — JPQL bulk delete (no N+1 entity loading)
    @Modifying
    @Transactional
    @Query("DELETE FROM SmsVerification sv WHERE sv.expiresAt < :cutoff OR sv.used = true")
    fun deleteExpiredOrUsed(@Param("cutoff") cutoff: Instant)
}
```

Note: `@Modifying` + `@Transactional` at repository level — same pattern as `RefreshTokenRepository.deleteAllByUser()`.

### Pattern 3: SmsService Interface + ConsoleSmsService

```kotlin
// authentication/service/SmsService.kt
interface SmsService {
    fun sendCode(phone: String, code: String)
}

// authentication/service/ConsoleSmsService.kt
@Component
class ConsoleSmsService : SmsService {
    private val logger = LoggerFactory.getLogger(ConsoleSmsService::class.java)

    override fun sendCode(phone: String, code: String) {
        logger.info("[SMS] Sending code {} to {}", code, phone)
    }
}
```

This is structurally identical to the `TwilioVerifyClient` interface/impl swap. Tests `@MockitoBean SmsService` instead of `@MockitoBean TwilioVerifyClient`.

### Pattern 4: SmsVerificationService — generate, hash, store, verify

```kotlin
// authentication/service/SmsVerificationService.kt
@Service
class SmsVerificationService(
    private val smsVerificationRepository: SmsVerificationRepository,
    private val smsService: SmsService,
    private val passwordEncoder: PasswordEncoder  // existing bean from LocalAuthConfig
) {
    companion object {
        private const val CODE_LENGTH_DIGITS = 1_000_000  // 6-digit: 000000–999999
        private const val EXPIRY_MINUTES = 5L
        private val random = SecureRandom()
    }

    @Transactional
    fun sendCode(phoneE164: String) {
        val code = String.format("%06d", random.nextInt(CODE_LENGTH_DIGITS))
        val hash = passwordEncoder.encode(code)
        // Delete any existing codes for this phone before inserting new one
        smsVerificationRepository.deleteAllByPhone(phoneE164)
        smsVerificationRepository.save(
            SmsVerification(
                phone = phoneE164,
                codeHash = hash,
                expiresAt = Instant.now().plusSeconds(EXPIRY_MINUTES * 60)
            )
        )
        smsService.sendCode(phoneE164, code)
    }

    @Transactional
    fun verifyCode(phoneE164: String, code: String): Boolean {
        val record = smsVerificationRepository.findActiveByPhone(phoneE164, Instant.now())
            ?: return false
        if (!passwordEncoder.matches(code, record.codeHash)) return false
        record.used = true
        smsVerificationRepository.save(record)
        return true
    }
}
```

### Pattern 5: Scheduled Cleanup

```kotlin
// config/SmsSchedulerConfig.kt  (or add to TemplateApplication.kt)
@Configuration
@EnableScheduling
class SmsSchedulerConfig(
    private val smsVerificationRepository: SmsVerificationRepository
) {
    @Scheduled(fixedRate = 3_600_000)  // every 1 hour in ms
    @Transactional
    fun cleanupExpiredCodes() {
        smsVerificationRepository.deleteExpiredOrUsed(Instant.now())
    }
}
```

Alternative: add `@EnableScheduling` to `TemplateApplication.kt` (simpler for a single-instance template). The dedicated config class is cleaner separation.

### Pattern 6: V3 Flyway Migration

```sql
-- V3__add_sms_verifications.sql
CREATE TABLE sms_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(30) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sms_verifications_phone ON sms_verifications(phone);
```

No FK to `users` table — phone users may not exist yet when OTP is requested. Index on `phone` for efficient active-code lookup.

### Pattern 7: Refactored PhoneOtpService

```kotlin
@Service
class PhoneOtpService(
    private val smsVerificationService: SmsVerificationService,
    private val userService: UserService,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService
) {
    fun sendOtp(rawPhone: String) {
        val phoneE164 = normalizeToE164(rawPhone)
        smsVerificationService.sendCode(phoneE164)
    }

    @Transactional
    fun verifyOtp(rawPhone: String, code: String): AuthResponse {
        val phoneE164 = normalizeToE164(rawPhone)
        val verified = smsVerificationService.verifyCode(phoneE164, code)
        if (!verified) throw BadCredentialsException("Invalid or expired OTP")
        val user = userService.findOrCreatePhoneUser(phoneE164)
        val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)
        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
```

### Pattern 8: DTO field rename + endpoint rename

**PhoneOtpRequest.kt** — rename `phoneNumber: String` to `phone: String`
**PhoneVerifyRequest.kt** — rename `phoneNumber: String` to `phone: String`
**AuthController.kt**:
- `/phone/request-otp` → `/phone/request`
- `/phone/verify-otp` → `/phone/verify`
- `request.phoneNumber` → `request.phone` in both handler methods

### Anti-Patterns to Avoid

- **Using `ThreadLocalRandom` or `Random` for OTP generation:** These are not cryptographically secure. Use `SecureRandom` only.
- **Storing plaintext OTP codes:** The code must be BCrypt-hashed immediately after generation. Only the hash is persisted.
- **Using derived `deleteByPhone()` instead of JPQL `@Modifying @Query`:** Derived delete loads all matching entities first (N+1), then deletes one by one. Use JPQL bulk delete — established pattern already in `RefreshTokenRepository.deleteAllByUser()`.
- **Reusing BCrypt strength 10 in tests:** BCrypt strength 10 takes ~100ms per hash. Integration tests hashing OTPs would be slow. Use `passwordEncoder` bean as-is in tests (it uses `DelegatingPasswordEncoder` which tests with the `{bcrypt}` prefix). If performance is a real issue, the test config can override the bean with `BCryptPasswordEncoder(4)`, but the existing approach should be fine since only a few hashes are done per test.
- **Setting `phone` column UNIQUE in `sms_verifications`:** Multiple rows per phone may exist (one active + stale) until cleanup runs. The active lookup is by JPQL query, not unique constraint.
- **Forgetting `@Transactional` on `sendCode`:** The delete-then-insert is two operations; if the insert fails the delete should roll back.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cryptographically random codes | Custom entropy source | `SecureRandom.nextInt(1_000_000)` | `SecureRandom` is seeded with OS entropy — safe by default |
| OTP hash verification | Custom constant-time compare | `passwordEncoder.matches()` | BCrypt's `checkpw` is timing-attack safe; already in classpath |
| JPQL bulk delete | `deleteBy` derived methods | `@Modifying @Query("DELETE FROM ...")` | Derived delete does SELECT then per-entity DELETE — N+1 problem; already solved in `RefreshTokenRepository` |
| Scheduled cleanup | Quartz / ShedLock | `@EnableScheduling` + `@Scheduled(fixedRate)` | Single-instance template — no clustering needed; zero deps |

**Key insight:** All the infrastructure (BCrypt, scheduling, JPQL) already exists in this codebase or Spring. The work is new data model + wiring, not new technology.

---

## Common Pitfalls

### Pitfall 1: Twilio SDK Still Initializes at Startup

**What goes wrong:** If `TwilioConfig` is not deleted (only commented out), `Twilio.init()` runs at startup with the removed env vars → `NullPointerException` or `IllegalArgumentException`.
**Why it happens:** `@PostConstruct` in `TwilioConfig` runs unconditionally.
**How to avoid:** Delete `TwilioConfig.kt` entirely. Remove Twilio SDK from `pom.xml`. Remove `app.auth.twilio.*` from both `application.yaml` and test `application.yaml`.
**Warning signs:** `IllegalArgumentException: AccountSid must start with AC` at startup.

### Pitfall 2: Test `application.yaml` Still References Twilio Config Keys

**What goes wrong:** Test context fails to start because `@Value("${app.auth.twilio.verify-service-sid}")` in the deleted `PhoneOtpService` still exists as dead reference, or the yaml still has twilio keys that other beans reference.
**Why it happens:** Test YAML is a full override — if it declares twilio keys that no bean consumes, it just silently ignores them. But if a bean still injects `${app.auth.twilio.*}` and the key is removed from YAML, context fails.
**How to avoid:** Remove Twilio YAML keys AND verify no `@Value` in any remaining bean references them.
**Warning signs:** `Could not resolve placeholder 'app.auth.twilio.verify-service-sid'` in test logs.

### Pitfall 3: `@Transactional` Missing on `sendCode` — Partial Update

**What goes wrong:** `deleteAllByPhone` succeeds but `smsVerificationRepository.save()` fails → phone is left with no active code and gets no SMS.
**Why it happens:** `@Modifying @Transactional` on the repository method creates its own transaction, but the outer `sendCode` method is in a separate transaction unless annotated.
**How to avoid:** `@Transactional` on `SmsVerificationService.sendCode()` wraps both the delete and the save in a single transaction.
**Warning signs:** `DataIntegrityViolationException` or silent loss of the new code.

### Pitfall 4: `@EnableScheduling` Not Added

**What goes wrong:** `@Scheduled` annotations are silently ignored — no cleanup ever runs.
**Why it happens:** `@Scheduled` detection requires `@EnableScheduling` somewhere in the application context.
**How to avoid:** Add `@EnableScheduling` to either `TemplateApplication.kt` or a dedicated config class. Verify by checking the Spring startup log for `Registering tasks...` or equivalent.
**Warning signs:** Expired records accumulate in `sms_verifications` indefinitely.

### Pitfall 5: BCrypt Performance in Tests

**What goes wrong:** Integration tests involving phone OTP become slow (100-300ms per hash call at strength 10).
**Why it happens:** `DelegatingPasswordEncoder` delegates to BCrypt strength 10 for `encode()`. Each call to `sendCode()` in a test hashes a 6-digit string at full strength.
**How to avoid:** The existing `passwordEncoder` bean is shared. If test slowness is observed, the test YAML can override the bean with a strength-4 encoder. More practically: the default `DelegatingPasswordEncoder` bean already exists in tests (from `LocalAuthConfig`) and works fine — just note it may take ~100ms per OTP encode in tests. Given tests mock `SmsService`, only the hash+verify path is exercised, not actual SMS — so only 1-2 BCrypt operations per test. This is acceptable.
**Warning signs:** Test suite for `PhoneAuthIntegrationTest` taking 5+ seconds.

### Pitfall 6: Phone DTO Field Rename Breaks Existing JSON Clients

**What goes wrong:** `phoneNumber` → `phone` is a breaking API change. Any client sending `{"phoneNumber": "..."}` will send an unrecognized field (silently ignored with default Jackson config), and `phone` will be null → `@NotBlank` validation fails with 400.
**Why it happens:** JSON deserialization uses field name by default.
**How to avoid:** This is intentional — the phase explicitly renames the field. Ensure all test JSON bodies in `PhoneAuthIntegrationTest` are updated from `"phoneNumber"` to `"phone"`.
**Warning signs:** Tests passing `{"phoneNumber": "..."}` return 400 after rename.

---

## Code Examples

### SecureRandom 6-digit code generation

```kotlin
// Source: java.security.SecureRandom (JDK standard)
val random = SecureRandom()
val code = String.format("%06d", random.nextInt(1_000_000))
// Output: "000001" through "999999" — always 6 digits with leading zeros
```

### BCrypt encode and verify (using existing PasswordEncoder bean)

```kotlin
// Source: Spring Security 7 PasswordEncoder interface
// In SmsVerificationService constructor:
//   private val passwordEncoder: PasswordEncoder  (injected from LocalAuthConfig)

val hash: String = passwordEncoder.encode(code)     // encode for storage
val matches: Boolean = passwordEncoder.matches(code, hash)  // verify on check
```

Note: `DelegatingPasswordEncoder.encode()` prefixes with `{bcrypt}` → stored hash looks like `{bcrypt}$2a$10$...`. `matches()` strips the prefix and delegates correctly.

### JPQL bulk delete (mirrors RefreshTokenRepository pattern)

```kotlin
// Source: existing RefreshTokenRepository.deleteAllByUser pattern in this codebase
@Modifying
@Transactional
@Query("DELETE FROM SmsVerification sv WHERE sv.expiresAt < :cutoff OR sv.used = true")
fun deleteExpiredOrUsed(@Param("cutoff") cutoff: Instant)
```

### @Scheduled fixedRate cleanup

```kotlin
// Source: Spring Framework @Scheduled annotation
@Scheduled(fixedRate = 3_600_000)  // 1 hour in milliseconds
@Transactional
fun cleanupExpiredCodes() {
    smsVerificationRepository.deleteExpiredOrUsed(Instant.now())
}
```

### Updated test pattern — @MockitoBean SmsService

```kotlin
// Replace:  @MockitoBean private lateinit var twilioVerifyClient: TwilioVerifyClient
// With:
@MockitoBean
private lateinit var smsService: SmsService

// Replace:  doNothing().`when`(twilioVerifyClient).sendVerification(anyString(), anyString(), anyString())
// With:
doNothing().`when`(smsService).sendCode(anyString(), anyString())

// Remove all `when(twilioVerifyClient.checkVerification(...)).thenReturn("approved")` lines
// — verification is now done by the real SmsVerificationService against H2
```

Note: Because `SmsVerificationService` does real BCrypt + H2 operations in tests, the test must pre-seed a code record in H2 when testing the verify path — or call the real `sendCode` path (which will call the mocked `smsService.sendCode()` but still persist the hash to H2).

### Test strategy for verify path

```kotlin
// In PhoneAuthIntegrationTest.verifyOtp success test:
// Step 1: Call requestOtp endpoint (mocked SmsService captures nothing, but H2 gets the hash)
// Step 2: Read the actual code from H2 via smsVerificationRepository.findActiveByPhone(...)
// Step 3: Call verifyOtp with the real code read from the repository

// OR: inject SmsVerificationService and call sendCode directly in @BeforeEach setup
```

This is the key test architecture shift: the old tests mocked the Twilio verification check result. New tests must use the real verification path against H2 — the code must be read from the repository or captured differently. The cleanest approach is to call `smsVerificationService.sendCode(testPhone)` in the test, then use a fixed known code by injecting the repository and reading the stored hash, then verify against a code that matches. But since the hash is BCrypt, the plaintext code is unknown after generation.

**Recommended test approach:**
- Inject `SmsVerificationRepository` in `PhoneAuthIntegrationTest`
- Inject `PasswordEncoder` in test
- Call `/phone/request` → H2 has a `SmsVerification` row with a BCrypt hash
- Read the row, cannot recover the code → instead: directly insert a known test code via `smsVerificationRepository.save(SmsVerification(phone, passwordEncoder.encode("123456"), Instant.now().plusSeconds(300)))`
- Then call `/phone/verify` with `"123456"` → verified against H2

The pre-seeding approach is used in `PhoneAuthIntegrationTest.verifyOtp success for returning user` (which already pre-creates a `User`) — same pattern for pre-seeding a verification record.

---

## Full Impact Assessment

### Files to CREATE (new)

| File | Type |
|------|------|
| `authentication/model/SmsVerification.kt` | JPA entity |
| `authentication/repository/SmsVerificationRepository.kt` | Spring Data repo |
| `authentication/service/SmsService.kt` | Interface |
| `authentication/service/ConsoleSmsService.kt` | Default impl |
| `authentication/service/SmsVerificationService.kt` | Core logic |
| `config/SmsSchedulerConfig.kt` | @EnableScheduling + cleanup |
| `src/main/resources/db/migration/V3__add_sms_verifications.sql` | Schema migration |

### Files to MODIFY

| File | Change |
|------|--------|
| `authentication/service/PhoneOtpService.kt` | Replace Twilio deps with SmsVerificationService |
| `authentication/dto/PhoneOtpRequest.kt` | `phoneNumber` → `phone` |
| `authentication/dto/PhoneVerifyRequest.kt` | `phoneNumber` → `phone` |
| `authentication/controller/AuthController.kt` | Endpoint paths + field refs |
| `pom.xml` | Remove Twilio SDK dependency |
| `src/main/resources/application.yaml` | Remove `app.auth.twilio.*` section |
| `src/test/resources/application.yaml` | Remove `app.auth.twilio.*` section |
| `src/test/kotlin/.../PhoneAuthIntegrationTest.kt` | New mock strategy + JSON field names |

### Files to DELETE

| File | Reason |
|------|--------|
| `authentication/service/TwilioVerifyClient.kt` | Replaced by SmsService |
| `config/TwilioConfig.kt` | Twilio SDK removed |

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Twilio Verify manages code lifecycle (generate, store, expire, verify) | Self-managed: SecureRandom + BCrypt + DB | No external service dependency; full control |
| `TwilioVerifyClient` interface | `SmsService` interface | Same pattern, cleaner name |
| `DefaultTwilioVerifyClient` | `ConsoleSmsService` | Dev/test: stdout; prod: swap in real SMS provider |
| Tests mock checkVerification returning "approved" | Tests pre-seed H2 with known hash | Tests exercise the real verification path |
| `/phone/request-otp`, `/phone/verify-otp` | `/phone/request`, `/phone/verify` | Cleaner REST naming |
| `phoneNumber` DTO field | `phone` DTO field | Shorter, consistent with `User.phone` field |

---

## Open Questions

1. **Code expiry duration**
   - What we know: Industry standard is 3-10 minutes; Twilio Verify defaults to 10 minutes
   - What's unclear: Not specified in phase description
   - Recommendation: Default to 5 minutes (`EXPIRY_MINUTES = 5L`) — balance between usability and security

2. **Should `sendCode` replace (delete-then-insert) or accumulate records?**
   - What we know: Accumulation requires the cleanup job to work; replacement is more predictable
   - What's unclear: Phase description does not specify
   - Recommendation: Delete-then-insert on `sendCode` — exactly one active code per phone at any time; simpler verification logic

3. **Verification attempt counting (brute-force protection)**
   - What we know: Industry practice is max 3-5 attempts; phase description does not mention it
   - What's unclear: Whether to implement attempt counting or leave as TODO
   - Recommendation: Leave as TODO comment (consistent with rate limiting TODOs already in the codebase); the existing `used` flag already prevents replay after first success

4. **Where to put `@EnableScheduling`**
   - What we know: Can go on `TemplateApplication` or a dedicated config class
   - Recommendation: Dedicated `SmsSchedulerConfig.kt` — follows Spring convention of grouping scheduling concerns; avoids bloating main application class

---

## Sources

### Primary (HIGH confidence)
- Spring Security 7 BCryptPasswordEncoder API — https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.html — strength range 4-31, encode/matches methods
- `RefreshTokenRepository.kt` in this codebase — `@Modifying @Transactional @Query("DELETE FROM ...")` pattern verified directly
- `LocalAuthConfig.kt` in this codebase — `passwordEncoder()` bean (DelegatingPasswordEncoder) confirmed present, reusable
- `PhoneAuthIntegrationTest.kt` in this codebase — existing test structure confirmed, shows `@MockitoBean TwilioVerifyClient` pattern to replace
- `TwilioVerifyClient.kt` in this codebase — interface confirmed replaceable, `DefaultTwilioVerifyClient` confirmed for deletion

### Secondary (MEDIUM confidence)
- Spring `@EnableScheduling` official Javadoc — https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html — enables `@Scheduled` detection
- Baeldung: Purging Expired Tokens — https://www.baeldung.com/registration-token-cleanup — `@Scheduled(cron)` + `@Modifying @Query` delete pattern
- Baeldung: Spring Data JPA @Modifying — https://www.baeldung.com/spring-data-jpa-modifying-annotation — confirmed `@Modifying` + `@Transactional` required for bulk delete
- OTP security design 2025 — https://nerdbot.com/2026/02/28/designing-a-secure-otp-verification-flow-for-modern-web-apps/ — 5-minute expiry, single-use flag, SecureRandom

### Tertiary (LOW confidence)
- None — all key claims verified with primary sources

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all components already present in codebase or JDK; no new deps needed
- Architecture: HIGH — directly mirrors existing RefreshToken/TwilioVerifyClient patterns in the same codebase
- Test strategy: MEDIUM — pre-seeding H2 for verify tests is the right approach but requires careful setup; not directly verified against running test suite
- Pitfalls: HIGH — verified against actual code (TwilioConfig @PostConstruct, existing deleteAllByUser JPQL pattern, field rename impact)

**Research date:** 2026-03-02
**Valid until:** 2026-04-02 (stable Spring Boot/Security APIs; no fast-moving dependencies)
