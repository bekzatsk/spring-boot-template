# Phase 5: Add Account Management — Research

**Researched:** 2026-03-03
**Domain:** Account management flows — forgot password, change password, change email, change phone with self-managed verification codes
**Confidence:** HIGH (codebase patterns confirmed, external flows verified, library availability confirmed)

---

## Summary

Phase 5 adds four account management operations on top of the existing auth infrastructure (Phases 1–4). All four flows share a common underlying pattern: issue a short-lived verification code, store it hashed, verify it, then mutate the user's credentials. The project already has a complete self-managed OTP infrastructure (`SmsVerificationService`, `SmsVerification` entity, `SmsVerificationRepository`) that implements exactly this pattern for phone login. Phase 5 extends and reuses that pattern.

**Two categories of flows exist:**

1. **Unauthenticated flows** — Forgot password requires no JWT (user cannot log in). These endpoints must be open in `SecurityConfig` alongside `/api/v1/auth/**`.
2. **Authenticated flows** — Change password, change email, change phone require a valid JWT access token (`/api/v1/users/**` is already guarded by `authenticated`).

Email delivery is needed for forgot-password and change-email. The project has no email infrastructure yet. Spring Boot's `spring-boot-starter-mail` (4.0.3, BOM-managed) + `JavaMailSender` is the standard. Following the `SmsService`/`ConsoleSmsService` pattern, an `EmailService` interface with a `ConsoleEmailService` default provides testability via `@MockitoBean` and runtime substitutability via `@ConditionalOnMissingBean`.

**Primary recommendation:** Reuse `SmsVerificationService` patterns for all four flows. Add a single `verification_codes` table with a `purpose` discriminator column (FORGOT_PASSWORD, CHANGE_EMAIL, CHANGE_PHONE). Add `spring-boot-starter-mail` for email delivery. Mirror the `SmsService`/`ConsoleSmsService` abstraction with an `EmailService` interface. Revoke all refresh tokens after forgot-password reset. The `UserController` (authenticated `/api/v1/users/**`) is the natural home for change-* endpoints.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-mail` | 4.0.3 (BOM-managed) | JavaMailSender auto-configuration for email delivery | Spring Boot official; no extra version needed — BOM manages it |
| `spring-security` (already present) | via Boot 4.0.3 | Secures change-* endpoints behind `authenticated` | Already wired |
| `spring-boot-starter-validation` (already present) | via Boot 4.0.3 | Input validation on new email, phone, password DTOs | Already wired |
| `libphonenumber` (already present) | 8.13.52 | E.164 normalization for change-phone flow | Already present, reuse `normalizeToE164()` |
| BCrypt `PasswordEncoder` (already present) | via Boot 4.0.3 | Hash verification codes before DB storage; verify new passwords | Already wired via `LocalAuthConfig` |
| `uuid-creator` (already present) | 6.1.1 | UUID v7 for new `verification_codes` entity via `BaseEntity` | Already wired |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `JavaMailSender` (from `spring-boot-starter-mail`) | 4.0.3 | Send password reset / email change codes by email | Always for forgot-password and change-email flows |
| `@Scheduled` (already present via `@EnableScheduling`) | via Boot 4.0.3 | Scheduled cleanup of expired verification codes | Reuse existing `SmsSchedulerConfig` cleanup job pattern |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Single `verification_codes` table with `purpose` column | Separate tables per flow (forgot_password_tokens, email_change_tokens, phone_change_tokens) | Separate tables are more explicit but create schema sprawl; single table with enum discriminator is simpler and mirrors the existing `sms_verifications` table |
| Short numeric codes (6-digit) for email flows | Long random token strings (32-byte URL-safe) for email links | URL-safe tokens are more appropriate for email links that the user clicks (no typing); 6-digit codes are better when user types into an app field. Either works — 6-digit code with verificationId (matching current phone pattern) is simpler and consistent |
| `JavaMailSender` | AWS SES SDK, SendGrid client | JavaMailSender is SMTP-agnostic — works with any SMTP relay including AWS SES and SendGrid via SMTP; no vendor coupling |

---

## Architecture Patterns

### Recommended Project Structure

```
src/main/kotlin/kz/innlab/template/
├── authentication/
│   ├── model/
│   │   ├── SmsVerification.kt         (existing)
│   │   └── VerificationCode.kt        (NEW — multi-purpose, replaces sms_verifications for new flows)
│   ├── repository/
│   │   └── VerificationCodeRepository.kt  (NEW)
│   ├── service/
│   │   ├── SmsVerificationService.kt  (existing, unchanged)
│   │   ├── EmailService.kt            (NEW — interface)
│   │   ├── ConsoleEmailService.kt     (NEW — dev default, no @Component)
│   │   └── AccountManagementService.kt (NEW — orchestrates all 4 flows)
│   └── dto/
│       ├── ForgotPasswordRequest.kt   (NEW)
│       ├── ResetPasswordRequest.kt    (NEW)
│       ├── ChangePasswordRequest.kt   (NEW)
│       ├── ChangeEmailRequest.kt      (NEW — step 1: request code)
│       ├── VerifyChangeEmailRequest.kt (NEW — step 2: confirm with code)
│       ├── ChangePhoneRequest.kt      (NEW — step 1: request code)
│       └── VerifyChangePhoneRequest.kt (NEW — step 2: confirm with code, same shape as PhoneVerifyRequest)
├── user/
│   └── controller/
│       └── UserController.kt          (EXTEND — add change-password, change-email, change-phone endpoints)
├── config/
│   └── SmsSchedulerConfig.kt          (EXTEND — add ConsoleEmailService bean + cleanup for verification_codes)
└── resources/
    └── db/migration/
        └── V2__add_verification_codes.sql  (NEW)
```

### Pattern 1: Multi-Purpose Verification Code Table

The existing `sms_verifications` table is phone-OTP-specific. The new flows (forgot-password, change-email, change-phone) benefit from a shared `verification_codes` table with a `purpose` discriminator.

**Why one table:**
- All flows share the same lifecycle: generate → hash → store → verify → mark-used → expire
- Avoids 3 nearly-identical tables
- Maps cleanly to one `VerificationCode` entity + one `VerificationCodeRepository`

**DB Schema (V2 migration):**

```sql
CREATE TYPE verification_purpose AS ENUM (
    'FORGOT_PASSWORD',
    'CHANGE_EMAIL',
    'CHANGE_PHONE'
);

CREATE TABLE verification_codes (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),   -- NULL for forgot-password (user identified by email/phone)
    identifier VARCHAR(255) NOT NULL,    -- email or phone E.164, depending on purpose
    purpose VARCHAR(50) NOT NULL,        -- 'FORGOT_PASSWORD', 'CHANGE_EMAIL', 'CHANGE_PHONE'
    code_hash VARCHAR(255) NOT NULL,
    new_value VARCHAR(255),              -- For CHANGE_EMAIL: pending new email; for CHANGE_PHONE: pending new E.164
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_codes_identifier_purpose ON verification_codes(identifier, purpose);
```

**Note on PostgreSQL ENUMs vs VARCHAR:** The existing schema uses VARCHAR for all enum-like columns (e.g., `provider VARCHAR(50)` in `user_providers`). Use `VARCHAR(50)` for `purpose` to stay consistent and avoid Flyway/Hibernate enum mapping complexity.

**Note on H2 compatibility:** Use `VARCHAR(50)` for `purpose`, NOT a native `ENUM` type — H2 does not support PostgreSQL `CREATE TYPE ... AS ENUM`. The existing `sms_verifications` table uses `BOOLEAN` and `INTEGER` which H2 handles fine.

### Pattern 2: Flow Structures

**Forgot Password (unauthenticated — two-step):**

```
POST /api/v1/auth/forgot-password  { email }
  → find user by email
  → if not found: return 202 Accepted (same response — prevents email enumeration)
  → generate 6-digit code, BCrypt hash, store in verification_codes (FORGOT_PASSWORD, identifier=email)
  → send code by email via EmailService
  → return 202 Accepted + { verificationId }

POST /api/v1/auth/reset-password  { verificationId, email, code, newPassword }
  → look up verification_codes by verificationId
  → verify: same email, not used, not expired, attempts < 3
  → BCrypt matches code
  → mark used; update user.passwordHash = BCrypt(newPassword)
  → revoke all refresh tokens for user (refreshTokenRepository.deleteAllByUser)
  → return 200 OK
```

**Change Password (authenticated — single step, no code needed):**

```
POST /api/v1/users/me/change-password  { currentPassword, newPassword }  [Bearer token required]
  → extract userId from JWT
  → load user; verify user has LOCAL provider
  → verify currentPassword matches user.passwordHash via PasswordEncoder.matches()
  → update user.passwordHash = BCrypt(newPassword)
  → revoke all refresh tokens for user (optional but recommended — security best practice)
  → return 200 OK
```

**Change Email (authenticated — two-step):**

```
POST /api/v1/users/me/change-email/request  { newEmail }  [Bearer token required]
  → extract userId from JWT
  → verify newEmail not already taken (findByEmail)
  → generate 6-digit code, BCrypt hash, store in verification_codes
    (CHANGE_EMAIL, identifier=current email OR userId, new_value=newEmail)
  → send code to newEmail via EmailService (verifying ownership of new address)
  → return 200 OK + { verificationId }

POST /api/v1/users/me/change-email/verify  { verificationId, code }  [Bearer token required]
  → extract userId from JWT
  → look up verification_codes by verificationId, check userId matches
  → BCrypt matches code, not expired, attempts < 3
  → check new_value (pending email) not taken (re-verify uniqueness)
  → update user.email = new_value
  → mark used
  → return 200 OK
```

**Change Phone (authenticated — two-step, mirrors existing phone auth):**

```
POST /api/v1/users/me/change-phone/request  { phone }  [Bearer token required]
  → extract userId from JWT
  → normalizeToE164(phone)
  → verify phone not already taken (findByPhone)
  → generate 6-digit code, BCrypt hash, store in verification_codes
    (CHANGE_PHONE, identifier=phoneE164, new_value=phoneE164, user_id=userId)
  → send code via SmsService (reuse existing)
  → return 200 OK + { verificationId }

POST /api/v1/users/me/change-phone/verify  { verificationId, phone, code }  [Bearer token required]
  → extract userId from JWT
  → normalizeToE164(phone)
  → look up verification_codes by verificationId, check userId matches
  → BCrypt matches code, not expired, attempts < 3
  → check phone not taken (re-verify uniqueness)
  → update user.phone = phoneE164; ensure LOCAL in user.providers
  → mark used
  → return 200 OK
```

### Pattern 3: EmailService / ConsoleEmailService (mirror SmsService pattern)

```kotlin
// authentication/service/EmailService.kt
interface EmailService {
    fun sendCode(to: String, code: String, purpose: String)
}

// authentication/service/ConsoleEmailService.kt
class ConsoleEmailService : EmailService {
    override fun sendCode(to: String, code: String, purpose: String) {
        logger.info("[EMAIL] Sending {} code {} to {}", purpose, code, to)
    }
}

// In SmsSchedulerConfig (or new EmailConfig):
@Bean
@ConditionalOnMissingBean(EmailService::class)
fun emailService(): EmailService = ConsoleEmailService()
```

When `spring.mail.host` is configured in prod, a `JavaMailSenderEmailService` implementing `EmailService` can be registered as a `@Bean` to override `ConsoleEmailService`.

### Pattern 4: VerificationCodeService (mirror SmsVerificationService)

```kotlin
@Service
class VerificationCodeService(
    private val verificationCodeRepository: VerificationCodeRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.auth.verification.dev-code:}") private val devCode: String = ""
) {
    companion object {
        private const val CODE_BOUND = 1_000_000
        private const val EXPIRY_MINUTES = 15L      // longer than SMS: user has to check email
        private const val RATE_LIMIT_SECONDS = 60L
        private const val MAX_ATTEMPTS = 3
        private val random = SecureRandom()
    }

    @Transactional
    fun sendCode(
        identifier: String,
        purpose: VerificationPurpose,
        newValue: String? = null,
        userId: UUID? = null
    ): UUID { ... }

    @Transactional
    fun verifyCode(verificationId: UUID, identifier: String, purpose: VerificationPurpose, code: String): VerificationCode { ... }
}
```

### Pattern 5: Security Config Changes

Forgot-password and reset-password are unauthenticated. Current `SecurityConfig`:

```kotlin
authorize("/api/v1/auth/**", permitAll)
authorize("/api/**", authenticated)
```

`/api/v1/auth/forgot-password` and `/api/v1/auth/reset-password` naturally fall under `/api/v1/auth/**` — already permitted. No SecurityConfig change needed.

Change-password, change-email/*, change-phone/* live under `/api/v1/users/**` — already guarded by `authenticated`. No SecurityConfig change needed.

### Pattern 6: Refresh Token Revocation After Password Reset

After a successful forgot-password reset, call `refreshTokenRepository.deleteAllByUser(user)`. This terminates all existing sessions. This is the same method used in `RefreshTokenService.rotate()` for reuse detection.

After change-password (authenticated, user-initiated), revocation is optional but recommended. Revoke all OTHER sessions (not current) or all sessions and force re-login — simple approach is `deleteAllByUser`.

### Anti-Patterns to Avoid

- **Returning different HTTP status for unknown email in forgot-password:** Always return 202 Accepted regardless of whether the email exists — prevents email enumeration.
- **Storing plaintext codes in the DB:** Always BCrypt hash (same as existing `SmsVerification.codeHash`).
- **Not revoking refresh tokens after password change:** "Ghost sessions" — old sessions remain valid. After reset, call `refreshTokenRepository.deleteAllByUser`.
- **Using H2 native enum types in migration:** H2 does not support `CREATE TYPE ... AS ENUM`. Use `VARCHAR(50)` for the `purpose` column — consistent with existing schema.
- **Using `@GeneratedValue` in new entity:** Use `BaseEntity` (extends `Persistable<UUID>`, UUID v7 via `UuidCreator.getTimeOrderedEpoch()`) as the project established in Phase 4.
- **Putting change-* endpoints in AuthController:** AuthController is for unauthenticated auth flows. Change-* are user management operations — they belong in `UserController` under `/api/v1/users/me/`.
- **Not re-verifying email/phone uniqueness at confirm step:** Between request and verify steps, another user may have registered the same email/phone. Re-check uniqueness in the verify step before committing.
- **Using JWT for password reset tokens:** If the JWT signing key is compromised, all reset tokens are forgeable. Use opaque random codes (6-digit with verificationId) — same as the project's existing SMS pattern.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Email sending | Custom SMTP client | `spring-boot-starter-mail` + `JavaMailSender` | Thread-safe, connection pooling, MimeMessage support, tested |
| Password hashing | Custom hash for new password | `PasswordEncoder.encode()` (already in context) | BCrypt is already wired via `LocalAuthConfig` |
| Verification code generation | Custom PRNG | `SecureRandom` (existing pattern in `SmsVerificationService`) | Already established; CSPRNG; no new dependency |
| Phone normalization | Custom regex | `normalizeToE164()` in `PhoneNumberUtil.kt` (already exists) | Already exists; eliminates region ambiguity |
| Rate limiting per-identifier | Custom counter in DB | Existing pattern: `existsByIdentifierAndCreatedAtAfter(...)` | Same JPQL predicate pattern used in `SmsVerificationRepository` |

**Key insight:** The entire verification code lifecycle (generate → hash → store → verify → expire → cleanup) already exists in `SmsVerificationService`. Phase 5 extracts the reusable core into `VerificationCodeService` and drives it from the account management operations.

---

## Common Pitfalls

### Pitfall 1: H2 Enum Type in Migration

**What goes wrong:** Using `CREATE TYPE verification_purpose AS ENUM (...)` in the Flyway migration causes H2 test failures.
**Why it happens:** H2 does not implement PostgreSQL-native `CREATE TYPE ... AS ENUM`. Tests use H2 (`spring.flyway.enabled=false` in test profile, H2 `create-drop`) — but any new migration must be H2-compatible if the test profile ever enables Flyway, and the entity DDL generated by Hibernate must match.
**How to avoid:** Use `VARCHAR(50)` for the `purpose` column. Map it to a Kotlin `enum class VerificationPurpose` with `@Enumerated(EnumType.STRING)`.
**Warning signs:** `JdbcSQLSyntaxErrorException: Feature not supported: "CREATE TYPE"` in test run.

### Pitfall 2: Email Enumeration in Forgot-Password

**What goes wrong:** Returning `404 Not Found` when the email is not registered reveals which emails are registered.
**Why it happens:** Naive implementation checks existence and returns early.
**How to avoid:** Always return `202 Accepted` with `{ verificationId: null }` or just `{}` when email not found. Still generate and discard a dummy verification so timing is consistent.
**Warning signs:** Different response times or bodies for found vs. not-found emails.

### Pitfall 3: RefreshToken FK Constraint in Tests

**What goes wrong:** `@BeforeEach` cleanup fails with FK constraint violation when deleting users before refresh tokens.
**Why it happens:** `refresh_tokens.user_id` references `users.id`. The existing tests solve this by deleting refresh tokens before users.
**How to avoid:** In new `AccountManagementIntegrationTest`, follow the established `@BeforeEach` cleanup order: refresh tokens → sms_verifications → verification_codes → users.
**Warning signs:** `ERROR: update or delete on table "users" violates foreign key constraint "refresh_tokens_user_id_fkey"`.

### Pitfall 4: doAnswer Pattern for Email Code Capture in Tests

**What goes wrong:** Plain Mockito `ArgumentCaptor.capture()` returns null in Kotlin non-null parameter context.
**Why it happens:** Kotlin strict null safety rejects the null placeholder that ArgumentCaptor uses.
**How to avoid:** Use `doAnswer { invocation -> capturedCode = invocation.arguments[1] as String; null }` pattern — exactly as established in `PhoneAuthIntegrationTest.captureCodeOnSend()`. Mirror for `EmailService.sendCode()`.
**Warning signs:** `NullPointerException` on `capture()` call in test setup.

### Pitfall 5: Change-Email Race Condition

**What goes wrong:** Email A requests change to Email B. Before verification, Email B registers normally. Verification succeeds and overwrites Email B's account owner.
**Why it happens:** Uniqueness check happens at request time, not at confirm time.
**How to avoid:** Re-check uniqueness in `verifyCode` (confirm step) before committing `user.email = newValue`. Throw `IllegalStateException` (→ 409) if new email is already taken.
**Warning signs:** Duplicate email in the `users` table triggers PostgreSQL partial unique index violation.

### Pitfall 6: `BaseEntity` Not Used for New Entity

**What goes wrong:** New `VerificationCode` entity uses `@GeneratedValue(strategy = IDENTITY)` or `@GeneratedValue(strategy = UUID)`.
**Why it happens:** Forgetting Phase 4 established `BaseEntity` as the standard.
**How to avoid:** Extend `BaseEntity` for `VerificationCode` — same as `SmsVerification`, `User`, and `RefreshToken` already do.
**Warning signs:** UUID v4 IDs in new records; `isNew()` misdetection causing `UPDATE` instead of `INSERT`.

### Pitfall 7: spring.mail.host Missing in Dev/Test = No Bean

**What goes wrong:** Application fails to start because `JavaMailSender` is not registered (Spring Boot only auto-creates it when `spring.mail.host` is set) — but tests don't need real email sending.
**Why it happens:** `@MockitoBean EmailService` replaces the interface; `JavaMailSender` is never injected into `ConsoleEmailService` (which doesn't need it). There is no risk if `EmailService` interface is the dependency boundary. Only a problem if `JavaMailSenderEmailService` is wired unconditionally.
**How to avoid:** `ConsoleEmailService` does not depend on `JavaMailSender`. Prod `JavaMailSenderEmailService` is registered via `@Bean @ConditionalOnProperty("spring.mail.host")` in an `EmailConfig`. Tests `@MockitoBean EmailService` — no mail infrastructure needed.
**Warning signs:** `NoSuchBeanDefinitionException: No qualifying bean of type 'JavaMailSender'` in test startup.

---

## Code Examples

### VerificationCode Entity

```kotlin
// Source: mirrors SmsVerification.kt pattern + BaseEntity from Phase 4
@Entity
@Table(name = "verification_codes")
class VerificationCode(
    @Column(name = "identifier", nullable = false)
    val identifier: String,              // email or E.164 phone

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 50)
    val purpose: VerificationPurpose,

    @Column(name = "code_hash", nullable = false)
    val codeHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "new_value")
    val newValue: String? = null,        // pending new email or phone

    @Column(name = "user_id")
    val userId: UUID? = null             // null for FORGOT_PASSWORD
) : BaseEntity() {

    @Column(nullable = false)
    var used: Boolean = false

    @Column(nullable = false)
    var attempts: Int = 0

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VerificationCode) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class VerificationPurpose { FORGOT_PASSWORD, CHANGE_EMAIL, CHANGE_PHONE }
```

### VerificationCodeRepository

```kotlin
interface VerificationCodeRepository : JpaRepository<VerificationCode, UUID> {
    fun existsByIdentifierAndPurposeAndCreatedAtAfter(
        identifier: String,
        purpose: VerificationPurpose,
        since: Instant
    ): Boolean

    @Modifying
    @Transactional
    @Query("DELETE FROM VerificationCode vc WHERE vc.expiresAt < :cutoff OR vc.used = true")
    fun deleteExpiredOrUsed(@Param("cutoff") cutoff: Instant)

    @Modifying
    @Transactional
    @Query("DELETE FROM VerificationCode vc WHERE vc.identifier = :identifier AND vc.purpose = :purpose")
    fun deleteAllByIdentifierAndPurpose(
        @Param("identifier") identifier: String,
        @Param("purpose") purpose: VerificationPurpose
    )
}
```

### Flyway V2 Migration (V2__add_verification_codes.sql)

```sql
-- Phase 5: Account management verification codes
-- Purpose column uses VARCHAR(50) not PostgreSQL ENUM — H2 compatibility preserved
CREATE TABLE verification_codes (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    identifier VARCHAR(255) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    new_value VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_codes_identifier_purpose
    ON verification_codes(identifier, purpose);
```

### AccountManagementController (sketch)

```kotlin
@RestController
@RequestMapping("/api/v1/users/me")
class AccountManagementController(
    private val accountManagementService: AccountManagementService
) {
    // Change Password — no code needed, just current + new
    @PostMapping("/change-password")
    fun changePassword(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<Void> {
        accountManagementService.changePassword(UUID.fromString(jwt.subject), request.currentPassword, request.newPassword)
        return ResponseEntity.ok().build()
    }

    // Change Email — two-step
    @PostMapping("/change-email/request")
    fun requestChangeEmail(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangeEmailRequest
    ): ResponseEntity<Map<String, Any>> {
        val verificationId = accountManagementService.requestEmailChange(UUID.fromString(jwt.subject), request.newEmail)
        return ResponseEntity.ok(mapOf("verificationId" to verificationId))
    }

    @PostMapping("/change-email/verify")
    fun verifyChangeEmail(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: VerifyChangeEmailRequest
    ): ResponseEntity<Void> {
        accountManagementService.verifyEmailChange(UUID.fromString(jwt.subject), request.verificationId, request.code)
        return ResponseEntity.ok().build()
    }

    // Change Phone — two-step (mirrors phone auth)
    @PostMapping("/change-phone/request")
    fun requestChangePhone(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangePhoneRequest
    ): ResponseEntity<Map<String, Any>> {
        val verificationId = accountManagementService.requestPhoneChange(UUID.fromString(jwt.subject), request.phone)
        return ResponseEntity.ok(mapOf("verificationId" to verificationId))
    }

    @PostMapping("/change-phone/verify")
    fun verifyChangePhone(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: VerifyChangePhoneRequest
    ): ResponseEntity<Void> {
        accountManagementService.verifyPhoneChange(UUID.fromString(jwt.subject), request.verificationId, request.phone, request.code)
        return ResponseEntity.ok().build()
    }
}
```

### Forgot Password in AuthController (existing controller, new endpoints)

```kotlin
// Add to AuthController
@PostMapping("/forgot-password")
fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<Map<String, Any?>> {
    // TODO: rate limiting
    val verificationId = accountManagementService.requestPasswordReset(request.email)
    // Always return same shape — verificationId may be null if email not found (anti-enumeration)
    return ResponseEntity.accepted().body(mapOf("verificationId" to verificationId))
}

@PostMapping("/reset-password")
fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Void> {
    // TODO: rate limiting
    accountManagementService.resetPassword(request.verificationId, request.email, request.code, request.newPassword)
    return ResponseEntity.ok().build()
}
```

### Test Pattern for Email Code Capture

```kotlin
// Mirror of PhoneAuthIntegrationTest.captureCodeOnSend()
@MockitoBean
private lateinit var emailService: EmailService

private fun captureEmailCodeOnSend(): () -> String {
    var capturedCode: String? = null
    doAnswer { invocation ->
        capturedCode = invocation.arguments[1] as String  // sendCode(to, code, purpose) — index 1
        null
    }.`when`(emailService).sendCode(anyString(), anyString(), anyString())
    return { capturedCode ?: error("emailService.sendCode was not called") }
}
```

### application.yaml additions (dev profile)

```yaml
app:
  auth:
    verification:
      dev-code: "123456"    # mirrors sms.dev-code pattern
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Long UUID reset tokens in email links | Short 6-digit codes with verificationId (API-first, mobile-friendly) | Phase 3 established for SMS | Match existing pattern — users type code into app, not click links |
| Separate token tables per flow | Single `verification_codes` table with `purpose` discriminator | Pattern established here | Simpler schema, single cleanup job |
| `@GeneratedValue` UUID | `BaseEntity` UUID v7 via `UuidCreator.getTimeOrderedEpoch()` | Phase 4 | All new entities extend `BaseEntity` |
| Twilio for OTP delivery | Self-managed codes via `SmsService` + `SmsVerificationService` | Phase 3 | Fully self-managed; same pattern applies to email codes |

**Deprecated/outdated:**
- Long-lived password reset tokens (24h+): Modern guidance recommends 15–30 minutes for email codes
- Returning `404` for unknown email in forgot-password: Anti-enumeration requires `202 Accepted` always

---

## Open Questions

1. **Should change-password revoke ALL refresh tokens (force re-login everywhere) or just be informational?**
   - What we know: Security best practice (OWASP, sources above) is to revoke all sessions after password change to kill "ghost sessions"
   - What's unclear: UX impact — user may be logged out on all devices
   - Recommendation: Revoke all refresh tokens (call `refreshTokenRepository.deleteAllByUser`) — consistent with forgot-password reset behavior; document this as a security decision

2. **Change-email: send code to new email only, or verify old email too?**
   - What we know: Sending to new email proves ownership of new address. Sending to old email notifies user of account change (security alert). Better-auth issue #7196 shows a typo attack when only new email is verified.
   - What's unclear: Complexity vs. security tradeoff
   - Recommendation: Send to new email only for the verification code (proves ownership). The better-auth typo issue (user types wrong new email) is mitigated by requiring the code from that new email — if they typed wrong email, code never arrives. Add a plain notification email to old address (no code, just "your email was changed"). This is a LOW-complexity, HIGH-security outcome.

3. **Should `VerificationCode` entity store `userId` for forgot-password (where no JWT is available)?**
   - What we know: For forgot-password, no JWT is present. The user is identified by email (`identifier`). `user_id` can be `NULL REFERENCES users(id)` (nullable FK).
   - What's unclear: Whether nullable FK causes Hibernate complications
   - Recommendation: Allow `userId = null` for FORGOT_PASSWORD purpose. At confirm time, look up user by `identifier` (email). This avoids coupling the unauthenticated flow to a user ID.

4. **Separate `AccountManagementController` or add methods to `UserController`?**
   - What we know: `UserController` currently has one endpoint (`GET /me`). Adding 5+ endpoints makes it large. A separate `AccountManagementController` at the same `/api/v1/users/me` prefix keeps concerns separated.
   - Recommendation: New `AccountManagementController` with `@RequestMapping("/api/v1/users/me")` — keeps `UserController` focused on profile reads.

5. **Expiry duration for email verification codes?**
   - What we know: SMS codes expire in 5 minutes (existing `EXPIRY_MINUTES = 5L` in `SmsVerificationService`). Email codes need longer — user must check inbox.
   - Recommendation: 15 minutes. Security guidance says 15–30 minutes is acceptable; 15 minutes balances security and convenience for email-delivered codes.

---

## Sources

### Primary (HIGH confidence)

- Spring Boot official docs — `https://docs.spring.io/spring-boot/reference/io/email.html` — JavaMailSender auto-config, `spring-boot-starter-mail` artifact, required properties
- Maven Central — `https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-mail/4.0.3` — confirmed `spring-boot-starter-mail:4.0.3` exists and is BOM-managed
- Codebase analysis (`SmsVerificationService.kt`, `SmsVerification.kt`, `SmsVerificationRepository.kt`, `SmsSchedulerConfig.kt`, `ConsoleSmsService.kt`) — HIGH confidence on reuse patterns

### Secondary (MEDIUM confidence)

- SuperTokens blog — `https://supertokens.com/blog/implementing-a-forgot-password-flow` — forgot-password flow design, token storage, anti-enumeration pattern (202 response)
- DEV Community — `https://dev.to/msnmongare/how-to-secure-your-forgot-password-endpoint-best-practices-for-developers-11g` — rate limiting (3 req/hr per account), token expiry (15–60 min), session invalidation after password reset
- Medium "Ghost Session" article — `https://medium.com/@mr.nt09/your-password-changed-but-your-old-sessions-didnt-the-ghost-session-bug-and-how-to-kill-it-0f0f7a8de6dc` — refresh token revocation on password change

### Tertiary (LOW confidence)

- WebSearch findings on multi-purpose verification_codes table with `purpose` discriminator — described pattern, no specific authoritative source found; reasoning from codebase analysis is HIGH confidence

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `spring-boot-starter-mail:4.0.3` confirmed in Maven Central; all other libs already in project
- Architecture: HIGH — patterns derived directly from existing `SmsVerificationService` codebase; strong isomorphism with new flows
- Pitfalls: HIGH — H2/enum pitfall confirmed from existing V1 migration decisions; ArgCaptor/doAnswer pitfall confirmed from Phase 03-02 decision in STATE.md; FK constraint order confirmed from Phase 04-01 decision
- Security flows (anti-enumeration, token revocation): MEDIUM — verified by multiple authoritative web sources

**Research date:** 2026-03-03
**Valid until:** 2026-06-03 (stable domain — Spring Boot major version would extend; keep for 90 days)
