# Pitfalls Research

**Domain:** Spring Boot 4 + Kotlin — Firebase Cloud Messaging (FCM) and Email (SMTP/IMAP) integration into existing auth backend
**Researched:** 2026-03-03
**Confidence:** HIGH (Firebase Admin SDK official docs; Spring Mail official docs; JavaMail/Jakarta Mail known issues; multiple authoritative sources)

> **Note:** This file supersedes the v1–v5 auth pitfalls file and adds the v6.0 FCM + Email domain. The auth pitfalls from previous research remain referenced in the Sources section.

---

## Critical Pitfalls

### Pitfall 1: Firebase Admin SDK Initialized Multiple Times (Duplicate App Exception)

**What goes wrong:**
Calling `FirebaseApp.initializeApp(options)` more than once in the same JVM process throws `IllegalStateException: FirebaseApp name [DEFAULT] already exists!` and crashes the application. This happens on Spring context refresh (e.g., during tests, live-reload, or multi-context setups). The SDK maintains a static registry — it is not Spring-context-scoped.

**Why it happens:**
Developers wrap initialization in a `@Bean` method and annotate the config class with `@Configuration`. Spring may call the `@Bean` method more than once if the bean is not properly scoped, or integration tests create multiple `ApplicationContext` instances (one per test class without `@DirtiesContext` management). Spring Boot DevTools context refresh also triggers this.

**How to avoid:**
Guard initialization with `FirebaseApp.getApps().isEmpty()` before calling `initializeApp`:

```kotlin
@Configuration
class FirebaseConfig {
    @Bean
    fun firebaseApp(@Value("\${firebase.credentials-path}") credentialsPath: String): FirebaseApp {
        if (FirebaseApp.getApps().isNotEmpty()) {
            return FirebaseApp.getInstance()
        }
        val serviceAccount = FileInputStream(credentialsPath)
        val options = FirebaseOptions.builder()
            .setCredentialsFromStream(serviceAccount)
            .build()
        return FirebaseApp.initializeApp(options)
    }
}
```

For tests, mock the `FirebaseMessaging` bean directly — do NOT initialize real Firebase in test contexts.

**Warning signs:**
- `IllegalStateException: FirebaseApp name [DEFAULT] already exists!` on startup or test run
- Tests pass individually but fail when run together
- DevTools hot-reload causes `FirebaseApp` crash

**Phase to address:**
Phase: Firebase Admin SDK initialization + device token management

---

### Pitfall 2: Packaging `firebase-service-account.json` Inside the JAR

**What goes wrong:**
Placing the Firebase service account JSON file in `src/main/resources/` causes it to be bundled into the JAR artifact. Any CI artifact, Docker image layer, or unpacked JAR exposes Google service account credentials with full Firebase project access. This is an irreversible secret leak — the credential must be rotated immediately if this happens.

**Why it happens:**
Developers follow Firebase's "Getting Started" docs which show `FileInputStream("serviceAccountKey.json")` — a relative path that works in IDE but they copy it to resources for "convenience." Spring Boot's resource handling makes it easy to put anything in resources without thinking about artifact security.

**How to avoid:**
- Never place `firebase-service-account.json` in `src/main/resources/`
- Add `**/firebase-service-account*.json` to `.gitignore` immediately
- Use one of these patterns instead:
  1. **Environment variable with JSON content:** `GOOGLE_APPLICATION_CREDENTIALS_JSON` env var → parse as stream
  2. **Mounted file path:** `FIREBASE_CREDENTIALS_PATH` env var pointing to a volume-mounted file
  3. **Google Application Default Credentials (ADC):** On GCP/Cloud Run, use workload identity — no JSON file needed
- Add the credentials path to `.env.example` with a placeholder value

**Warning signs:**
- `firebase-service-account.json` visible in `git status` as tracked
- `target/` directory contains the credentials file after `mvn package`
- Docker image layers contain the JSON when inspected with `docker history`

**Phase to address:**
Phase: Firebase Admin SDK initialization + device token management

---

### Pitfall 3: Not Handling Stale/Invalid FCM Registration Tokens

**What goes wrong:**
FCM device tokens expire, are revoked, or become invalid when a user uninstalls the app, reinstalls it, or clears app data. Sending a push notification to a stale token returns either `UNREGISTERED` or `INVALID_ARGUMENT` error codes from the FCM API. If these errors are not handled, the backend accumulates dead tokens indefinitely. At scale, this wastes API quota, slows batch sends, and the token table grows unbounded.

**Why it happens:**
Developers store tokens on registration and never handle FCM's error responses. The send call "succeeds" from an HTTP perspective (returns 200) but the response body contains per-token failure codes that must be inspected.

**How to avoid:**
- After every FCM send, inspect the response for `UNREGISTERED` and `INVALID_ARGUMENT` error codes
- On `UNREGISTERED`: delete the token record immediately from the database
- On `INVALID_ARGUMENT` for a token: delete the token record (it is malformed)
- For `sendEachForMulticast` (batch send), iterate `BatchResponse.responses` and remove failed tokens:

```kotlin
val batchResponse = FirebaseMessaging.getInstance().sendEachForMulticast(message)
batchResponse.responses.forEachIndexed { index, sendResponse ->
    if (!sendResponse.isSuccessful) {
        val errorCode = sendResponse.exception?.messagingErrorCode
        if (errorCode == MessagingErrorCode.UNREGISTERED ||
            errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            deviceTokenRepository.deleteByToken(tokens[index])
        }
    }
}
```

- Also remove tokens on logout/revoke — do not wait for FCM to tell you

**Warning signs:**
- `device_tokens` table grows indefinitely with no deletions
- FCM batch send `failureCount` is always > 0 in logs but no cleanup happens
- Users receive duplicate notifications (multiple stale tokens per user)

**Phase to address:**
Phase: Firebase Admin SDK initialization + device token management

---

### Pitfall 4: One FCM Token Per User Instead of One Per Device

**What goes wrong:**
Storing a single FCM token per user and overwriting it on each login means only the most-recently-logged-in device receives push notifications. A user logged in on both phone and tablet only gets notifications on whichever device logged in last. When the user logs out on one device, the remaining device's token is lost.

**Why it happens:**
The `User` entity already exists — developers add a single `fcmToken: String?` column to it. This feels natural but does not model the one-user-to-many-devices reality.

**How to avoid:**
Model device tokens as a separate entity with a many-to-one relationship to `User`:

```kotlin
@Entity
@Table(name = "device_tokens")
class DeviceToken : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    @Column(nullable = false, unique = true)
    lateinit var token: String

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    lateinit var platform: DevicePlatform  // ANDROID, IOS, WEB

    @Column(nullable = false)
    var lastUsedAt: Instant = Instant.now()
}
```

Register tokens on login, deregister on logout. Enforce a maximum of N tokens per user (e.g., 10) to prevent abuse.

**Warning signs:**
- `users` table has an `fcm_token` column (single token per user)
- Users with multiple devices report missing notifications
- Token is overwritten on every login in the service layer

**Phase to address:**
Phase: Firebase Admin SDK initialization + device token management

---

### Pitfall 5: FCM Platform Differences — iOS Requires APNs Configuration

**What goes wrong:**
Android FCM tokens work directly. iOS push notifications require APNs (Apple Push Notification service) to be configured in the Firebase project console AND the `apns` field in the FCM message payload to be set correctly for iOS-specific behavior (sound, badge, content-available for background wake). Sending an Android-formatted message to an iOS token delivers silently or not at all with no obvious error.

**Why it happens:**
Developers test on Android first, FCM works, and assume iOS is identical. Firebase's FCM API accepts the send call without error — the failure is silent on the server side.

**How to avoid:**
- Configure APNs in Firebase Console (upload .p8 key or .p12 certificate) before testing iOS delivery
- Use the `apns` builder for iOS-specific fields:

```kotlin
val message = Message.builder()
    .setToken(token)
    .setNotification(Notification.builder()
        .setTitle(title)
        .setBody(body)
        .build())
    .setAndroidConfig(AndroidConfig.builder()
        .setPriority(AndroidConfig.Priority.HIGH)
        .build())
    .setApnsConfig(ApnsConfig.builder()
        .setAps(Aps.builder()
            .setSound("default")
            .build())
        .build())
    .build()
```

- Store `platform` (ANDROID, IOS, WEB) with each token so the service can include platform-appropriate config
- Test iOS delivery in a real device or TestFlight environment — simulators do not receive push notifications

**Warning signs:**
- Android notifications work but iOS notifications silently fail
- FCM send returns success but iOS device never shows the notification
- No `apns` configuration in Firebase Console for the project

**Phase to address:**
Phase: Firebase Admin SDK initialization + device token management

---

### Pitfall 6: FCM Topic Subscriptions Not Cleaned Up on Logout

**What goes wrong:**
When a user subscribes a device token to an FCM topic (e.g., `news`, `user-{id}`), the subscription persists indefinitely in Firebase's topic registry even after the token is deleted from the local database. Subsequent sends to that topic reach devices that should no longer receive them — including devices of logged-out users or uninstalled apps.

**Why it happens:**
Topic subscription is an explicit API call, but unsubscription is easy to forget. Developers focus on the happy path (subscribe on login) and do not model the logout/token-deletion path.

**How to avoid:**
- Unsubscribe the token from ALL its topics before deleting it from the database:

```kotlin
// On logout or token deletion:
fun deregisterToken(token: String, topics: List<String>) {
    if (topics.isNotEmpty()) {
        FirebaseMessaging.getInstance().unsubscribeFromTopicAsync(topics, token)
    }
    deviceTokenRepository.deleteByToken(token)
}
```

- Store which topics each token is subscribed to in the local database
- Alternatively: avoid persistent topic subscriptions entirely; use user-specific sends via token lists for small user bases

**Warning signs:**
- Logged-out users receive push notifications
- Topic subscriber counts in Firebase Console grow without bound
- No `unsubscribeFromTopic` calls anywhere in the codebase

**Phase to address:**
Phase: Firebase Admin SDK initialization + device token management

---

### Pitfall 7: JavaMailSender / Spring Mail `mail.smtp.auth=true` Without SSL/TLS Sends Password in Plaintext

**What goes wrong:**
Configuring `spring.mail.properties.mail.smtp.auth=true` without also enabling STARTTLS or SSL causes the SMTP client to authenticate using the configured mechanism (LOGIN, PLAIN) over an unencrypted connection. Credentials are transmitted in plaintext on the network. Modern SMTP servers (Gmail, SendGrid, etc.) reject this, but some self-hosted servers accept it — silently compromising credentials.

**Why it happens:**
Developers copy minimal Spring Mail configuration examples that show `auth=true` but omit the TLS settings. The application sends email successfully on localhost (no real network exposure) but the config is insecure for production.

**How to avoid:**
Always pair SMTP auth with STARTTLS or SSL:

```yaml
spring:
  mail:
    host: ${SMTP_HOST}
    port: ${SMTP_PORT:587}       # 587 for STARTTLS, 465 for SSL
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true          # for port 587
            required: true        # fail if server does not support STARTTLS
          # OR for port 465 (SSL):
          # ssl:
          #   enable: true
```

**Warning signs:**
- `spring.mail.port=25` in configuration (port 25 = unauthenticated relay, plaintext)
- `starttls.enable` or `ssl.enable` absent from `mail.smtp.properties`
- Wireshark/tcpdump shows AUTH LOGIN or AUTH PLAIN commands in cleartext on the wire

**Phase to address:**
Phase: SMTP email sending service

---

### Pitfall 8: Spring Mail Blocks Virtual Threads on SMTP Send

**What goes wrong:**
`JavaMailSender.send()` is a synchronous, blocking I/O call. Under Spring MVC with Virtual Threads (as used in this project), blocking I/O mounts the virtual thread on a platform thread for the duration of the SMTP connection. For transactional emails triggered during an HTTP request (e.g., "send verification email on registration"), the HTTP response is delayed by the full SMTP round-trip (typically 200–2000ms). Under high load, many concurrent platform threads are pinned on SMTP calls.

**Why it happens:**
Virtual Threads handle most blocking I/O transparently, but SMTP calls via `javax.mail`/`jakarta.mail` use synchronized blocks internally, which can pin the carrier thread (a known JDK limitation: synchronized blocks prevent virtual thread unmounting).

**How to avoid:**
- Execute mail sends via `@Async` (Spring's async executor):

```kotlin
@Service
class SmtpEmailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: TemplateEngine
) : EmailService {

    @Async
    override fun sendVerificationEmail(to: String, code: String): CompletableFuture<Void> {
        val message = mailSender.createMimeMessage()
        // ... build message ...
        mailSender.send(message)
        return CompletableFuture.completedFuture(null)
    }
}
```

- Enable `@EnableAsync` on the application or a config class (can co-exist with existing `@EnableScheduling` in `SmsSchedulerConfig`)
- Use a bounded thread pool for the async executor to prevent unbounded mail send threads

**Warning signs:**
- HTTP endpoint that triggers email send has p95 latency > 500ms
- Thread dump shows carrier threads pinned in `sun.security.ssl.*` or `com.sun.mail.*` synchronized blocks
- Under load, mail sends queue up and block request threads

**Phase to address:**
Phase: SMTP email sending service

---

### Pitfall 9: IMAP Session Not Closed — Connection Leak

**What goes wrong:**
`jakarta.mail.Store` and `Folder` objects represent open IMAP connections. If `folder.close()` and `store.close()` are not called — even when an exception is thrown — the IMAP server accumulates open connections. Most IMAP servers (including Gmail) enforce a maximum concurrent connection limit per account (Gmail: ~15). Exhausting this limit causes `javax.mail.MessagingException: Too many simultaneous connections`.

**Why it happens:**
IMAP connection management is manual — there is no built-in connection pool in Jakarta Mail. Developers open connections in try blocks but forget to close in `finally` or use Kotlin's `use` extension, especially when exceptions occur mid-operation.

**How to avoid:**
Always use try-finally or Kotlin's extension for cleanup:

```kotlin
fun fetchUnreadMessages(): List<EmailMessage> {
    val store: Store = session.getStore("imaps")
    store.connect(host, username, password)
    val folder = store.getFolder("INBOX")
    folder.open(Folder.READ_ONLY)
    try {
        return folder.messages
            .filter { !it.isSet(Flags.Flag.SEEN) }
            .map { it.toEmailMessage() }
    } finally {
        runCatching { folder.close(false) }
        runCatching { store.close() }
    }
}
```

- Consider an IMAP connection pool library (e.g., `angus-mail` with connection pooling) for high-frequency polling
- For low-frequency polling (scheduled job), open-connect-read-close per invocation is acceptable

**Warning signs:**
- `MessagingException: Too many simultaneous connections` from IMAP server
- `store.isConnected()` returns true for connections created by previous requests
- No `store.close()` / `folder.close()` in catch/finally blocks

**Phase to address:**
Phase: IMAP/POP3 email receiving

---

### Pitfall 10: IMAP Polling Scheduled Job Conflicts With `@EnableScheduling` in `SmsSchedulerConfig`

**What goes wrong:**
Adding a new `@Scheduled` IMAP polling method in a new config class while `SmsSchedulerConfig` already has `@EnableScheduling` causes no runtime error, but having `@EnableScheduling` on multiple `@Configuration` classes is redundant and confusing. More critically, if the IMAP polling job and the SMS cleanup job share the same default single-threaded `TaskScheduler`, a slow IMAP poll (blocked on network) can delay the SMS cleanup job. Expired SMS codes accumulate until the IMAP poll completes.

**Why it happens:**
Developers add a new scheduler config without checking whether `@EnableScheduling` already exists. Spring Boot's default `TaskScheduler` uses a single thread by default.

**How to avoid:**
- Keep `@EnableScheduling` only in `SmsSchedulerConfig` (it is already there — do not add it again)
- Add IMAP polling as a `@Scheduled` method in the existing `SmsSchedulerConfig`, or create a dedicated service bean with `@Scheduled` without repeating `@EnableScheduling`
- Configure a multi-threaded `TaskScheduler` bean to prevent job starvation:

```kotlin
@Bean
fun taskScheduler(): TaskScheduler {
    val scheduler = ThreadPoolTaskScheduler()
    scheduler.poolSize = 4
    scheduler.setThreadNamePrefix("scheduler-")
    return scheduler
}
```

**Warning signs:**
- Two `@Configuration` classes both annotated with `@EnableScheduling`
- IMAP polling log timestamps show irregular intervals (delayed by other jobs)
- SMS cleanup job shows delayed execution correlated with IMAP polling duration

**Phase to address:**
Phase: IMAP/POP3 email receiving

---

### Pitfall 11: Flyway Migration Numbering Gap After V2

**What goes wrong:**
The existing schema has `V1__initial_schema.sql` and `V2__account_linking.sql`. Adding FCM device tokens and/or email-related tables requires new migrations. If a developer creates `V3__...` locally but another team member also creates `V3__...` independently (for a different feature), Flyway fails with `Found more than one migration with version 3`. Additionally, out-of-order migrations (V5 before V4) fail unless `spring.flyway.out-of-order=true` is configured, which is risky in production.

**Why it happens:**
Multiple developers working on the same milestone create migrations without coordinating version numbers. In single-developer projects (like this template), this happens when a developer starts migration work on one branch and switches to another.

**How to avoid:**
- For this milestone, reserve V3 through V5 for FCM + Email tables (one migration per logical group: device_tokens, email_outbox, email_inbox_cache if needed)
- Use descriptive names: `V3__device_tokens.sql`, `V4__email_outbox.sql`
- Never skip version numbers — Flyway detects gaps as errors with default config
- Set `spring.flyway.validate-on-migrate=true` (default) to catch checksum mismatches early
- In test profile, Flyway is disabled (H2 uses `create-drop`) — confirm this remains true after adding new migrations to avoid H2-incompatible SQL

**Warning signs:**
- `FlywayException: Found more than one migration with version X`
- Flyway checksum validation fails after editing an already-applied migration file
- Test profile fails because H2 cannot parse PostgreSQL-specific DDL in migration files

**Phase to address:**
Phase: Firebase Admin SDK initialization + device token management (first migration V3); Phase: SMTP/IMAP (if schema needed)

---

### Pitfall 12: SecurityConfig Missing Permits for New Notification/Email Endpoints

**What goes wrong:**
The existing `SecurityConfig` has `authorize("/api/v1/auth/**", permitAll)` and `authorize("/api/**", authenticated)`. New notification and email endpoints under `/api/v1/notifications/**` and `/api/v1/email/**` are automatically protected — which is correct for user-facing endpoints. However, internal webhook endpoints (e.g., a Firebase Cloud Messaging delivery receipt webhook, or an email bounce webhook from an SMTP provider) must NOT require Bearer token auth since they are called by external services. Forgetting to permit these returns 401 and the webhooks silently fail.

**Why it happens:**
Developers add new controller endpoints and forget to review the security filter chain. The existing `authorize("/api/**", authenticated)` is a catch-all that requires auth for everything under `/api/`.

**How to avoid:**
- Before adding any endpoint, explicitly decide: public, authenticated user, or internal webhook
- For webhook endpoints, add a specific permit rule BEFORE the catch-all:

```kotlin
authorizeHttpRequests {
    authorize(HttpMethod.OPTIONS, "/**", permitAll)
    authorize("/api/v1/auth/**", permitAll)
    authorize(HttpMethod.POST, "/api/v1/webhooks/**", permitAll)  // external webhooks
    authorize("/api/**", authenticated)
    authorize(anyRequest, permitAll)
}
```

- Protect webhook endpoints with a shared secret (HMAC signature verification) instead of Bearer tokens
- Add integration tests that verify each new endpoint returns the correct HTTP status without auth and with auth

**Warning signs:**
- New webhook endpoint always returns 401 in logs from external service calls
- SMTP provider bounce/complaint webhooks show delivery failures in provider dashboard
- No new `authorize(...)` rules added to `SecurityConfig` for the v6 endpoints

**Phase to address:**
Phase: SMTP email sending service; Phase: IMAP/POP3 email receiving

---

### Pitfall 13: Firebase Admin SDK Sends on the Request Thread (Blocking HTTP Under Virtual Threads)

**What goes wrong:**
`FirebaseMessaging.getInstance().send(message)` is a synchronous blocking call that makes an HTTP request to `https://fcm.googleapis.com`. Under Spring MVC with Virtual Threads, this blocks the request thread for the FCM round-trip (typically 100–500ms). For a "send push notification" API endpoint, this means HTTP response time = FCM network latency. Under high concurrent load, many virtual threads are held waiting on FCM responses, and if FCM has an outage, all in-flight request threads hang until the FCM timeout fires.

**Why it happens:**
The synchronous `send()` method is the simplest API. The async `sendAsync()` returns a `ListenableFuture<String>` (Guava) which is unfamiliar, so developers default to the blocking version.

**How to avoid:**
Use `sendAsync()` and bridge to Kotlin coroutines or `CompletableFuture`:

```kotlin
// Option A: sendAsync with CompletableFuture bridge
fun sendNotification(token: String, message: Message): CompletableFuture<String> {
    return ApiFutures.toCompletableFuture(
        FirebaseMessaging.getInstance().sendAsync(message)
    )
}

// Option B: wrap sync call in @Async method
@Async
fun sendNotificationAsync(message: Message): CompletableFuture<String> {
    val result = FirebaseMessaging.getInstance().send(message)
    return CompletableFuture.completedFuture(result)
}
```

- Set a reasonable FCM timeout via `FirebaseOptions.setConnectTimeout()` and `setReadTimeout()` to prevent indefinite hangs

**Warning signs:**
- P95 latency of `/api/v1/notifications/send` equals FCM network latency (~300ms)
- During FCM outages, all API threads hang with no timeout
- Thread dump shows virtual threads parked in OkHttp/FCM HTTP stack during load test

**Phase to address:**
Phase: Firebase Admin SDK initialization + device token management

---

### Pitfall 14: H2 In-Memory Test Database Cannot Test Firebase or Real SMTP

**What goes wrong:**
The existing test setup uses H2 in-memory database with `spring.flyway.enabled=false` and `spring.jpa.hibernate.ddl-auto=create-drop`. Integration tests cannot test real FCM sends or real SMTP sends. Developers write tests that inadvertently initialize the real `FirebaseApp` or `JavaMailSender`, causing:
- Firebase: `IllegalStateException` if real credentials are not present in CI
- SMTP: connection refused (no SMTP server in test environment)
- Both: tests pass locally (with real credentials) but fail in CI

**Why it happens:**
The `@SpringBootTest` annotation loads the full application context, which auto-wires all beans including `FirebaseConfig` and `SmtpEmailService`. Without explicit test overrides, the real implementations are used.

**How to avoid:**
- In test profile (`application-test.yml`), set `firebase.enabled=false` and use `@ConditionalOnProperty` on `FirebaseConfig`
- Provide test-only mock beans using `@TestConfiguration`:

```kotlin
@TestConfiguration
class NotificationTestConfig {
    @Bean
    @Primary
    fun firebaseMessaging(): FirebaseMessaging = mockk(relaxed = true)

    @Bean
    @Primary
    fun emailService(): EmailService = mockk(relaxed = true)
}
```

- Use GreenMail for SMTP integration tests (embedded SMTP server in tests):

```kotlin
// In test: start GreenMail SMTP server, configure JavaMailSender to point at it
val greenMail = GreenMail(ServerSetup(3025, null, ServerSetup.PROTOCOL_SMTP))
greenMail.start()
```

- Mock FCM at the `FirebaseMessaging` bean level — do NOT mock the `FirebaseApp` initialization

**Warning signs:**
- `FileNotFoundException` for `firebase-service-account.json` during test runs
- `ConnectException: Connection refused` to port 587 in test output
- Tests pass on developer machine (has real credentials) but fail in CI

**Phase to address:**
All phases (establish test mock pattern in first Firebase/Email phase and reuse throughout)

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Single FCM token per user (column on `users` table) | Zero migration complexity | Multi-device users miss notifications; hard to migrate later | Never — model as separate `device_tokens` table from the start |
| Sending FCM synchronously on request thread | Simple implementation | High latency on notification endpoints; hanging threads on FCM outage | Never in production — use `@Async` or `sendAsync()` |
| Not cleaning up stale FCM tokens | No deletion logic needed | Token table grows unbounded; batch send wastes quota; quota exhaustion | Never — handle `UNREGISTERED` error from day one |
| Embedding Firebase credentials in JAR | Simpler path setup in dev | Secret exposed in any artifact; must rotate credential immediately | Never — use env var or mounted file |
| SMTP without STARTTLS/SSL | Fewer config properties | Credentials transmitted in plaintext | Never — always require TLS |
| Not using `@Async` for email sends | Simpler code path | HTTP response time includes full SMTP round-trip | Acceptable ONLY for batch/background email jobs, not for request-scoped sends |
| IMAP open-close per request (no pool) | No pool management complexity | Connection limit exhaustion under concurrent polling | Acceptable for low-frequency scheduled polling (< 1/min); not for real-time |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Firebase Admin SDK | `FirebaseApp.initializeApp()` called without guard | Check `FirebaseApp.getApps().isNotEmpty()` before initializing |
| Firebase Admin SDK | Credentials file in `src/main/resources/` | Use env var with JSON content or volume-mounted file path |
| FCM send | Ignoring per-token error codes in batch response | Inspect `BatchResponse.responses`; delete tokens on `UNREGISTERED` |
| FCM iOS | No `apns` config in message payload | Set `ApnsConfig` with sound/badge for iOS; store `platform` with token |
| FCM topic | Subscribing tokens without tracking subscriptions | Store subscribed topics per token in DB; unsubscribe on token deletion |
| Spring Mail | `mail.smtp.auth=true` without TLS | Add `starttls.enable=true` and `starttls.required=true` for port 587 |
| Spring Mail | Blocking `send()` on request thread | Use `@Async` service method or `sendAsync()` pattern |
| Jakarta Mail IMAP | No `store.close()` in finally block | Use try-finally with `runCatching { store.close() }` |
| GreenMail in tests | Configuring same port as prod SMTP | Use a non-standard test port (e.g., 3025) and override `spring.mail.port` in test config |
| Flyway in tests | H2 running FCM/email migration files with PostgreSQL syntax | Ensure `spring.flyway.enabled=false` in test profile; use H2-compatible DDL only |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Synchronous FCM send on HTTP request thread | P95 latency of notification endpoint = FCM network RTT (200–500ms) | Use `sendAsync()` or `@Async` | From first high-concurrency load test |
| Unbounded IMAP connection creation without pool | `Too many simultaneous connections` from IMAP server | Open-close per scheduled poll invocation; pool only if polling > 1/min | ~15 concurrent connections (Gmail limit) |
| FCM batch send to all tokens without pagination | FCM batch limit is 500 tokens per request; crash on `> 500` | Chunk token list into batches of ≤ 500 before `sendEachForMulticast` | At 501+ device tokens for a single send |
| SMTP send inside `@Transactional` method | Email sent even if DB transaction rolls back (orphaned email) | Send email AFTER transaction commits using `TransactionSynchronizationManager` or outbox pattern | On any failed transaction |
| Single-threaded default `TaskScheduler` for all scheduled jobs | IMAP polling delay cascades to SMS cleanup delay | Configure `ThreadPoolTaskScheduler` with poolSize ≥ number of scheduled jobs | When any scheduled job is slow |
| Loading all unread IMAP messages into memory | `OutOfMemoryError` on large inbox | Use `folder.getMessages(start, end)` with pagination; process in chunks | Inbox with > 1000 unread messages |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Firebase service account JSON in source control | Full Firebase project access exposed in git history | Add `**/firebase-service-account*.json` to `.gitignore`; use env var for credentials |
| SMTP password in `application.yml` (committed) | Mail account compromised | Use `${SMTP_PASSWORD}` placeholder; real value only via environment variable |
| FCM webhook endpoint without signature verification | Anyone can POST fake delivery receipts | Verify Firebase-generated HMAC signature on webhook requests using shared secret |
| Sending emails containing JWT or refresh tokens | Tokens in email can be forwarded, cached in mail servers | Never send auth tokens via email; use short-lived, single-use opaque codes (already done for VerificationCode) |
| IMAP credentials same as SMTP credentials | Single credential compromise loses both send and receive | Use separate accounts/app-passwords for SMTP and IMAP if possible |
| Storing full email body content in DB without size limit | Mail bomb attack fills disk; `@Lob` column grows unbounded | Truncate body at storage layer (e.g., first 10KB); store metadata only for inbox cache |
| Exposing internal email IDs (sequential DB IDs) in API response | Enumeration of all received emails | Use UUID v7 for `DeviceToken` and email entities — already using `BaseEntity` with UUID v7 |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No notification delivery feedback to caller | API returns 200 but user never knows if push actually delivered | Return FCM message ID on success; log failures per token with error code |
| Sending push notification and email for same event without deduplication | User receives duplicate alerts | Use notification preference model: user chooses preferred channel |
| No retry for transient FCM errors (`INTERNAL`, `QUOTA_EXCEEDED`) | Notifications lost on FCM-side transient failures | Implement exponential backoff retry for `INTERNAL` and `UNAVAILABLE` FCM error codes |
| Email "From" address not matching domain | Emails land in spam; DMARC/DKIM failures | Configure DKIM signing with SMTP provider; "From" domain must match SPF/DKIM records |
| HTML email with no plain-text fallback | Email clients that disable HTML show blank email | Always set both HTML and plain-text parts in `MimeMultipart` |

---

## "Looks Done But Isn't" Checklist

- [ ] **Firebase initialization guard:** `FirebaseApp.getApps().isNotEmpty()` check present — verify by running tests twice in same JVM (no `IllegalStateException`)
- [ ] **Firebase credentials not in JAR:** `mvn package && jar tf target/*.jar | grep service-account` returns nothing
- [ ] **Stale token cleanup:** `UNREGISTERED` FCM error code triggers token deletion — verify with a test using a known-bad token
- [ ] **Multi-device model:** `device_tokens` is a separate table with FK to `users` — not a column on `users`
- [ ] **SMTP TLS required:** `starttls.required=true` in config — not just `starttls.enable=true` (required fails fast if server does not support TLS)
- [ ] **Email send is async:** `@Async` on send method; HTTP endpoint returns before SMTP completes — verify with timing test
- [ ] **IMAP connection closed:** Every `store.close()` is in a `finally` block — verify with connection-count monitoring on IMAP server
- [ ] **FCM batch size:** Sends with > 500 tokens are chunked into batches of ≤ 500 — verify with 501-token test
- [ ] **Flyway migrations:** V3 (and V4 if needed) exist, checksums pass, no H2-incompatible SQL in test profile
- [ ] **Security config updated:** New notification/email endpoints have explicit auth rules; webhook endpoints are `permitAll` with HMAC verification
- [ ] **Test mocking:** CI tests pass with no real Firebase credentials and no real SMTP server — GreenMail or mocks used
- [ ] **`@EnableScheduling` single location:** Only one class annotated with `@EnableScheduling` — confirm `SmsSchedulerConfig` is still the only one

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Firebase service account committed to git | HIGH | Revoke key in Google Cloud Console immediately; generate new key; remove from git history with `git filter-repo`; rotate all dependent secrets |
| Duplicate Firebase app initialization crashing tests | LOW | Add `FirebaseApp.getApps().isNotEmpty()` guard in config bean; clear app in `@AfterAll` with `FirebaseApp.getInstance().delete()` in test teardown |
| Stale FCM token table growing unbounded | MEDIUM | Write one-time cleanup migration to delete tokens older than 90 days; add `UNREGISTERED` handler to send service going forward |
| IMAP connection leak exhausting server limit | MEDIUM | Restart application to release connections; add finally blocks for all `store.close()` calls; verify with integration test |
| Wrong Flyway version number conflict | LOW | Delete the conflicting migration file; create new one with correct sequential version number; clean Flyway schema history if applied to dev DB only |
| SMTP password exposed in committed `application.yml` | HIGH | Rotate SMTP password immediately; remove from git history; move to environment variable |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Firebase duplicate app initialization | Phase: Firebase Admin SDK setup | Tests run in same JVM without `IllegalStateException` |
| Firebase credentials in JAR | Phase: Firebase Admin SDK setup | `jar tf` check on packaged artifact; `.gitignore` entry present |
| Stale FCM token not cleaned up | Phase: Device token management | Integration test: send to known-bad token; verify token deleted from DB |
| Single token per user | Phase: Device token management | Schema review: `device_tokens` table exists with FK to `users` |
| iOS APNs not configured | Phase: Device token management | End-to-end test on real iOS device or TestFlight build |
| Topic subscription leak | Phase: Device token management | Logout test: token unsubscribed from all topics before deletion |
| SMTP without TLS | Phase: SMTP email sending | Config review: `starttls.required=true` present; verify with test against non-TLS server |
| Blocking SMTP on request thread | Phase: SMTP email sending | Latency test: HTTP endpoint returns in < 50ms regardless of SMTP duration |
| IMAP connection leak | Phase: IMAP/POP3 receiving | IMAP connection count monitoring during load test; no growth over baseline |
| Scheduler single-threaded starvation | Phase: IMAP/POP3 receiving | Add `ThreadPoolTaskScheduler` bean before adding IMAP polling job |
| Flyway migration versioning conflict | Phase: FCM token schema migration | `mvn flyway:validate` passes; no version conflicts; test profile uses H2 create-drop |
| SecurityConfig missing new endpoint permits | Phase: SMTP / IMAP | Integration test: unauthenticated request to webhook endpoint returns expected status |
| FCM blocking send on request thread | Phase: Firebase Admin SDK setup | Async test: response arrives before FCM network call completes |
| H2 tests initializing real Firebase/SMTP | All phases | CI run without credentials passes all tests; GreenMail or mocks used |

---

## Sources

- Firebase Admin SDK Java/Kotlin docs — https://firebase.google.com/docs/admin/setup (HIGH confidence — official)
- FCM Admin SDK: Send messages — https://firebase.google.com/docs/cloud-messaging/send-message (HIGH confidence — official)
- FCM error codes — https://firebase.google.com/docs/reference/fcm/rest/v1/ErrorCode (HIGH confidence — official)
- Firebase Admin SDK: `FirebaseApp.initializeApp` guard pattern — https://firebase.google.com/docs/admin/setup#initialize-sdk (HIGH confidence — official docs show the guard explicitly)
- Spring Mail configuration — https://docs.spring.io/spring-boot/reference/io/email.html (HIGH confidence — official Spring Boot docs)
- GreenMail embedded mail server for tests — https://greenmail-project.github.io/greenmail/ (HIGH confidence — official project docs)
- Jakarta Mail / Angus Mail IMAP connection management — https://eclipse-ee4j.github.io/angus-mail/ (HIGH confidence — official)
- FCM batch send limit (500 messages) — https://firebase.google.com/docs/cloud-messaging/send-message#send-messages-to-multiple-devices (HIGH confidence — official)
- APNs configuration for FCM iOS — https://firebase.google.com/docs/cloud-messaging/ios/client (HIGH confidence — official)
- Spring Boot `@Async` configuration — https://docs.spring.io/spring-framework/reference/integration/scheduling.html (HIGH confidence — official)
- Virtual Threads and synchronized block pinning (JEP 444) — https://openjdk.org/jeps/444 (HIGH confidence — JDK spec)
- Spring Security: SecurityConfig `authorizeHttpRequests` — https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html (HIGH confidence — official)
- Flyway versioning best practices — https://documentation.red-gate.com/fd/migrations-184127470.html (HIGH confidence — official Flyway docs)
- Previous v1–v5 auth pitfalls research: `.planning/research/PITFALLS.md` (v2026-03-01 version, archived)

---
*Pitfalls research for: FCM push notifications + SMTP/IMAP email on Spring Boot 4 + Kotlin auth backend*
*Researched: 2026-03-03*
