# Phase 1: FCM Push Notifications - Research

**Researched:** 2026-03-03
**Domain:** Firebase Cloud Messaging (FCM) — Firebase Admin SDK Java 9.x, Spring Boot 4.0 Kotlin
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **Async delivery:** API accepts notification requests with 202 Accepted and processes in background
- **Max tokens per user:** Configurable via application.yml (not hardcoded)
- **Hard delete on token removal:** No soft delete, row is removed entirely
- **Stale token cleanup:** Reactive only — delete when FCM returns UNREGISTERED/INVALID_ARGUMENT during send; no scheduled job
- **Predefined topics only:** Users can only subscribe to topics that exist in the system
- **Topics stored in database table, managed via admin API** (dynamic, no restart needed)
- **No list endpoint for topics** — clients know topic names from app logic
- **Log all send attempts** — PENDING, SENT, FAILED statuses for complete audit trail
- **Access model:** Users see own history + admin role can view all notifications
- **Cursor-based pagination using UUID v7** (aligns with existing project pattern)
- **Registration response:** Return only the single registered token details (not full list)
- **GET /notifications/tokens** endpoint to list all user's registered tokens
- **Bulk delete:** Support both DELETE /notifications/tokens/{deviceId} (single) and DELETE /notifications/tokens (all user tokens)

### Claude's Discretion

- Endpoint structure (separate vs unified send endpoints)
- Notification status tracking (individual vs history-only)
- Data payload validation strategy
- Device token deduplication on re-register
- Device identifier approach (user-provided deviceId vs FCM token as key)
- Notification history read/dismiss interaction model
- Topic send authorization (admin-only vs any authenticated user)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| FCM-01 | Authenticated user can register a device token with platform type (ANDROID/IOS/WEB) | DeviceToken entity + JPA repository; POST /api/v1/notifications/tokens |
| FCM-02 | User can delete a device token (e.g., on logout) | DELETE /notifications/tokens/{deviceId} and DELETE /notifications/tokens (bulk) |
| FCM-03 | User can send a push notification to a single device token with title, body, and custom data payload | `FirebaseMessaging.getInstance().send(Message)` — Notification + data map |
| FCM-04 | User can send a push notification to multiple device tokens (batch/multicast, max 500 per request) | `FirebaseMessaging.getInstance().sendEachForMulticast(MulticastMessage)` — up to 500 tokens |
| FCM-05 | User can send a push notification to a topic | `Message.builder().setTopic(topic)` via `FirebaseMessaging.getInstance().send(message)` |
| FCM-06 | User can subscribe/unsubscribe a device token to/from a topic | `FirebaseMessaging.subscribeToTopic(tokens, topic)` / `unsubscribeFromTopic(tokens, topic)` |
| FCM-07 | Stale device tokens are automatically removed when FCM returns UNREGISTERED/INVALID_ARGUMENT errors | Catch `FirebaseMessagingException`, check `MessagingErrorCode.UNREGISTERED` / `INVALID_ARGUMENT`, delete from DB |
| FCM-08 | Firebase Admin SDK is initialized with service account credentials from environment variable, guarded against double-init | `FirebaseApp.getApps().isEmpty()` guard + `GoogleCredentials.fromStream(base64Decode(FIREBASE_CREDENTIALS_JSON))` |
| FCM-09 | Dev profile logs notifications to console when Firebase credentials are not configured | `ConsolePushService` registered via `@ConditionalOnMissingBean(PushService::class)` — mirrors SmsService/EmailService pattern |
| NMGT-01 | Sent notifications are persisted in a notification history table (type, recipient, title, body, status, timestamp) | NotificationHistory JPA entity; saved before async dispatch as PENDING, updated to SENT/FAILED |
| NMGT-02 | User can view their notification history via API endpoint | GET /api/v1/notifications/history with cursor-based pagination via UUID v7 |
| NFRA-01 | Flyway migration creates device_tokens table (UUID v7 PK, user_id FK, platform enum, token, timestamps) | V3__add_notifications.sql — next after V2 |
| NFRA-02 | Flyway migration creates notification_history table | Same V3 migration file |
| NFRA-04 | New notification/ domain package follows existing project structure conventions (model/repository/service/controller/dto) | Confirmed from project structure: kz.innlab.template.notification.{model,repository,service,controller,dto} |
</phase_requirements>

---

## Summary

This phase adds FCM push notification capability to the existing Spring Boot 4.0 Kotlin template using **Firebase Admin SDK 9.8.0** (released February 25, 2026 — current as of research date). The Firebase Admin SDK is the definitive server-side Java library for FCM; the legacy HTTP API was shut down June 2024 and must not be used. The SDK wraps the FCM HTTP v1 API, which is the only supported API.

The key architectural insight is that the project already has the exact pattern needed: `ConsoleSmsService` and `ConsoleEmailService` are fallback beans registered via `@ConditionalOnMissingBean`. The `ConsolePushService` follows this same pattern exactly. Firebase is initialized in a `@Configuration` class guarded by `FirebaseApp.getApps().isEmpty()` to prevent double-init crashes on Spring context refresh — a known pitfall documented in STATE.md. Credentials are loaded from a base64-encoded JSON in the `FIREBASE_CREDENTIALS_JSON` environment variable (never from files in version control).

FCM send operations are blocking HTTP calls (100–500ms) and must be dispatched asynchronously. The project uses `spring.threads.virtual.enabled=true` (already configured) which means Spring's `@Async` executor uses virtual threads automatically — no extra thread pool configuration needed. The notification history record is written synchronously before the async dispatch so status is always auditable even if the background thread crashes.

**Primary recommendation:** Add `firebase-admin:9.8.0` to pom.xml, implement `PushService` interface with `FirebasePushService` (prod) and `ConsolePushService` (dev fallback), add `NotificationController` under `kz.innlab.template.notification.*` following the existing domain package pattern.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.google.firebase:firebase-admin` | 9.8.0 | FCM send, topic mgmt, credential init | Only supported server-side FCM SDK; legacy API shutdown June 2024 |
| Spring Boot (existing) | 4.0.3 | Web MVC, JPA, Security, Virtual Threads | Already in project |
| Flyway (existing) | Boot-managed | Schema migrations | Already in project — V3 is next |
| uuid-creator (existing) | 6.1.1 | UUID v7 for PKs and pagination cursors | Already in project — BaseEntity uses it |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `google-api-client` (existing) | 2.9.0 | Already declared in pom.xml | Check for version conflict with firebase-admin's transitive `google-http-client`; may need `<dependencyManagement>` with `libraries-bom` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Firebase Admin SDK | Raw FCM HTTP v1 REST calls | SDK manages auth token refresh, retry, connection pooling — hand-rolling is error-prone and more code |
| `@Async` + virtual threads | Kotlin coroutines | Coroutines require `kotlinx-coroutines-reactor`; virtual threads are already active and sufficient for blocking I/O |
| `FIREBASE_CREDENTIALS_JSON` (base64) | `GOOGLE_APPLICATION_CREDENTIALS` file path | Base64 env var works in all environments including container deployments without volume mounts |

**Installation:**

```xml
<!-- Add to pom.xml <dependencies> -->
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.8.0</version>
</dependency>
```

**After adding:** Run `mvn dependency:tree | grep google-http-client` to detect version skew with the existing `google-api-client:2.9.0`. If versions conflict, add `com.google.cloud:libraries-bom` to `<dependencyManagement>` to align them. This is a documented risk in STATE.md.

---

## Architecture Patterns

### Recommended Project Structure

```
src/main/kotlin/kz/innlab/template/
├── notification/
│   ├── model/
│   │   ├── DeviceToken.kt              # JPA entity: UUID v7 PK, user FK, platform enum, fcmToken, deviceId, timestamps
│   │   ├── NotificationHistory.kt      # JPA entity: UUID v7 PK, userId, type, recipient, title, body, status, timestamp
│   │   ├── NotificationTopic.kt        # JPA entity: UUID v7 PK, name (unique), createdAt — admin-managed predefined topics
│   │   └── NotificationStatus.kt       # enum: PENDING, SENT, FAILED
│   ├── repository/
│   │   ├── DeviceTokenRepository.kt    # JpaRepository<DeviceToken, UUID>
│   │   ├── NotificationHistoryRepository.kt
│   │   └── NotificationTopicRepository.kt
│   ├── service/
│   │   ├── PushService.kt              # interface: send(token), sendMulticast(tokens), sendToTopic(topic), subscribe, unsubscribe
│   │   ├── FirebasePushService.kt      # @Service — prod implementation using FirebaseMessaging
│   │   ├── ConsolePushService.kt       # dev fallback — @ConditionalOnMissingBean(PushService::class)
│   │   ├── DeviceTokenService.kt       # register, delete, deleteAll, listByUser, cleanup stale
│   │   ├── NotificationService.kt      # orchestrates: save history PENDING, dispatch async, update SENT/FAILED
│   │   └── TopicService.kt             # admin CRUD for predefined topics; subscribe/unsubscribe device tokens
│   ├── controller/
│   │   ├── NotificationController.kt   # /api/v1/notifications — token mgmt, send, history, topic subscribe
│   │   └── TopicAdminController.kt     # /api/v1/admin/topics — admin create/delete topics
│   └── dto/
│       ├── RegisterTokenRequest.kt
│       ├── DeviceTokenResponse.kt
│       ├── SendNotificationRequest.kt
│       ├── NotificationHistoryResponse.kt
│       └── TopicSubscribeRequest.kt
├── config/
│   └── FirebaseConfig.kt               # @Configuration — FirebaseApp bean with double-init guard
└── resources/
    └── db/migration/
        └── V3__add_notifications.sql   # device_tokens + notification_history + notification_topics tables
```

### Pattern 1: Firebase Initialization with Double-Init Guard

**What:** A `@Configuration` class creates a `FirebaseApp` bean. Guards against double-init by checking `FirebaseApp.getApps().isEmpty()`. Loads credentials from base64-encoded JSON in `FIREBASE_CREDENTIALS_JSON` env var. Returns `null` / is skipped when env var is absent (dev profile console fallback takes over).

**When to use:** Always — Spring context may initialize config classes multiple times during refresh.

```kotlin
// Source: https://firebase.google.com/docs/admin/setup + STATE.md decisions
@Configuration
class FirebaseConfig {

    @Bean
    @ConditionalOnProperty(name = ["app.firebase.enabled"], havingValue = "true", matchIfMissing = false)
    fun firebaseApp(): FirebaseApp {
        if (FirebaseApp.getApps().isEmpty()) {
            val credentialsJson = System.getenv("FIREBASE_CREDENTIALS_JSON")
                ?: error("FIREBASE_CREDENTIALS_JSON env var not set")
            val credentialsBytes = Base64.getDecoder().decode(credentialsJson)
            val credentials = GoogleCredentials.fromStream(credentialsBytes.inputStream())
            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()
            FirebaseApp.initializeApp(options)
        }
        return FirebaseApp.getInstance()
    }
}
```

In `application.yaml` (dev profile): `app.firebase.enabled: false`
In `application.yaml` (prod profile): `app.firebase.enabled: true`

### Pattern 2: Console Fallback with @ConditionalOnMissingBean

**What:** `ConsolePushService` is registered as the `PushService` bean only when no other `PushService` bean is present. When `FirebasePushService` is active (prod), it takes precedence and `ConsolePushService` is never instantiated. This mirrors the exact `ConsoleSmsService` / `ConsoleEmailService` pattern already in the project (see `SmsSchedulerConfig`).

**When to use:** Dev profile or test profile where `FIREBASE_CREDENTIALS_JSON` is absent.

```kotlin
// Source: Mirrors SmsSchedulerConfig.kt pattern in this project
// Registered in NotificationConfig.kt (or extending SmsSchedulerConfig)
@Bean
@ConditionalOnMissingBean(PushService::class)
fun consolePushService(): PushService = ConsolePushService()
```

```kotlin
class ConsolePushService : PushService {
    private val logger = LoggerFactory.getLogger(ConsolePushService::class.java)

    override fun sendToToken(token: String, title: String, body: String, data: Map<String, String>) {
        logger.info("[PUSH] token={} title='{}' body='{}' data={}", token, title, body, data)
    }

    override fun sendToTopic(topic: String, title: String, body: String, data: Map<String, String>) {
        logger.info("[PUSH] topic={} title='{}' body='{}' data={}", topic, title, body, data)
    }

    override fun sendMulticast(tokens: List<String>, title: String, body: String, data: Map<String, String>): BatchResponse? {
        logger.info("[PUSH] multicast tokens={} title='{}' body='{}'", tokens.size, title, body)
        return null
    }

    override fun subscribeToTopic(tokens: List<String>, topic: String) {
        logger.info("[PUSH] subscribe tokens={} topic={}", tokens.size, topic)
    }

    override fun unsubscribeFromTopic(tokens: List<String>, topic: String) {
        logger.info("[PUSH] unsubscribe tokens={} topic={}", tokens.size, topic)
    }
}
```

### Pattern 3: Single Token Send with Stale Token Cleanup

**What:** Send to one FCM token. Catch `FirebaseMessagingException`. If error code is `UNREGISTERED` or `INVALID_ARGUMENT`, delete the token from the database immediately (reactive cleanup — no scheduled job per user decisions).

**When to use:** FCM-03, FCM-07

```kotlin
// Source: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
//         https://firebase.google.com/docs/cloud-messaging/manage-tokens
fun sendToToken(token: String, title: String, body: String, data: Map<String, String>): String {
    val message = Message.builder()
        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
        .putAllData(data)
        .setToken(token)
        .build()
    return FirebaseMessaging.getInstance().send(message)
}

// In NotificationService, after catching FirebaseMessagingException:
catch (e: FirebaseMessagingException) {
    val code = e.messagingErrorCode
    if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
        deviceTokenRepository.deleteByFcmToken(token)  // hard delete per user decision
    }
    historyRecord.status = NotificationStatus.FAILED
    notificationHistoryRepository.save(historyRecord)
}
```

### Pattern 4: Multicast Send (up to 500 tokens)

**What:** `sendEachForMulticast()` sends one message to a list of tokens (max 500). Returns `BatchResponse` where each index corresponds to the input token. Failed tokens with `UNREGISTERED`/`INVALID_ARGUMENT` are extracted and deleted.

**When to use:** FCM-04

```kotlin
// Source: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
fun sendMulticast(tokens: List<String>, title: String, body: String, data: Map<String, String>): BatchResponse {
    val message = MulticastMessage.builder()
        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
        .putAllData(data)
        .addAllTokens(tokens)
        .build()
    return FirebaseMessaging.getInstance().sendEachForMulticast(message)
}

// Stale token extraction after multicast:
val staleTokens = batchResponse.responses
    .mapIndexedNotNull { index, sendResponse ->
        if (!sendResponse.isSuccessful) {
            val code = sendResponse.exception?.messagingErrorCode
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                tokens[index]
            } else null
        } else null
    }
if (staleTokens.isNotEmpty()) {
    deviceTokenRepository.deleteAllByFcmTokenIn(staleTokens)
}
```

### Pattern 5: Topic Send

**What:** Send to a named topic using `Message.builder().setTopic(topic)`. Topic names auto-create in FCM on first subscriber — but per user decisions, topics are predefined in a database table and validated server-side before subscribing/sending.

**When to use:** FCM-05

```kotlin
// Source: https://firebase.google.com/docs/cloud-messaging/send-topic-messages
val message = Message.builder()
    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
    .putAllData(data)
    .setTopic(topicName)  // no leading "/" needed for named topics
    .build()
FirebaseMessaging.getInstance().send(message)
```

### Pattern 6: Topic Subscribe / Unsubscribe

**What:** Admin SDK `subscribeToTopic()` and `unsubscribeFromTopic()` accept up to 1,000 tokens per call. Returns `TopicManagementResponse` with success/failure counts. Validate that the topic exists in the DB before subscribing (predefined topics only per user decisions).

**When to use:** FCM-06

```kotlin
// Source: https://firebase.google.com/docs/cloud-messaging/manage-topic-subscriptions
val response = FirebaseMessaging.getInstance().subscribeToTopic(listOf(fcmToken), topicName)
// response.successCount, response.failureCount, response.errors
```

### Pattern 7: Async Dispatch with History Logging

**What:** NotificationService saves a `NotificationHistory` record with status PENDING before dispatching to FCM. The FCM call is run asynchronously (virtual threads via `@Async`). After the FCM call completes, history status is updated to SENT or FAILED. This ensures the 202 Accepted response is immediate and send attempts are always auditable.

**When to use:** All send endpoints — NMGT-01

```kotlin
@Service
class NotificationService(
    private val pushService: PushService,
    private val historyRepository: NotificationHistoryRepository
) {

    @Transactional
    fun sendToToken(userId: UUID, request: SendNotificationRequest): UUID {
        val history = NotificationHistory(
            userId = userId,
            type = NotificationType.SINGLE,
            recipient = request.token,
            title = request.title,
            body = request.body,
            status = NotificationStatus.PENDING
        )
        historyRepository.save(history)
        dispatchAsync(history.id, request)
        return history.id
    }

    @Async
    fun dispatchAsync(historyId: UUID, request: SendNotificationRequest) {
        val history = historyRepository.findById(historyId).orElseThrow()
        try {
            pushService.sendToToken(request.token, request.title, request.body, request.data)
            history.status = NotificationStatus.SENT
        } catch (e: Exception) {
            history.status = NotificationStatus.FAILED
        } finally {
            historyRepository.save(history)
        }
    }
}
```

**Note:** `@Async` requires `@EnableAsync` on a `@Configuration` class. With `spring.threads.virtual.enabled=true` already set in `application.yaml`, the auto-configured `AsyncTaskExecutor` uses virtual threads — no additional executor bean needed.

### Pattern 8: Cursor-Based Pagination for History

**What:** UUID v7 is time-ordered. Cursor pagination uses the last-seen `id` as a cursor — `WHERE id < :cursor ORDER BY id DESC LIMIT :size`. Matches the existing project UUID v7 pattern documented in STATE.md.

**When to use:** NMGT-02 — GET /api/v1/notifications/history

```kotlin
// In NotificationHistoryRepository
@Query("SELECT n FROM NotificationHistory n WHERE n.userId = :userId AND n.id < :cursor ORDER BY n.id DESC")
fun findByUserIdBeforeCursor(
    @Param("userId") userId: UUID,
    @Param("cursor") cursor: UUID,
    pageable: Pageable
): List<NotificationHistory>
```

### Pattern 9: Database Schema (Flyway V3)

**What:** Next migration is V3. Never skip version numbers (STATE.md rule). H2 test profile keeps `spring.flyway.enabled=false` so migration syntax is PostgreSQL-specific without H2 compatibility concerns.

**When to use:** NFRA-01, NFRA-02

```sql
-- V3__add_notifications.sql

CREATE TABLE device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(10) NOT NULL CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    fcm_token TEXT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_tokens_user_device UNIQUE (user_id, device_id)
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);
CREATE INDEX idx_device_tokens_fcm_token ON device_tokens(fcm_token);

CREATE TABLE notification_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('SINGLE', 'MULTICAST', 'TOPIC')),
    recipient TEXT NOT NULL,              -- token, comma-separated tokens, or topic name
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    data JSONB,                           -- custom data payload
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_history_user_id ON notification_history(user_id);
-- UUID v7 is time-ordered, so id IS the cursor — no separate timestamp index needed for pagination

CREATE TABLE notification_topics (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Anti-Patterns to Avoid

- **Using the legacy FCM HTTP API:** Shut down June 2024. Only firebase-admin SDK (HTTP v1 API) works.
- **Not guarding FirebaseApp.initializeApp():** Calling it twice throws `IllegalStateException`. Always check `FirebaseApp.getApps().isEmpty()` first.
- **Storing service account JSON in version control:** Even encrypted. Use base64-encoded env var.
- **Synchronous FCM calls on the request thread:** FCM `send()` is a 100–500ms HTTP call. Always dispatch via `@Async`. Return 202 Accepted immediately.
- **Not handling INVALID_ARGUMENT for stale tokens:** INVALID_ARGUMENT indicates an invalid token ONLY when the payload is valid. The project sends structured requests, so INVALID_ARGUMENT on a valid payload means stale token.
- **Hardcoding token limits:** Max tokens per user must be `@Value("${app.notification.token.max-per-user:5}")` per user decision.
- **Holding FCM tokens as static: Never using deviceId as the primary deduplication key:** FCM tokens change on app reinstall/update. `deviceId` (user-supplied, e.g., device UUID) is the stable identifier. On re-register with same deviceId, update the fcm_token (upsert pattern).
- **Sending to FCM topics that don't exist in the DB:** Validate topic exists in `notification_topics` table before subscribing or sending.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| FCM token management | Custom HTTP client calling FCM REST | `firebase-admin` SDK | Auth token refresh, connection pooling, retry, error code mapping — all handled |
| Service account auth | Manual OAuth2 token generation | `GoogleCredentials.fromStream()` | Certificate rotation, token caching, scoped credentials — complex to get right |
| Multicast response parsing | Custom error aggregation | `BatchResponse.responses[i].exception?.messagingErrorCode` | SDK maintains 1-1 token-to-response mapping |
| Topic subscription limits | Manual batching beyond 1,000 | SDK enforces: `subscribeToTopic` throws `InvalidArgument` over 1,000 tokens | Not a practical concern for this template |
| History cursor pagination | Offset pagination | UUID v7 natural ordering — `WHERE id < :cursor ORDER BY id DESC LIMIT N` | Stable under concurrent inserts; no count queries |

**Key insight:** firebase-admin is a thin wrapper over the FCM HTTP v1 API but handles the significant complexity of Google credential management (OAuth2 service account tokens that expire every 60 minutes). Never hand-roll the credential refresh loop.

---

## Common Pitfalls

### Pitfall 1: Double Firebase Initialization (FirebaseApp Already Exists)
**What goes wrong:** `FirebaseApp.initializeApp()` called twice throws `IllegalStateException: FirebaseApp name [DEFAULT] already exists!`. This happens on Spring context refresh, test context reuse, or if two config classes both call `initializeApp()`.
**Why it happens:** Spring may create the application context multiple times in tests or with certain lifecycle hooks.
**How to avoid:** Always guard with `if (FirebaseApp.getApps().isEmpty())`. Set `app.firebase.enabled=false` in test profile (`src/test/resources/application.yaml`).
**Warning signs:** `IllegalStateException` on startup mentioning `[DEFAULT]`.

### Pitfall 2: google-http-client Version Skew
**What goes wrong:** Build or runtime `ClassNotFoundException` / `NoClassDefFoundError` due to `firebase-admin` and `google-api-client` (already in pom.xml at 2.9.0) pulling different versions of `google-http-client`.
**Why it happens:** Both depend on `google-http-client` transitively; Maven picks "nearest" but the versions may be binary-incompatible.
**How to avoid:** After adding `firebase-admin`, run `mvn dependency:tree | grep google-http-client`. If multiple versions appear, add `com.google.cloud:libraries-bom` to `<dependencyManagement>` to align. Documented as Phase 1 risk in STATE.md.
**Warning signs:** Runtime errors referencing `com.google.api.client.*` classes.

### Pitfall 3: Treating INVALID_ARGUMENT as Always Meaning Bad Payload
**What goes wrong:** Developer catches `INVALID_ARGUMENT` but only deletes token for `UNREGISTERED`, missing half of stale token cleanup.
**Why it happens:** `INVALID_ARGUMENT` can mean either bad payload OR invalid token. Documentation is nuanced.
**How to avoid:** Per Firebase docs: "If you are certain that the message payload is valid and you receive either UNREGISTERED or INVALID_ARGUMENT for a targeted token, it is safe to delete the token." Since the project controls the payload and validates it, catching both codes for cleanup is correct.
**Warning signs:** Growing database of unreachable tokens; FCM send success rate dropping over time.

### Pitfall 4: @Async Not Working (Self-Invocation)
**What goes wrong:** Calling an `@Async` method from within the same class (self-invocation) bypasses the Spring AOP proxy — the method runs synchronously on the request thread.
**Why it happens:** Spring's `@Async` is proxy-based; calling `this.dispatchAsync()` bypasses the proxy.
**How to avoid:** Put `@Async` methods in a separate `@Service` class (e.g., `NotificationDispatcher`) and inject it. Or use `ApplicationContext.getBean()` to get the proxied self.
**Warning signs:** Requests block for 100–500ms instead of returning 202 immediately.

### Pitfall 5: Missing @EnableAsync
**What goes wrong:** `@Async` annotations are silently ignored — methods run synchronously with no warning.
**Why it happens:** `@EnableAsync` must be present on a `@Configuration` class.
**How to avoid:** Add `@EnableAsync` to `FirebaseConfig` or a dedicated `AsyncConfig` class.
**Warning signs:** Response times match FCM latency instead of being near-instant.

### Pitfall 6: Notification History Transaction Boundary
**What goes wrong:** History record saved inside the `@Async` method — if the async method never runs (JVM shutdown) or the thread pool is exhausted, no audit trail exists.
**Why it happens:** Async dispatch happens outside the calling transaction.
**How to avoid:** Save `NotificationHistory` with status PENDING in the synchronous controller/service method BEFORE dispatching async. The async method only updates from PENDING to SENT/FAILED.
**Warning signs:** Missing history records for sends that "should have happened."

### Pitfall 7: FCM Topic Name Validation
**What goes wrong:** Topics are created in FCM automatically on first `subscribeToTopic()` call. Without server-side validation, clients can create arbitrary topics (e.g., `../../admin`).
**Why it happens:** FCM auto-creates topics; there is no FCM API to list or restrict topics.
**How to avoid:** Validate `topicName` against `notification_topics` table (predefined per user decisions). Reject subscription requests for unknown topics.
**Warning signs:** Proliferation of garbage topics in FCM dashboard; impossible to manage.

---

## Code Examples

Verified patterns from official sources:

### Firebase Admin SDK Initialization (Kotlin)

```kotlin
// Source: https://firebase.google.com/docs/admin/setup
// Source: https://111coding.github.io/blog/2024-08-24-springboot-firebase-json
@Configuration
@EnableAsync
class FirebaseConfig {

    @Bean
    @ConditionalOnProperty(name = ["app.firebase.enabled"], havingValue = "true")
    fun firebaseApp(): FirebaseApp {
        if (FirebaseApp.getApps().isEmpty()) {
            val base64Credentials = System.getenv("FIREBASE_CREDENTIALS_JSON")
                ?: error("FIREBASE_CREDENTIALS_JSON not set")
            val credentialBytes = Base64.getDecoder().decode(base64Credentials)
            val credentials = GoogleCredentials.fromStream(credentialBytes.inputStream())
            FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(credentials).build()
            )
        }
        return FirebaseApp.getInstance()
    }
}
```

### Single Token Send

```kotlin
// Source: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
val message = Message.builder()
    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
    .putAllData(data)
    .setToken(fcmToken)
    .build()
val messageId: String = FirebaseMessaging.getInstance().send(message)
```

### Multicast Send with Stale Token Extraction

```kotlin
// Source: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
val multicast = MulticastMessage.builder()
    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
    .putAllData(data)
    .addAllTokens(tokens)   // max 500
    .build()
val batchResponse: BatchResponse = FirebaseMessaging.getInstance().sendEachForMulticast(multicast)

val staleTokens = tokens.filterIndexed { i, _ ->
    val resp = batchResponse.responses[i]
    !resp.isSuccessful && resp.exception?.messagingErrorCode.let {
        it == MessagingErrorCode.UNREGISTERED || it == MessagingErrorCode.INVALID_ARGUMENT
    }
}
```

### Topic Subscription Management

```kotlin
// Source: https://firebase.google.com/docs/cloud-messaging/manage-topic-subscriptions
// Max 1,000 tokens per call
val response: TopicManagementResponse =
    FirebaseMessaging.getInstance().subscribeToTopic(listOf(fcmToken), topicName)
// response.successCount, response.failureCount
```

### Stale Token Error Handling

```kotlin
// Source: https://firebase.google.com/docs/cloud-messaging/manage-tokens
try {
    FirebaseMessaging.getInstance().send(message)
} catch (e: FirebaseMessagingException) {
    when (e.messagingErrorCode) {
        MessagingErrorCode.UNREGISTERED,
        MessagingErrorCode.INVALID_ARGUMENT -> {
            // Safe to delete — payload is valid, token is stale
            deviceTokenRepository.deleteByFcmToken(fcmToken)
        }
        else -> logger.warn("FCM send failed: {}", e.messagingErrorCode)
    }
    throw e  // re-throw to mark history as FAILED
}
```

### Endpoint Design Recommendation

For Claude's Discretion on endpoint structure — recommend **separate endpoints** for clarity and documentation:

```
POST   /api/v1/notifications/tokens              → 201 Created — register device token
GET    /api/v1/notifications/tokens              → 200 OK — list user's tokens
DELETE /api/v1/notifications/tokens/{deviceId}   → 204 No Content — delete single token
DELETE /api/v1/notifications/tokens              → 204 No Content — delete all user tokens

POST   /api/v1/notifications/send/token          → 202 Accepted — send to single token
POST   /api/v1/notifications/send/multicast      → 202 Accepted — send to up to 500 tokens
POST   /api/v1/notifications/send/topic          → 202 Accepted — send to a topic

POST   /api/v1/notifications/topics/{name}/subscribe    → 200 OK
DELETE /api/v1/notifications/topics/{name}/subscribe    → 200 OK (unsubscribe)

GET    /api/v1/notifications/history             → 200 OK — cursor-paginated history

POST   /api/v1/admin/topics                      → 201 Created — admin: create predefined topic
DELETE /api/v1/admin/topics/{name}               → 204 No Content — admin: remove topic
```

**Deduplication recommendation (Claude's Discretion):** Use `deviceId` (user-supplied stable identifier) as the unique key per user. On registration with existing `deviceId`, upsert the `fcm_token` and update `updated_at`. This handles app reinstall/token refresh without duplicate rows.

**Topic send authorization recommendation (Claude's Discretion):** Admin-only for `/send/topic` — topic sends are broadcast operations affecting all subscribers; any authenticated user sending to any topic is too broad for a template.

**Notification history read/dismiss (Claude's Discretion):** History-only (no dismiss) — matches the audit trail intent. Adding a `read_at` column is low value for a push notification template; consuming projects add it when needed.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| FCM Legacy HTTP API | FCM HTTP v1 API (Admin SDK) | Shutdown June 2024 | Legacy API is dead — only firebase-admin SDK works |
| `sendMulticast()` | `sendEachForMulticast()` | firebase-admin 9.x | `sendMulticast` deprecated; `sendEachForMulticast` is current |
| Platform thread pool for async | Virtual Threads (Spring Boot 3+) | Spring Boot 3.2 / Boot 4 | No explicit executor config needed with `spring.threads.virtual.enabled=true` |
| `GOOGLE_APPLICATION_CREDENTIALS` file path | Base64 env var + `GoogleCredentials.fromStream()` | Deployment best practice | Works in containers without volume mounts; no file access needed |
| Topic creation via FCM (auto) | Server-managed predefined topics in DB | Project decision | Prevents arbitrary topic creation; enforces known topic set |

**Deprecated/outdated:**
- `FirebaseMessaging.sendMulticast()`: Deprecated in favor of `sendEachForMulticast()` — use `sendEachForMulticast()` throughout
- FCM Legacy HTTP API (firebase.googleapis.com/fcm/send): Shut down June 2024 — never reference it
- `firebase-admin < 9.x`: Version 9.0 introduced `sendEachForMulticast`; use 9.8.0 (latest as of Feb 2026)

---

## Open Questions

1. **google-http-client version conflict severity**
   - What we know: `firebase-admin:9.8.0` and `google-api-client:2.9.0` (already in pom.xml) both pull `google-http-client` transitively. STATE.md flags this as a known risk.
   - What's unclear: Whether `firebase-admin 9.8.0` + `google-api-client 2.9.0` conflict in practice. The `libraries-bom v26.76.0` (upgraded in firebase-admin 9.8.0 per release notes) may resolve this.
   - Recommendation: Run `mvn dependency:tree | grep google-http-client` after adding the dependency. If two versions appear, add `libraries-bom` to `<dependencyManagement>`. Make this the first task after adding the dependency.

2. **@Async self-invocation boundary**
   - What we know: `@Async` methods called from the same class bypass the Spring proxy.
   - What's unclear: Whether to put async dispatch in `NotificationService` itself (requires a separate `NotificationDispatcher` bean) or inline in the controller with a separate service layer.
   - Recommendation: Create a separate `NotificationDispatcher` `@Service` that owns the `@Async` dispatch method. `NotificationService` handles the synchronous parts (save PENDING, call dispatcher). Clean separation, avoids proxy bypass.

3. **JSONB data field — H2 test compatibility**
   - What we know: H2 test profile has `spring.flyway.enabled=false` and uses `ddl-auto: create-drop`. JSONB is PostgreSQL-specific.
   - What's unclear: Whether Hibernate maps `JSONB` cleanly in H2 via `ddl-auto: create-drop`, or whether the entity mapping needs a different approach for tests.
   - Recommendation: Use `String` type for the `data` field in the JPA entity with `@Column(columnDefinition = "jsonb")` for prod schema. For test profile, Hibernate `create-drop` with H2 will use VARCHAR — which is compatible. If not, store data as `TEXT` in the entity and use `@Column(columnDefinition = "text")` in the migration. Low risk.

---

## Sources

### Primary (HIGH confidence)
- [Firebase Admin SDK Setup — firebase.google.com](https://firebase.google.com/docs/admin/setup) — initialization, credential loading, double-init guard
- [Send FCM Messages — firebase.google.com](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk) — Message, MulticastMessage, sendEachForMulticast, BatchResponse
- [Manage FCM Tokens — firebase.google.com](https://firebase.google.com/docs/cloud-messaging/manage-tokens) — UNREGISTERED/INVALID_ARGUMENT error handling, cleanup strategy
- [Topic Subscription Management — firebase.google.com](https://firebase.google.com/docs/cloud-messaging/manage-topic-subscriptions) — subscribeToTopic, unsubscribeFromTopic, TopicManagementResponse
- [Firebase Admin Java Release Notes — firebase.google.com](https://firebase.google.com/support/release-notes/admin/java) — confirmed 9.8.0 is latest (Feb 25, 2026), sendEachForMulticast API
- [MessagingErrorCode — firebase.google.com](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/messaging/MessagingErrorCode) — UNREGISTERED, INVALID_ARGUMENT enum values
- Project source files: `ConsoleSmsService.kt`, `SmsSchedulerConfig.kt`, `ConsoleEmailService.kt`, `BaseEntity.kt`, `RefreshTokenService.kt` — confirmed existing patterns

### Secondary (MEDIUM confidence)
- [111coding.github.io — Firebase credentials as base64 env var](https://111coding.github.io/blog/2024-08-24-springboot-firebase-json) — base64 credential approach confirmed by official docs
- [firebase.google.com/docs/cloud-messaging/send-topic-messages](https://firebase.google.com/docs/cloud-messaging/send-topic-messages) — topic send via Admin SDK
- WebSearch: firebase-admin 9.8.0 Maven dependency — confirmed version number matches release notes

### Tertiary (LOW confidence)
- None — all critical claims verified against official Firebase documentation

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — firebase-admin 9.8.0 confirmed from release notes (Feb 25, 2026); FCM HTTP v1 API is the only API
- Architecture: HIGH — patterns verified against official docs; project conventions confirmed from source code
- Pitfalls: HIGH — double-init, dependency conflict, and async patterns verified from official sources and project STATE.md

**Research date:** 2026-03-03
**Valid until:** 2026-06-03 (90 days — firebase-admin releases are infrequent; FCM API is stable)
