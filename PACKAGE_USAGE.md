# Package Usage — `auth-spring-boot-starter`

Reference for consuming the starter from another Spring Boot project: how beans are exported, and the full catalog of injectable **services**, **repositories**, **models**, **enums**, and **pluggable interfaces**.

> For install / config properties see [INSTALL_INSTRUCTION.md](INSTALL_INSTRUCTION.md) and [README.md](README.md). This file is the API surface reference.

---

## 1. How beans reach your project

The starter ships `AuthAutoConfiguration`:

```kotlin
@AutoConfiguration(before = [ServletWebSecurityAutoConfiguration::class])
@AutoConfigurationPackage(basePackages = ["kz.innlab.starter"])
@ComponentScan("kz.innlab.starter")
```

Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, so **just adding the dependency** auto-registers everything under `kz.innlab.starter`:

- every `@Service`, `@Component`, `@Repository`, `@Configuration`
- every `@Entity` (into the shared JPA persistence unit)

Inject any of them by type:

```kotlin
@Service
class MyService(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService,
    private val mailService: MailService,
)
```

**Rules:**
- Keep your app's base package **outside** `kz.innlab.starter` (use your own, e.g. `com.example.app`).
- All listed types are `public` (Kotlin default). Nothing is `internal`.
- Override any pluggable default by declaring your own `@Bean` — starter backs off via `@ConditionalOnMissingBean` (see §6).

---

## 2. Services

### Auth & Account

| Service | Method | Returns / Notes |
|---------|--------|-----------------|
| **UserService** | `createUserByAdmin(email, rawPassword, name, roles?, temporary=false)` | `User`. Bypasses `registration.enabled`. Email taken → `IllegalStateException` (409). |
| | `findOrCreateGoogleUser(providerId, email, name?, picture?)` | `User`. Idempotent link/create. |
| | `findOrCreateAppleUser(...)` | `User`. |
| | `findOrCreatePhoneUser(phoneE164)` | `User` (`email=""`). Respects `registration.enabled`. Expects E.164. |
| | `findOrCreateTelegramUser(telegramUserId, telegramUsername?)` | `User`. |
| | `findById(id)` | `User` (throws if missing). |
| **LocalAuthService** | `register(email, rawPassword, name?)` | `AuthResponse`. Honors `registration.enabled` + `email-verification.enabled`. |
| | `login(email, rawPassword)` | `AuthResponse`. 401 on bad creds. |
| **TokenService** | `generateAccessToken(userId, roles, requiredActions=emptySet())` | `String` (signed JWT). |
| **RefreshTokenService** | `createToken(user)` | `String` (raw refresh token; hash persisted). |
| | `rotate(rawToken)` | `Pair<User, String>`. Reuse-detection + 10s grace (409). |
| | `revoke(rawToken)` | `Unit`. Idempotent. |
| **AccountManagementService** | `requestPasswordReset(email)` | `UUID?` (anti-enumeration). |
| | `resetPassword(verificationId, email, code, newPassword)` | Revokes all refresh tokens. |
| | `changePassword(userId, currentPassword, newPassword)` | Authenticated. |
| | `verifyEmail(email, verificationId, code)` | Clears `VERIFY_EMAIL` action. |
| | `resendEmailVerification(email)` | `UUID?`. Rate-limited 1/60s. |
| | `requestEmailChange(userId, newEmail)` / `verifyEmailChange(userId, verificationId, code)` | OTP email change. |
| | `requestPhoneChange(userId, phone)` / `verifyPhoneChange(userId, verificationId, phone, code)` | OTP phone change. |
| **AdminUserService** | `list(query?, pageable)` → `Page<User>`; `findById(id)`; `updatePassword/​updateEmail/​updatePhone/​updateProfile/​updateRoles(adminId, targetId, …)`; `deleteUser(adminId, targetId)` | Writes `admin_audit_log`. Last-admin guardrails. |

### OAuth / Phone / Telegram (social)

| Service | Method | Notes |
|---------|--------|-------|
| **GoogleOAuth2Service** | `authenticate(idToken, name?, picture?)` → `AuthResponse` | Verifies Google ID token. |
| **AppleOAuth2Service** | `authenticate(AppleAuthRequest)` → `AuthResponse` | |
| **PhoneOtpService** | `sendOtp(rawPhone)` → `OtpSendResult`; `verifyOtp(verificationId, rawPhone, code)` → `AuthResponse` | Normalizes to E.164. |
| **SmsVerificationService** | `sendCode(phoneE164)` → `OtpSendResult`; `verifyCode(verificationId, phoneE164, code)` → `Boolean` | Rate-limited 60s. |
| **VerificationCodeService** | `createCode(identifier, purpose, newValue?, userId?)` → `Pair<UUID,String>`; `verifyCode(verificationId, identifier, purpose, code)` → `VerificationCode` | Generic OTP store. Hashed, 15m TTL, 3 attempts. |
| **TelegramAuthService** | `initSession(ip?)`, `verifyCode(sessionId, code)`, `resendCode(sessionId)`, `getSessionStatus(sessionId)`, webhook handlers | |
| **OtpDeliveryService** | `sendCode(phoneE164, code)` | Orchestrates WhatsApp → SMS fallback. |

### Notifications & Mail

| Service | Method | Notes |
|---------|--------|-------|
| **NotificationService** | `sendToToken/​sendMulticast/​sendToTopic(userId, …, title, body, data)` → `UUID?`; `getHistory(userId, cursor?, size)` | |
| **DeviceTokenService** | `register(userId, platform, fcmToken, deviceId)`, `deleteByDeviceId`, `deleteAllByUser`, `listByUser`, `cleanupStaleTokens` | |
| **TopicService** | `createTopic/​deleteTopic/​validateTopicExists(name)`, `subscribe/​unsubscribe(token, topicName)` | |
| **NotificationPreferenceService** | `isChannelEnabled(userId, channel)`, `getPreferences(userId)`, `updatePreferences(userId, updates)` | |
| **MailService** (interface) | `send(to, subject, textBody?, htmlBody?, attachments)`; `sendEmail(userId, to, subject, …)` → `UUID` (tracked in `mail_history`) | See §6. |
| **ImapService** | `listInbox(offset, size, unreadOnly)`, `getMessage(n)`, `markRead/​markUnread(n)` | Requires IMAP config. |

---

## 3. Repositories

All `JpaRepository<Entity, UUID>` — you get `findById/save/delete/findAll` plus:

| Repository | Custom methods |
|-----------|----------------|
| **UserRepository** | `findByEmail`, `findByPhone`, `findByAppleProviderId`, `findByTelegramUserId`, `search(q, pageable)`, `countAdmins()` |
| **RefreshTokenRepository** | `findByTokenHash`, `findByReplacedByTokenHash`, `deleteAllByUser(user)` |
| **VerificationCodeRepository** | `existsByIdentifierAndPurposeAndCreatedAtAfter`, `deleteAllByIdentifierAndPurpose`, `deleteExpiredOrUsed(cutoff)` |
| **SmsVerificationRepository** | `existsByPhoneAndCreatedAtAfter`, `deleteAllByPhone`, `findActiveByPhone`, `deleteExpiredOrUsed` |
| **TelegramAuthSessionRepository** | `findBySessionId`, `countByIpAddressAndCreatedAtAfter`, `countByTelegramUserIdAndCreatedAtAfter`, `deleteExpired` |
| **DeviceTokenRepository** | `findByUserId`, `findByUserIdAndDeviceId`, `deleteByFcmToken`, `deleteAllByFcmTokenIn` |
| **NotificationHistoryRepository** | `findByUserIdBeforeCursor(...)`, `findByUserIdLatest(...)` |
| **NotificationTopicRepository** | `findByName`, `existsByName` |
| **NotificationPreferenceRepository** | `findByUserId`, `findByUserIdAndChannel` |
| **MailHistoryRepository** | `findByUserId(userId)` |
| **AdminAuditLogRepository** | `findByAdminIdOrderByCreatedAtDesc`, `findByTargetIdOrderByCreatedAtDesc` |

---

## 4. Models (entities, schema `auth`)

| Entity | Table | Key fields |
|--------|-------|-----------|
| **User** | `users` | `email`, `name?`, `picture?`, `passwordHash?`, `passwordTemporary`, `emailVerified`, `phone?`, `telegramUserId?`, `telegramUsername?`, `providers: Set<AuthProvider>`, `providerIds: Map`, `roles: Set<Role>`, `requiredActions: Set<RequiredAction>`, `createdAt`, `updatedAt` |
| **RefreshToken** | `refresh_tokens` | `user`, `tokenHash`, `expiresAt`, `revoked`, `usedAt?`, `replacedByTokenHash?` |
| **VerificationCode** | `verification_codes` | `identifier`, `purpose`, `codeHash`, `expiresAt`, `newValue?`, `userId?`, `used`, `attempts` |
| **SmsVerification** | `sms_verifications` | `phone`, `codeHash`, `expiresAt`, `used`, `attempts` |
| **TelegramAuthSession** | `telegram_auth_sessions` | `sessionId`, `status`, `codeHash?`, `attempts`, `telegramUserId?`, `telegramChatId?`, `ipAddress?`, `verifiedAt?` |
| **DeviceToken** | `device_tokens` | `userId`, `platform`, `fcmToken`, `deviceId` |
| **NotificationHistory** | `notification_history` | `userId`, `type`, `recipient`, `title`, `body`, `data?`, `status` |
| **NotificationTopic** | `notification_topics` | `name` |
| **NotificationPreference** | `notification_preferences` | `userId`, `channel`, `enabled` |
| **MailHistory** | `mail_history` | `userId`, `toAddress`, `subject`, `textBody?`, `htmlBody?`, `hasAttachments`, `status`, `attempts` |
| **AdminAuditLog** | `admin_audit_log` | `adminId`, `action`, `targetId?`, `before?`, `after?` |

All extend `BaseEntity` (UUID v7 PK via `Persistable`).

---

## 5. Enums

| Enum | Values |
|------|--------|
| **Role** | `USER`, `ADMIN` |
| **AuthProvider** | `GOOGLE`, `APPLE`, `LOCAL`, `TELEGRAM` |
| **RequiredAction** | `UPDATE_PASSWORD`, `VERIFY_EMAIL`, `VERIFY_PHONE` |
| **VerificationPurpose** | `FORGOT_PASSWORD`, `CHANGE_EMAIL`, `CHANGE_PHONE`, `VERIFY_EMAIL` |
| **TelegramSessionStatus** | `PENDING`, `CODE_SENT`, `VERIFIED`, `EXPIRED` |
| **Platform** | `ANDROID`, `IOS`, `WEB` |
| **NotificationChannel** | `PUSH`, `EMAIL` |
| **NotificationType** | `SINGLE`, `MULTICAST`, `TOPIC` |
| **NotificationStatus** / **MailStatus** | `PENDING`, `SENT`, `FAILED` |

---

## 6. Pluggable interfaces (override the console defaults)

Declare your own `@Bean` — starter's default backs off automatically.

| Interface | Method | Default bean |
|-----------|--------|--------------|
| **SmsService** | `sendCode(phone, code)` | `ConsoleSmsService` (logs). `TwilioSmsService` via config. |
| **WhatsAppService** | `sendCode(phone, code)` | **none** (optional; OtpDeliveryService injects `Optional`). Must throw on failure for SMS fallback. |
| **EmailService** | `sendCode(to, code, purpose)` | `ConsoleEmailService` (logs). |
| **MailService** | `send(...)`, `sendEmail(...)` | `ConsoleMailService` / `SmtpMailService` / `ExternalMailService` by config. |
| **PushService** | `sendToToken/​sendMulticast/​sendToTopic/​subscribeToTopic` | `FirebasePushService` when `app.firebase.enabled=true`. |
| **TelegramBotService** | `sendMessage(chatId, text)` | console default. |

```kotlin
@Configuration
class MyProviders {
    @Bean
    fun emailService(): EmailService = object : EmailService {
        override fun sendCode(to: String, code: String, purpose: String) { /* SendGrid */ }
    }
}
```

---

## 7. Key DTOs

```kotlin
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val requiredActions: List<String> = emptyList()
)

data class OtpSendResult(
    val verificationId: UUID,
    val resendAvailableAt: Instant,   // when a new code may be requested
    val retryAfterSeconds: Long       // cooldown length (for countdown UI)
)
```

---

## 8. Recipes

### Create user without registration, then issue tokens

```kotlin
@Service
class Onboarding(
    private val userService: UserService,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService
) {
    @Transactional
    fun createAndLogin(email: String, password: String, name: String? = null): AuthResponse {
        val user = userService.createUserByAdmin(email, password, name, roles = setOf(Role.USER))
        val access = tokenService.generateAccessToken(user.id, user.roles, user.requiredActions)
        val refresh = refreshTokenService.createToken(user)
        return AuthResponse(access, refresh, user.requiredActions.map { it.name })
    }
}
```

### Phone user (no email)

```kotlin
import kz.innlab.starter.authentication.service.normalizeToE164

@Transactional
fun createPhoneUserAndLogin(rawPhone: String): AuthResponse {
    val phoneE164 = normalizeToE164(rawPhone)                 // "+77011234567"
    val user = userService.findOrCreatePhoneUser(phoneE164)  // idempotent; email=""
    val access = tokenService.generateAccessToken(user.id, user.roles, user.requiredActions)
    val refresh = refreshTokenService.createToken(user)
    return AuthResponse(access, refresh, user.requiredActions.map { it.name })
}
```

> Cookie mode (`app.auth.cookie.enabled=true`): any controller returning `AuthResponse` gets `Set-Cookie` automatically via `AuthResponseCookieAdvice` — no extra code.

---

## 9. Gotchas

- **`findOrCreatePhoneUser` / social creators respect `registration.enabled`** → throw when registration is closed. `createUserByAdmin` **bypasses** it (admin-trusted).
- **Phone users have `email = ""`, not `null`** (partial unique index `WHERE email != ''`).
- **Always normalize phone** with `normalizeToE164()` before `findOrCreatePhoneUser` / `findByPhone` — else duplicates.
- **Access control is via `requiredActions`** (JWT claim + `RequiredActionFilter`), not the `emailVerified` flag directly.
- **Your migrations** go in `classpath:db/migration`; starter's are `classpath:db/migration-auth` (schema `auth`) via a separate `authFlyway` bean — don't mix.
- **Protect any endpoint you build** that creates users — add a `SecurityFilterChain` with `hasRole("ADMIN")` (see INSTALL §0).
