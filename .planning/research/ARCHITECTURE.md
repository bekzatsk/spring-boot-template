# Architecture Research

**Domain:** Spring Boot 4 — FCM Push Notifications + Email SMTP/IMAP integration (v6.0 Notifications milestone)
**Researched:** 2026-03-03
**Confidence:** HIGH (Firebase Admin SDK and Spring Mail SMTP patterns well-established; IMAP via Jakarta Mail direct API)

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     HTTP Clients (mobile/web)                    │
│   POST /api/v1/notifications/**   POST/GET /api/v1/mail/**       │
└───────────────────┬─────────────────────────┬───────────────────┘
                    │  JWT Bearer              │  JWT Bearer
┌───────────────────▼─────────────────────────▼───────────────────┐
│          SecurityConfig (EXISTING — UNCHANGED)                    │
│          /api/v1/auth/** permitAll                               │
│          /api/**          authenticated                          │
└───────────────────┬─────────────────────────┬───────────────────┘
                    │                         │
     ┌──────────────▼──────────┐   ┌──────────▼──────────────────┐
     │  notification/ (NEW)    │   │  mail/ (NEW)                 │
     │                         │   │                              │
     │  DeviceTokenController  │   │  MailController              │
     │  NotificationController │   │  MailService (interface)     │
     │  ─────────────────────  │   │  SmtpMailService (impl)      │
     │  PushService (interface)│   │  ImapMailService (impl)      │
     │  FcmPushService (impl)  │   └──────────┬───────────────────┘
     │  DeviceTokenService     │              │
     └──────────┬──────────────┘              │
                │                             │
     ┌──────────▼──────────┐       ┌──────────▼──────────────────┐
     │  device_tokens table│       │  JavaMailSender (Spring)     │
     │  (Flyway V3)        │       │  Jakarta Mail Session        │
     │  DeviceToken entity │       │  SMTP send / IMAP receive    │
     └─────────────────────┘       └─────────────────────────────┘
                │
     ┌──────────▼──────────┐
     │  Firebase Admin SDK │
     │  FirebaseApp        │
     │  FirebaseMessaging  │
     │  (FCM HTTP v1 API)  │
     └─────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Status |
|-----------|----------------|--------|
| `notification/model/DeviceToken` | Stores FCM registration token per user+platform+deviceId | NEW |
| `notification/repository/DeviceTokenRepository` | CRUD for device tokens; find by userId; delete stale | NEW |
| `notification/service/PushService` | Interface: send to token, topic, multicast | NEW |
| `notification/service/FcmPushService` | Firebase Admin SDK impl; FirebaseMessaging calls | NEW |
| `notification/service/ConsolePushService` | Dev/test fallback — logs instead of calling FCM | NEW |
| `notification/service/DeviceTokenService` | Register/unregister tokens; topic subscribe/unsubscribe | NEW |
| `notification/controller/DeviceTokenController` | POST /register, DELETE /unregister — authenticated | NEW |
| `notification/controller/NotificationController` | POST /send — authenticated (admin or internal) | NEW |
| `notification/dto/*` | RegisterTokenRequest, SendNotificationRequest, NotificationResponse | NEW |
| `config/FirebaseConfig` | FirebaseApp.initializeApp() + FirebaseMessaging bean | NEW |
| `config/NotificationConfig` | ConsolePushService fallback via @ConditionalOnMissingBean | NEW |
| `mail/service/MailService` | Interface: sendEmail(to, subject, body, html, attachments?) | NEW |
| `mail/service/SmtpMailService` | JavaMailSender impl; MimeMessage with HTML + attachments | NEW |
| `mail/service/ImapMailService` | Direct Jakarta Mail Store; IMAP folder + message listing | NEW |
| `mail/controller/MailController` | GET /inbox, GET /messages/{id}, POST /send | NEW |
| `mail/dto/*` | SendMailRequest, MailMessageResponse, InboxResponse | NEW |
| `authentication/service/EmailService` | MODIFY: add `sendHtml(to, subject, body)` default method | MODIFY |
| `authentication/service/ConsoleEmailService` | MODIFY: implement sendHtml() stub | MODIFY |
| `config/SmsSchedulerConfig` | No change — email bean fallback registration stays here | UNCHANGED |
| `config/SecurityConfig` | No change — /api/** authenticated covers new routes | UNCHANGED |

## Recommended Project Structure

```
src/main/kotlin/kz/innlab/template/
├── config/
│   ├── FirebaseConfig.kt           # NEW — FirebaseApp + FirebaseMessaging beans
│   ├── NotificationConfig.kt       # NEW — ConsolePushService fallback
│   ├── SecurityConfig.kt           # UNCHANGED
│   └── SmsSchedulerConfig.kt       # UNCHANGED (email fallback bean stays here)
│
├── notification/                   # NEW domain
│   ├── model/
│   │   └── DeviceToken.kt          # entity: id(UUID v7), userId, token, platform, deviceId
│   ├── repository/
│   │   └── DeviceTokenRepository.kt
│   ├── service/
│   │   ├── PushService.kt          # interface
│   │   ├── FcmPushService.kt       # Firebase Admin SDK impl
│   │   ├── ConsolePushService.kt   # dev/test stub
│   │   └── DeviceTokenService.kt   # token register/unregister/upsert
│   ├── controller/
│   │   ├── DeviceTokenController.kt
│   │   └── NotificationController.kt
│   └── dto/
│       ├── RegisterTokenRequest.kt
│       ├── SendNotificationRequest.kt
│       └── NotificationResponse.kt
│
├── mail/                           # NEW domain
│   ├── service/
│   │   ├── MailService.kt          # interface
│   │   ├── SmtpMailService.kt      # JavaMailSender impl (SMTP send + EmailService)
│   │   └── ImapMailService.kt      # Jakarta Mail Store impl (IMAP receive)
│   ├── controller/
│   │   └── MailController.kt
│   └── dto/
│       ├── SendMailRequest.kt
│       ├── MailMessageResponse.kt
│       └── InboxResponse.kt
│
├── authentication/                 # EXISTING — minimal changes
│   └── service/
│       ├── EmailService.kt         # MODIFY: add sendHtml() default method
│       └── ConsoleEmailService.kt  # MODIFY: stub sendHtml()
│
└── shared/
    └── model/
        └── BaseEntity.kt           # UNCHANGED — DeviceToken extends this
```

### Structure Rationale

- **notification/ as new top-level domain:** Mirrors the existing `user/` and `authentication/` domain structure. FCM concerns are fully self-contained and do not bleed into `authentication/`.
- **mail/ as separate top-level domain:** Email sending for notifications differs from auth verification codes. Keeping them separate lets `authentication/` remain focused. `SmtpMailService` implements both `EmailService` (auth codes) and `MailService` (general email) so no existing callers change.
- **FirebaseConfig and NotificationConfig in config/:** All infrastructure singleton beans live in `config/`. FirebaseApp is a JVM-wide singleton, not a domain concern.
- **EmailService stays in authentication/:** The interface is already used by `VerificationCodeService`. Extending it with a default `sendHtml()` method avoids breaking existing callers and avoids moving files.

## Architectural Patterns

### Pattern 1: Interface + ConditionalOnMissingBean (existing pattern — extend to push)

**What:** `PushService` defined as interface; `FcmPushService` as `@Service`. `ConsolePushService` fallback registered via `@ConditionalOnMissingBean` in `NotificationConfig` — exactly mirrors `SmsService`/`EmailService` pattern in `SmsSchedulerConfig`.
**When to use:** Any service backed by an external vendor. Allows test profiles to skip real Firebase without mocking.
**Trade-offs:** One extra class per service, but no Firebase dependency leaks into tests.

**Example:**
```kotlin
// notification/service/PushService.kt
interface PushService {
    fun sendToToken(token: String, title: String, body: String, data: Map<String, String> = emptyMap()): String
    fun sendToTopic(topic: String, title: String, body: String, data: Map<String, String> = emptyMap()): String
    fun sendMulticast(tokens: List<String>, title: String, body: String, data: Map<String, String> = emptyMap()): Int
}

// config/NotificationConfig.kt
@Configuration
class NotificationConfig {
    @Bean
    @ConditionalOnMissingBean(PushService::class)
    fun pushService(): PushService = ConsolePushService()
}
```

### Pattern 2: FirebaseApp singleton initialization with ConditionalOnProperty guard

**What:** `FirebaseConfig` reads service account JSON from a path set via env var, calls `FirebaseApp.initializeApp()` once, and exposes `FirebaseMessaging` as a `@Bean`. The entire config is guarded by `@ConditionalOnProperty(name = ["app.firebase.enabled"], havingValue = "true")` so dev/test profiles skip Firebase entirely — `NotificationConfig` then registers `ConsolePushService` via `@ConditionalOnMissingBean`.
**When to use:** Always. FirebaseApp must only be initialized once per JVM — double initialization throws `IllegalStateException`.
**Trade-offs:** The conditional property guard adds one config line per environment but completely eliminates Firebase dependency in dev/test.

**Example:**
```kotlin
@Configuration
@ConditionalOnProperty(name = ["app.firebase.enabled"], havingValue = "true")
class FirebaseConfig(
    @Value("\${app.firebase.service-account-path}") private val serviceAccountPath: String
) {
    @Bean
    fun firebaseApp(): FirebaseApp {
        val credentials = GoogleCredentials.fromStream(FileInputStream(serviceAccountPath))
        val options = FirebaseOptions.builder().setCredentials(credentials).build()
        return if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(options)
               else FirebaseApp.getInstance()
    }

    @Bean
    fun firebaseMessaging(app: FirebaseApp): FirebaseMessaging =
        FirebaseMessaging.getInstance(app)
}
```

application.yaml additions:
```yaml
app:
  firebase:
    enabled: ${FIREBASE_ENABLED:false}          # false in dev, true in prod
    service-account-path: ${FIREBASE_SERVICE_ACCOUNT_PATH:}
```

### Pattern 3: JavaMailSender for SMTP; raw Jakarta Mail Store for IMAP

**What:** Spring's `JavaMailSender` (from `spring-boot-starter-mail`) handles SMTP with full auto-configuration from `spring.mail.*` properties. IMAP receiving has no Spring abstraction — use `jakarta.mail.Store` directly via a `Session` built from `app.mail.imap.*` properties. Open and close the Store per request — never hold it open as a bean.
**When to use:** SMTP via `JavaMailSender` always. IMAP via raw Jakarta Mail only for inbox listing/reading. Spring Integration is overkill for simple inbox access.
**Trade-offs:** Two separate credential configurations (SMTP vs IMAP). IMAP connection overhead per request is acceptable with Virtual Threads; long-lived IMAP connections go stale server-side (typically 30 min timeout).

**Example:**
```kotlin
// mail/service/SmtpMailService.kt — implements both interfaces
@Service
class SmtpMailService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.username}") private val from: String
) : MailService, EmailService {

    // EmailService — used by VerificationCodeService (existing auth flows)
    override fun sendCode(to: String, code: String, purpose: String) {
        sendEmail(to, "Your $purpose code", "Your code is: $code", html = false)
    }

    // MailService — used by MailController (new general email)
    override fun sendEmail(to: String, subject: String, body: String, html: Boolean, attachments: List<Attachment>) {
        val mime = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mime, attachments.isNotEmpty(), "UTF-8")
        helper.setFrom(from)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(body, html)
        attachments.forEach { helper.addAttachment(it.filename, it.dataSource) }
        mailSender.send(mime)
    }
}

// mail/service/ImapMailService.kt
@Service
class ImapMailService(
    @Value("\${app.mail.imap.host}") private val host: String,
    @Value("\${app.mail.imap.port}") private val port: Int,
    @Value("\${app.mail.imap.username}") private val username: String,
    @Value("\${app.mail.imap.password}") private val password: String
) {
    fun listInbox(limit: Int = 20): List<MailMessageResponse> {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", host)
            put("mail.imaps.port", port.toString())
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect(host, username, password)
        return try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val messages = inbox.messages.takeLast(limit)
                messages.map { it.toResponse() }
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
    }
}
```

## Data Flow

### FCM Device Token Registration

```
Mobile App (on login or startup)
    |
    POST /api/v1/notifications/tokens/register
    |  Body: { platform: "android", deviceId: "abc123", token: "FCM_TOKEN" }
    |  Header: Authorization: Bearer <JWT>
    |
    DeviceTokenController  (extracts userId from JWT sub claim)
    |
    DeviceTokenService.register(userId, platform, deviceId, token)
    |  findByUserIdAndDeviceId -> update token if exists, insert if new
    |
    device_tokens table (PostgreSQL)
    |
    200 OK
```

### Push Notification Send

```
Authenticated caller
    |
    POST /api/v1/notifications/send
    |  Body: { target: "token|topic", value: "...", title, body, data }
    |
    NotificationController
    |
    FcmPushService (or ConsolePushService in dev)
    |  Message.builder().setToken/setTopic().setNotification().putAllData().build()
    |  FirebaseMessaging.getInstance().send(message)
    |
    FCM HTTP v1 API (Google)  -->  Device (Android/iOS/Web)
    |
    String messageId  -->  200 OK { messageId }
```

### Email Send (SMTP)

```
Authenticated caller
    |
    POST /api/v1/mail/send
    |  Body: { to, subject, body, html: true, attachments: [] }
    |
    MailController
    |
    SmtpMailService.sendEmail(...)
    |  JavaMailSender.createMimeMessage()
    |  MimeMessageHelper(multipart = attachments present)
    |  helper.setText(body, html=true)
    |  JavaMailSender.send(mime)
    |
    SMTP Server (Gmail/SendGrid/custom)
    |
    202 Accepted
```

### Email Receive (IMAP)

```
Authenticated caller
    |
    GET /api/v1/mail/inbox?limit=20
    |
    MailController
    |
    ImapMailService.listInbox(limit)
    |  Session.getInstance(imapProps)
    |  Store.connect(host, user, pass)      <- open per request
    |  Folder("INBOX").open(READ_ONLY)
    |  fetch messages, map to MailMessageResponse
    |  Folder.close(false), Store.close()  <- always close in finally
    |
    List<MailMessageResponse>  -->  200 OK
```

### Auth Email (Existing Path — Unchanged)

```
VerificationCodeService.sendCode(email, code, purpose)
    |
    EmailService.sendCode(to, code, purpose)   <- existing interface, unchanged callers
    |
    SmtpMailService.sendCode() in prod         (implements EmailService)
    ConsoleEmailService.sendCode() in dev      (fallback via @ConditionalOnMissingBean)
```

`SmtpMailService` implements both `EmailService` and `MailService`. When `spring.mail.host` is configured, Spring auto-configures `JavaMailSender` and `SmtpMailService` becomes the active `EmailService` bean, replacing `ConsoleEmailService` without any changes to `SmsSchedulerConfig`.

## Integration Points

### New vs. Modified Components

| Component | Change Type | Reason |
|-----------|-------------|--------|
| `config/FirebaseConfig.kt` | NEW | Firebase Admin SDK singleton init |
| `config/NotificationConfig.kt` | NEW | ConsolePushService fallback (mirrors SmsSchedulerConfig pattern) |
| `notification/` package | NEW | Full FCM domain |
| `mail/` package | NEW | Full email domain |
| `authentication/service/EmailService.kt` | MODIFY | Add `sendHtml(to, subject, body)` default method |
| `authentication/service/ConsoleEmailService.kt` | MODIFY | Implement `sendHtml()` stub |
| `config/SmsSchedulerConfig.kt` | UNCHANGED | Email fallback bean stays; scheduler stays |
| `config/SecurityConfig.kt` | UNCHANGED | `/api/**` authenticated already covers all new routes |
| `application.yaml` | MODIFY | Add `app.firebase.*`, `spring.mail.*`, `app.mail.imap.*` sections |
| Flyway V3 migration | NEW | `device_tokens` table |

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Firebase FCM | `firebase-admin` JAR; GoogleCredentials from service account JSON | Use FCM HTTP v1 API (legacy HTTP API is deprecated). Guard with `@ConditionalOnProperty(app.firebase.enabled)` so dev/test skips real Firebase and ConsolePushService activates. |
| SMTP Server | `spring-boot-starter-mail` -> `JavaMailSender` auto-configured from `spring.mail.*` | Works with Gmail (587/STARTTLS), SendGrid relay, or any SMTP server. `JavaMailSenderAutoConfiguration` activates when `spring.mail.host` is present. |
| IMAP Server | Direct `jakarta.mail.Store` via `Session.getInstance()` | No Spring abstraction exists for IMAP receive — raw Jakarta Mail is the standard approach. Open/close Store per request to avoid stale connections. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `notification/` to `user/` | `DeviceTokenService` takes `userId: UUID` — resolved from JWT sub claim in controller layer | No direct User entity dependency in notification domain — userId by value only, not by JPA relation |
| `mail/` to `authentication/` | `SmtpMailService` implements `EmailService` from `authentication/service/` | Keeps auth email path working without changes to any existing callers in `VerificationCodeService` etc. |
| `notification/` to Firebase | `FcmPushService` injects `FirebaseMessaging` bean from `FirebaseConfig` | In dev/test, `FirebaseConfig` is disabled and `ConsolePushService` is injected instead — zero FCM calls |
| `mail/` IMAP to mail server | `ImapMailService` creates a new `Session` and `Store` per call | Stateful connections — always open/close in try/finally; never hold as a bean field |

## Suggested Build Order

Each step is independently testable. Dependencies flow in one direction.

```
Step 1: Firebase Config + PushService interface + ConsolePushService
        FirebaseConfig.kt, NotificationConfig.kt, PushService.kt, ConsolePushService.kt
        Verifies: Firebase initializes without error in prod profile;
                  ConsolePushService registered when app.firebase.enabled=false

Step 2: DeviceToken entity + Flyway V3 migration
        DeviceToken.kt, DeviceTokenRepository.kt, V3__device_tokens.sql
        Verifies: Schema created; BaseEntity UUID v7 IDs work correctly

Step 3: FcmPushService implementation
        FcmPushService.kt (single token, topic, multicast)
        Verifies: FCM send returns messageId; invalid token exception handled gracefully

Step 4: DeviceTokenService + DeviceTokenController
        DeviceTokenService.kt, DeviceTokenController.kt, DTOs
        POST /api/v1/notifications/tokens/register
        DELETE /api/v1/notifications/tokens/{deviceId}
        Verifies: Token upsert works; user isolation enforced via JWT sub claim

Step 5: NotificationController (send endpoint)
        NotificationController.kt
        POST /api/v1/notifications/send
        Verifies: JWT auth guard works; delegates to PushService correctly

Step 6: spring-boot-starter-mail dependency + SmtpMailService
        Add dependency, add spring.mail.* config, SmtpMailService.kt
        SmtpMailService implements EmailService + MailService
        Verifies: Sends real email via SMTP; existing auth email codes still work
                  (ConsoleEmailService fallback replaced transparently)

Step 7: MailController (send endpoint)
        MailController.kt, SendMailRequest.kt, MailMessageResponse.kt
        POST /api/v1/mail/send
        Verifies: HTML + attachment support; MIME construction correct

Step 8: ImapMailService + MailController (receive endpoints)
        ImapMailService.kt, app.mail.imap.* config
        GET /api/v1/mail/inbox
        GET /api/v1/mail/messages/{id}
        Verifies: IMAP open/close works; messages mapped to DTO; try/finally prevents leaks
```

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0–10k users | Monolith is fine. FCM Admin SDK batches internally. Virtual Threads handle concurrent SMTP/IMAP I/O without thread pool exhaustion. |
| 10k–100k users | Device token cleanup job becomes important — add `@Scheduled` to `NotificationConfig` (mirrors `cleanupExpiredCodes()` in `SmsSchedulerConfig`) deleting tokens inactive > 90 days. |
| 100k+ users | FCM multicast limited to 500 tokens per call — add batching logic in `FcmPushService`. SMTP sending should move async (`@Async` or outbox pattern). IMAP fetching should be cached or event-driven (IMAP IDLE). |

### Scaling Priorities

1. **First bottleneck — stale device tokens:** Tokens accumulate as users reinstall apps. Add a scheduled cleanup in `NotificationConfig` identical to the `cleanupExpiredCodes()` job in `SmsSchedulerConfig`.
2. **Second bottleneck — synchronous SMTP on request thread:** For high volume, move email sending to a background thread via Spring `@Async` rather than blocking the HTTP Virtual Thread.

## Anti-Patterns

### Anti-Pattern 1: Holding IMAP Store open across requests

**What people do:** Create a `Store` bean, connect at startup, reuse it as a singleton across all requests.
**Why it is wrong:** IMAP connections time out server-side (typically 30 minutes). The stored `Store` goes stale and throws `MessagingException`. The bean becomes unusable until app restart. Virtual Threads make per-request connection cheap — there is no reason to share state.
**Do this instead:** Open `Store`, do work, close `Store` in a single `try/finally` block per request invocation.

### Anti-Pattern 2: Calling FirebaseApp.initializeApp() without checking getApps()

**What people do:** Call `FirebaseApp.initializeApp(options)` unconditionally in the `@Bean` method.
**Why it is wrong:** Throws `IllegalStateException: FirebaseApp name [DEFAULT] already exists!` on hot reload, test re-runs, or any context that initializes twice.
**Do this instead:** Guard with `if (FirebaseApp.getApps().isEmpty()) initializeApp(options) else getInstance()`.

### Anti-Pattern 3: Storing FCM token in the User entity

**What people do:** Add an `fcmToken: String?` column to the `users` table.
**Why it is wrong:** One user has multiple devices (phone, tablet, web). A single column overwrites the previous device's token silently, breaking push for all other devices.
**Do this instead:** Separate `device_tokens` table with composite uniqueness on `(user_id, device_id)`. `DeviceToken` extends `BaseEntity` (UUID v7 id, userId, platform enum, deviceId, token, updatedAt).

### Anti-Pattern 4: Implementing notification logic inside the authentication package

**What people do:** Call `FcmPushService.sendToToken()` from inside `LocalAuthService` or `AccountManagementService` (e.g., "push on login event").
**Why it is wrong:** Crosses domain boundaries. Auth services depend on the notification domain; circular concerns emerge and auth becomes harder to test in isolation.
**Do this instead:** Keep `notification/` standalone. If auth events should trigger pushes, coordinate at the controller layer — auth service finishes, then the controller calls the push service. Or use an application event (`ApplicationEventPublisher`) to decouple the domains.

## Sources

- [Using Firebase Cloud Messaging in Spring Boot Applications — Baeldung](https://www.baeldung.com/spring-fcm) — MEDIUM confidence
- [Firebase Admin SDK FCM HTTP v1 API — GitHub reference implementation](https://github.com/mdtalalwasim/Firebase-Cloud-Messaging-FCM-v1-API-Spring-boot) — MEDIUM confidence
- [Guide to Spring Email — Baeldung](https://www.baeldung.com/spring-email) — HIGH confidence (official Spring patterns)
- [Spring Framework Email Reference — Official Docs](https://docs.spring.io/spring-framework/reference/integration/email.html) — HIGH confidence
- [Spring Integration Mail (IMAP) — Official Docs](https://docs.spring.io/spring-integration/reference/mail.html) — HIGH confidence
- [spring-boot-starter-mail — MVN Repository](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-mail) — HIGH confidence

---
*Architecture research for: Spring Boot 4 FCM push notifications + SMTP/IMAP email integration*
*Researched: 2026-03-03*
