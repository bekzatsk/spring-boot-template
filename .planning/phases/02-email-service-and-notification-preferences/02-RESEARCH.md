# Phase 2: Email Service and Notification Preferences — Research

**Researched:** 2026-03-03
**Status:** Complete

## RESEARCH COMPLETE

## 1. Email Sending via SMTP

### Spring Boot Mail Starter
- **Dependency:** `spring-boot-starter-mail` — provides `JavaMailSender` auto-configuration
- Spring Boot 4.x auto-configures `JavaMailSenderImpl` when `spring.mail.host` is set
- Properties: `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password`, `spring.mail.properties.mail.smtp.auth`, `spring.mail.properties.mail.smtp.starttls.enable`
- For SSL/TLS: `spring.mail.properties.mail.smtp.ssl.enable=true`

### Async Sending (Virtual Thread Pinning)
- **Critical:** `JavaMailSender.send()` internally uses `synchronized` blocks (javax.mail Session) that **pin Virtual Threads**
- Same pattern as FCM send — must use `@Async` on a **separate bean** (not self-invocation)
- Follow exact `NotificationDispatcher` pattern: `MailDispatcher` as separate `@Service` with `@Async @Transactional` methods
- `@EnableAsync` already present on `FirebaseConfig.kt` — reusable

### Sending Plain Text, HTML, and Attachments
- **Plain text:** `SimpleMailMessage` — but too simple for our needs (no HTML, no attachments)
- **HTML + Attachments:** `MimeMessage` via `JavaMailSender.createMimeMessage()` + `MimeMessageHelper`
  ```kotlin
  val helper = MimeMessageHelper(message, true) // true = multipart
  helper.setTo(to)
  helper.setSubject(subject)
  helper.setText(body, isHtml) // second param = isHtml
  helper.addAttachment(filename, ByteArrayResource(bytes), contentType)
  ```
- Attachments as `InputStreamSource` (Spring's `ByteArrayResource` works)

### MailService Interface Design
- New `MailService` interface in `notification/service/` (NOT in `authentication/service/`)
- Methods: `send(to, subject, textBody, htmlBody?, attachments?)`
- **SmtpMailService** implements BOTH:
  - `MailService` (new general email interface)
  - `EmailService` (existing `authentication/service/EmailService` for verification codes)
- This means `SmtpMailService.sendCode()` delegates to `send()` internally
- `@ConditionalOnProperty(name = ["app.mail.smtp.host"])` gates `SmtpMailService` bean creation
- **ConsoleMailService** implements BOTH interfaces as fallback via `@ConditionalOnMissingBean`

### Retry Mechanism
- Spring Retry (`@Retryable`) would add a dependency. For a template, **manual retry loop** is simpler and dependency-free
- Implementation: in `MailDispatcher.dispatchEmail()`, wrap `JavaMailSender.send()` in a for-loop with configurable `max-attempts` (default 3) and `delay-ms` (default 5000)
- Use `Thread.sleep(delayMs)` between attempts (runs on platform thread via @Async, not Virtual Thread)
- Log each attempt and final failure

### Email History (mail_history table)
- Separate from `notification_history` — different schema needs (to/from/subject vs token/topic)
- Fields: `id UUID PK`, `user_id UUID FK`, `to_address VARCHAR(255)`, `subject VARCHAR(255)`, `text_body TEXT`, `html_body TEXT`, `has_attachments BOOLEAN DEFAULT FALSE`, `status VARCHAR(10) DEFAULT 'PENDING'`, `attempts INT DEFAULT 0`, `created_at TIMESTAMPTZ`
- Status values: `PENDING`, `SENT`, `FAILED` (reuse `NotificationStatus` enum or create `MailStatus`)
- Controller returns 202 Accepted with `mailId` (same pattern as notifications)

### Configuration
- application.yaml structure:
  ```yaml
  app:
    mail:
      smtp:
        host: ${SMTP_HOST:}
        port: ${SMTP_PORT:587}
        username: ${SMTP_USERNAME:}
        password: ${SMTP_PASSWORD:}
        from: ${SMTP_FROM:noreply@example.com}
        ssl-enabled: ${SMTP_SSL_ENABLED:false}
      imap:
        host: ${IMAP_HOST:}
        port: ${IMAP_PORT:993}
        username: ${IMAP_USERNAME:}
        password: ${IMAP_PASSWORD:}
        ssl-enabled: ${IMAP_SSL_ENABLED:true}
      retry:
        max-attempts: ${MAIL_RETRY_MAX_ATTEMPTS:3}
        delay-ms: ${MAIL_RETRY_DELAY_MS:5000}
  ```
- Map to `@ConfigurationProperties(prefix = "app.mail")` data class
- `spring.mail.*` auto-config for `JavaMailSender` — map from `app.mail.smtp.*`

## 2. IMAP Inbox Access

### Jakarta Mail (javax.mail replacement)
- Spring Boot 4.x uses Jakarta Mail (`jakarta.mail.*`) — shipped with `spring-boot-starter-mail`
- IMAP access: `Session.getInstance(properties)` → `store = session.getStore("imaps")` → `store.connect(host, port, username, password)` → `folder = store.getFolder("INBOX")`
- **Per-request lifecycle:** Open store → get folder → operate → close folder → close store (in try/finally)
- Never hold IMAP Store as a Spring bean singleton (documented decision)

### Message Listing
- `folder.open(Folder.READ_ONLY)` for listing, `READ_WRITE` for marking
- Get messages: `folder.getMessages()` returns array — IMAP message numbers are 1-based
- **Pagination:** `folder.getMessages(start, end)` where start/end are IMAP message numbers
  - Total count: `folder.getMessageCount()`
  - Offset-based: `start = total - offset - size + 1`, `end = total - offset` (newest first)
  - Return in reverse order (newest first)
- **Unread filter:** `folder.search(FlagTerm(Flags(Flags.Flag.SEEN), false))`
- Fetch profile for performance: `FetchProfile()` with `ENVELOPE`, `FLAGS`, `CONTENT_INFO` — prefetches headers without downloading body

### Message Details
- Subject: `message.subject`
- From: `message.from` (InternetAddress array)
- Date: `message.sentDate` or `message.receivedDate`
- Read/unread: `message.flags.contains(Flags.Flag.SEEN)`
- Has attachments: Check if multipart content has `Part.ATTACHMENT` disposition
- Body extraction: Recursive multipart walk — find `text/plain` and `text/html` parts
- Attachment metadata: filename, contentType, size from `Part.getFileName()`, `Part.getContentType()`, `Part.getSize()`

### Mark Read/Unread
- `folder.open(Folder.READ_WRITE)`
- `message.setFlag(Flags.Flag.SEEN, true)` for read, `false` for unread
- IMAP persists flag changes server-side

### Service Design
- `ImapService` in `notification/service/` — NOT an interface (single implementation, no fallback needed for IMAP)
- Constructor takes `MailProperties` for IMAP config
- Methods: `listInbox(offset, size, unreadOnly)`, `getMessage(messageNumber)`, `markRead(messageNumber)`, `markUnread(messageNumber)`
- Each method opens/closes Store and Folder internally
- When IMAP is not configured (host empty): methods throw `IllegalStateException("IMAP not configured")`

## 3. Notification Preferences

### Table Design
- `notification_preferences` table:
  ```sql
  CREATE TABLE notification_preferences (
      id UUID PRIMARY KEY,
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      channel VARCHAR(10) NOT NULL CHECK (channel IN ('PUSH', 'EMAIL')),
      enabled BOOLEAN NOT NULL DEFAULT TRUE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CONSTRAINT uq_notification_preferences_user_channel UNIQUE (user_id, channel)
  );
  ```
- Flyway V4 migration (V3 is notifications, V4 is preferences + mail_history)

### Opt-Out Model
- Default behavior when no row exists: channel is **enabled**
- Only create rows when user explicitly disables a channel
- Service logic: `fun isChannelEnabled(userId, channel): Boolean` — if no row found, return `true`

### Endpoints
- `GET /api/v1/notifications/preferences` — return current state (both channels, with defaults applied)
- `PUT /api/v1/notifications/preferences` — partial update (only included channels are changed)
- Both on existing `NotificationController` — avoids new controller proliferation

### Integration Points
- `NotificationService.sendToToken/sendMulticast/sendToTopic` checks `isChannelEnabled(userId, PUSH)` before dispatching
- Future `MailService` send checks `isChannelEnabled(userId, EMAIL)` before dispatching
- If disabled: skip silently (no error, no history entry), return null or skip ID

## 4. GreenMail for Integration Tests

### Dependency
- `com.icegreen:greenmail-spring:2.1.3` (test scope) — in-process SMTP + IMAP server
- Spring Boot auto-detection: `@RegisterExtension` or `@Rule` with GreenMail instance
- GreenMail for Spring Boot 4.x: Use `GreenMailExtension` (JUnit 5)

### Test Setup
```kotlin
companion object {
    @JvmField
    @RegisterExtension
    val greenMail = GreenMailExtension(ServerSetupTest.SMTP_IMAP)
        .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
}
```

### Test Properties
- Override `spring.mail.host`, `spring.mail.port` in test to point to GreenMail's SMTP
- Override `app.mail.imap.host`, `app.mail.imap.port` to point to GreenMail's IMAP
- Use `@DynamicPropertySource` to inject GreenMail ports

### What to Test
- Send email via API → verify received in GreenMail (`greenMail.receivedMessages`)
- Send with attachment → verify attachment present
- List inbox via IMAP → verify message listing
- Mark read/unread → verify flag changes
- Retry on failure → verify attempts count
- Console fallback when SMTP not configured
- Notification preferences: disable PUSH → send notification → verify not dispatched
- Notification preferences: disable EMAIL → send email → verify not dispatched

## 5. Codebase Patterns to Follow

### Existing Patterns (MUST match)
1. **@ConditionalOnMissingBean fallback:** `ConsolePushService` via `NotificationConfig`, `ConsoleSmsService` via `SmsSchedulerConfig`, `ConsoleEmailService` via `SmsSchedulerConfig`
2. **Separate @Async bean:** `NotificationDispatcher` pattern — `MailDispatcher` follows same
3. **Service → Dispatcher → External call:** `NotificationService` → `NotificationDispatcher` → `PushService`; `MailService(new)` → `MailDispatcher` → `JavaMailSender`
4. **202 Accepted with tracking ID:** Both push and email sends return ID
5. **PENDING/SENT/FAILED status:** History tracking pattern
6. **UUID v7 via BaseEntity:** All new entities extend BaseEntity
7. **Controller test pattern:** `@SpringBootTest` + `@AutoConfigureMockMvc` + `@MockitoBean` for external services

### Configuration Properties Pattern
- Use `@ConfigurationProperties(prefix = "app.mail")` with a data class
- Register via `@EnableConfigurationProperties` on config class
- Environment variables with defaults in application.yaml

## 6. Existing EmailService Integration

### Current State
- `EmailService` interface in `authentication/service/` — single method: `sendCode(to, code, purpose)`
- `ConsoleEmailService` registered via `SmsSchedulerConfig` when no bean present
- Used by `VerificationCodeService` for forgot-password, change-email flows

### Migration Strategy
- `SmtpMailService` implements BOTH `EmailService` and `MailService`
- `SmtpMailService.sendCode(to, code, purpose)` creates a simple text email internally
- `ConsoleMailService` also implements BOTH — replaces existing `ConsoleEmailService`
- Remove `ConsoleEmailService` class and its bean registration from `SmsSchedulerConfig`
- `SmsSchedulerConfig` only registers `ConsoleSmsService` fallback after this change
- Existing callers (VerificationCodeService) don't change — they still inject `EmailService`

## 7. File Attachment Handling

### Controller Layer
- `@RequestPart` for multipart/form-data file uploads
- Or embed attachments in JSON as base64 (simpler for API clients)
- **Decision from CONTEXT.md:** "controller accepts multipart/form-data for file uploads"
- Use `@RequestParam("files") files: List<MultipartFile>` alongside JSON body

### DTO Design
- Attachment representation: `data class EmailAttachment(val filename: String, val contentType: String, val bytes: ByteArray)`
- In request: multipart form — files as `MultipartFile`, other fields as form params or JSON part

## 8. Pitfalls

1. **Virtual Thread pinning:** JavaMailSender.send() pins — MUST use @Async dispatcher
2. **IMAP connection leaks:** Always close Store/Folder in finally blocks
3. **GreenMail + Spring Boot 4:** Ensure compatible version; may need `greenmail-junit5` artifact
4. **H2 compatibility:** Test profile uses H2 with `create-drop` — mail_history and notification_preferences tables auto-created by Hibernate
5. **Flyway V4 migration:** Next after V3 (notifications) — never skip version numbers
6. **Multipart controller:** Spring needs `MultipartAutoConfiguration` — already included via `spring-boot-starter-webmvc`

---

*Phase: 02-email-service-and-notification-preferences*
*Research completed: 2026-03-03*
