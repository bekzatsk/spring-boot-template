# Feature Research

**Domain:** Push Notifications (FCM) + Email Service (SMTP sending / IMAP receiving) — Spring Boot 4 auth backend v6.0 milestone
**Researched:** 2026-03-03
**Confidence:** HIGH for SMTP (Spring Mail is mature, well-documented); MEDIUM-HIGH for FCM HTTP v1 API (official docs clear; Spring Boot 4 specifics limited in production reports); MEDIUM for IMAP receiving (Spring Integration docs clear; operational complexity is nuanced)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features any backend offering push + email integration must provide. Missing these = the integration is not usable.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| FCM device token registration (store per-user) | Clients send a token after FCM SDK init; without persisting it, no notifications can be delivered | LOW | One POST endpoint; store token + userId + platform (ANDROID/IOS/WEB) + updatedAt in DB |
| FCM send to single device (token-targeted) | Core delivery primitive — every notification feature depends on this | LOW | FCM HTTP v1 API `messages:send`; service-account OAuth2 bearer token required (NOT legacy server key — deprecated June 2024) |
| FCM topic subscribe / unsubscribe | Group-based delivery (e.g., "all-users", "premium") without iterating tokens server-side | LOW | Firebase Admin SDK `subscribeToTopic()` / `unsubscribeFromTopic()`; topic names are app-defined strings |
| FCM send to topic | Broadcast to all topic subscribers in one API call | LOW | `Message.builder().setTopic(...)` — same endpoint as single-device send |
| FCM notification payload (title + body) | Minimum visible notification — Android and iOS render this natively without client code | LOW | `Notification` object in `Message`; both platforms auto-display |
| FCM data payload (custom JSON map) | Application logic driven by server (deep-link to screen, update badge count, silent refresh) | LOW | `data` map alongside or instead of `Notification`; client handles silently in background |
| Stale token deletion on FCM error | Sending to dead tokens wastes API quota; `UNREGISTERED` and `INVALID_ARGUMENT` errors from FCM indicate the token is dead | MEDIUM | Inspect FCM send response error codes; delete token row from DB on receipt of those codes |
| SMTP email sending — plain text | Baseline transactional delivery: already needed for existing auth flows (forgot-password, change-email) | LOW | `JavaMailSender` + `SimpleMailMessage`; Spring Boot auto-configures the bean from `spring.mail.*` properties |
| SMTP email sending — HTML | Transactional emails with branding, links, formatted layout | LOW | `MimeMessage` + `MimeMessageHelper`; produce HTML string (inline or from template) |
| SMTP email sending — attachments | File delivery: invoices, exports, reports | MEDIUM | `MimeMessageHelper.addAttachment()`; multipart MIME encoding; handled by the helper |
| SMTP retry on transient failure | Mail servers reject or timeout under load; silent loss is unacceptable for transactional email | MEDIUM | Spring Retry `@Retryable` on the send method; backoff on `MailException`; log to dead-letter on exhaustion |
| IMAP inbox listing (unread messages) | Read received emails programmatically: inbound support requests, auto-replies, webhook responses | MEDIUM | `JavaMail` `Store.getFolder("INBOX").open(READ_ONLY)`; search with `FlagTerm(Flags.Flag.SEEN, false)` |
| IMAP message fetch (headers + body) | Retrieve full email content — subject, sender, date, body (plain text or HTML) | MEDIUM | `MimeMessage.getContent()`; must handle multipart recursively; extract text/plain vs text/html parts |
| Env-var-based configuration | Firebase service account JSON path, SMTP host/port/user/pass must never be hardcoded | LOW | `application.yml` `${ENV_VAR}` pattern — already established in this project from v1.0 |

### Differentiators (Competitive Advantage)

Features not universally expected but meaningful for a production-grade backend template.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Multi-device token support per user | Users log in on phone + tablet; all devices should receive notifications | LOW | `device_tokens` table: one row per (userId, token) pair; fan-out on send by querying all tokens for user |
| FCM batch / multicast send | Avoid N API calls for N devices; use `MulticastMessage` for up to 500 tokens per call | LOW | `FirebaseMessaging.sendEachForMulticast()`; handle partial failures (some tokens may fail in batch) |
| Platform-specific FCM overrides | Android needs `channelId`; iOS needs `apns-priority`, sound, badge | MEDIUM | `AndroidConfig` + `ApnsConfig` in `Message.builder()`; configure per-send or per-template |
| Async email sending | Prevent SMTP connect/send latency from blocking API response threads | LOW | `@Async` on mail service method; this project uses Virtual Threads — fits naturally with the existing executor |
| FCM dry-run / validation mode | Test message construction without delivering to devices | LOW | `Message.setValidateOnly(true)` flag in FCM API; useful in test/staging profiles |
| Token freshness enforcement | Proactively request new tokens from clients monthly to avoid stale-token accumulation | LOW | Store `updatedAt` on device_token row; expose a PATCH endpoint or accept token on every auth call; official Firebase guidance recommends monthly refresh |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| FCM legacy HTTP API (non-v1) | Many tutorials still show `Authorization: key=<server_key>` | Google deprecated the legacy FCM API; sunset was June 2024 — it will stop working | FCM HTTP v1 API only, authenticated with service-account OAuth2 bearer token |
| Notification history stored in DB | "Users want to see past notifications" | Significant schema + API surface (pagination, read/unread state, delete); out of scope for a template | Document as extension point; consuming app should add a `notifications` table when needed |
| In-process persistent email queue | "What if SMTP is down?" | Reinvents a message broker; adds DB schema complexity; partial delivery tracking is hard | `@Retryable` with configurable attempts + backoff; log to dead-letter; escalate to Redis/RabbitMQ only when proven necessary |
| IMAP IDLE as MVP inbound channel | Real-time feels better than polling | IMAP IDLE connections are fragile (servers drop idle sessions after 20–30 min); error recovery and reconnection logic is complex; rarely justified at template stage | Use polling IMAP (scheduled `@Scheduled`) for MVP; add IDLE only if latency requirement is proven by a real use case |
| Sending email directly from auth service | "Just call the mail service from auth code" | Creates hard coupling between auth and notification; makes auth tests require SMTP infra | Auth already uses `VerificationCode` infrastructure; shared `MailService` bean is the correct abstraction — auth can call it, but they remain separate services |
| Managing notification preferences in-app | "GDPR requires opt-out" | Complex preference matrix; category management; not a template concern | Leave to consuming application; expose service-layer hooks/events for extensibility |
| Storing raw email content in DB | "Need audit trail of emails sent" | GDPR compliance complexity; large BLOB storage; not a template concern | Log email metadata (recipient, subject, timestamp, status) only; omit body |

---

## Feature Dependencies

```
[FCM Send to Single Device]
    └──requires──> [FCM Device Token in DB]
                       └──requires──> [Token Registration Endpoint]
                                          └──requires──> [JWT auth (existing v1.0)]

[FCM Send to Topic]
    └──requires──> [FCM Topic Subscribe]
                       └──requires──> [FCM Device Token in DB]

[FCM Batch/Multicast Send]
    └──requires──> [Multi-device tokens per user]
                       └──requires──> [FCM Device Token in DB]

[FCM Stale Token Cleanup]
    └──requires──> [FCM Send to Single Device] (reads error codes from send response)

[SMTP HTML Email]
    └──requires──> [SMTP Plain Text Email] (same JavaMailSender bean; HTML is additive)

[SMTP Attachments]
    └──requires──> [SMTP HTML Email] (multipart MimeMessage setup already in place)

[SMTP Retry]
    └──enhances──> [SMTP Plain Text Email] (wraps the send call with @Retryable)

[Async Email]
    └──enhances──> [SMTP Plain Text Email] (@Async wrapper; non-blocking)

[IMAP Message Fetch]
    └──requires──> [IMAP Inbox Listing] (folder must be opened before messages can be read)

[IMAP IDLE]
    └──requires──> [IMAP Inbox Listing] (same Store/Folder infrastructure; IDLE is a delivery mode)

[FCM Dry-Run]
    ──enhances──> [FCM Send to Single Device] (same API path, validateOnly flag)

[Platform-specific FCM Overrides]
    ──enhances──> [FCM Send to Single Device] (additive config on the same Message)
```

### Dependency Notes

- **Token registration is the root of the entire FCM subtree:** No stored token = no notification recipient. The `device_tokens` DB table and its registration endpoint must be built first.
- **FCM stale cleanup depends on send:** The only reliable detection of dead tokens is observing `UNREGISTERED` / `INVALID_ARGUMENT` error codes in the FCM send response. Cannot be done proactively without attempting delivery.
- **SMTP plain-text is the base:** All HTML and attachment capability uses the same auto-configured `JavaMailSender` bean. The bean is already needed by existing auth flows (forgot-password, change-email). Do not create a second configuration.
- **IMAP listing before fetch:** `Folder.open()` + search is required before any `MimeMessage` content can be read. They are sequential in the same method, not separate phases.
- **Existing auth dependency for FCM endpoints:** All new notification endpoints must be behind the existing Bearer token filter (Spring Security v1.0). UserId is extracted from `SecurityContextHolder` — no new auth mechanism needed.
- **Flyway dependency for new tables:** `device_tokens` table requires a new Flyway migration (V6). The existing V1–V5 migrations must not be modified.

---

## MVP Definition

This is a backend template milestone (v6.0), not a product launch. MVP = smallest surface that proves the integration works and is reusable by consuming projects.

### Launch With (v6.0)

- [x] FCM device token registration endpoint (POST /api/v1/notifications/tokens)
- [x] FCM device token deletion endpoint (DELETE /api/v1/notifications/tokens/{token})
- [x] FCM send to single device by token (POST /api/v1/notifications/send)
- [x] FCM send to topic (POST /api/v1/notifications/send-topic)
- [x] FCM topic subscribe / unsubscribe endpoints
- [x] FCM notification + data payload support in send request
- [x] Stale token deletion on UNREGISTERED / INVALID_ARGUMENT FCM error
- [x] SMTP sending — plain text and HTML (JavaMailSender + MimeMessage + MimeMessageHelper)
- [x] SMTP sending — attachments (MimeMessageHelper.addAttachment)
- [x] Async email sending (@Async on MailService methods)
- [x] SMTP retry on transient failure (@Retryable)
- [x] IMAP inbox listing — unread messages (subject, sender, date, snippet)
- [x] IMAP message fetch — full body (plain text extracted from multipart)
- [x] Flyway V6 migration for device_tokens table
- [x] Env-var configuration for Firebase service account + SMTP mail server

### Add After Validation (v6.x)

- [ ] Multi-device token support (fan-out send to all user devices) — add after first real app integration shows multi-device is needed
- [ ] FCM batch/multicast send — add when single-device fan-out N calls becomes a performance concern
- [ ] Platform-specific FCM overrides (AndroidConfig / ApnsConfig) — add when iOS/Android payload divergence is needed by a real consumer
- [ ] Email templates (Thymeleaf or inline HTML strings) — add when consuming project needs branded transactional email

### Future Consideration (v7+)

- [ ] IMAP IDLE real-time receiving — defer until polling latency is proven insufficient by a real use case
- [ ] Notification history (DB-stored, paginated) — out of scope for template; consuming app concern
- [ ] FCM dry-run validation mode — low value until integration testing framework is needed by consumers
- [ ] Token freshness enforcement (monthly re-registration prompt) — app-level concern; document as recommendation

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| FCM device token registration | HIGH | LOW | P1 |
| FCM send to single device | HIGH | LOW | P1 |
| FCM send to topic | HIGH | LOW | P1 |
| FCM topic subscribe/unsubscribe | HIGH | LOW | P1 |
| FCM notification + data payload | HIGH | LOW | P1 |
| Stale token cleanup (on send error) | MEDIUM | LOW | P1 |
| Flyway V6 migration (device_tokens) | HIGH | LOW | P1 |
| SMTP plain text + HTML | HIGH | LOW | P1 |
| SMTP attachments | MEDIUM | LOW | P1 |
| Async email (@Async) | HIGH | LOW | P1 |
| SMTP retry (@Retryable) | MEDIUM | LOW | P1 |
| IMAP inbox listing + message fetch | MEDIUM | MEDIUM | P1 |
| Env-var configuration (FCM + mail) | HIGH | LOW | P1 |
| Multi-device token support | MEDIUM | LOW | P2 |
| FCM batch/multicast send | MEDIUM | LOW | P2 |
| Platform-specific FCM overrides | MEDIUM | MEDIUM | P2 |
| Email templates (Thymeleaf) | MEDIUM | MEDIUM | P2 |
| FCM dry-run mode | LOW | LOW | P2 |
| IMAP IDLE | LOW | HIGH | P3 |
| Notification history (DB) | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for v6.0 launch
- P2: Should have; add when consuming project demands it
- P3: Nice to have; future milestone

---

## Existing System Integration Points

Dependencies on already-built v1.0–v5.0 features that the new v6.0 features must respect:

| New Feature | Existing Dependency | Integration Note |
|-------------|---------------------|-----------------|
| FCM token registration/send endpoints | JWT auth + Spring Security (v1.0) | All endpoints must be authenticated; extract userId from `SecurityContextHolder` via existing filter chain |
| SMTP mail service | VerificationCode + existing mail usage (v5.0) | Reuse the same `JavaMailSender` auto-configured bean; do not create a second `spring.mail.*` configuration |
| Async email | Virtual Threads executor (v1.0) | `@Async` with Virtual Threads — no separate thread pool required; fits the existing concurrency model |
| device_tokens DB table | UUID v7 + Flyway (v4.0, v2.0) | New table PK uses UUID v7 (uuid-creator already on classpath); new migration is V6 — must not alter V1–V5 |
| All new endpoints | Spring Security 7 stateless (v1.0) | No sessions, no CSRF, Bearer token required; CORS already configured |
| FCM service account config | application.yml profiles (v1.0) | Firebase service account JSON file path + SMTP credentials follow established `${ENV_VAR}` pattern |

---

## Sources

- [FCM: Best practices for registration token management — Firebase official](https://firebase.google.com/docs/cloud-messaging/manage-tokens) — HIGH confidence
- [Firebase Blog: Managing Cloud Messaging Tokens (2023)](https://firebase.blog/posts/2023/04/managing-cloud-messaging-tokens/) — HIGH confidence
- [Spring Boot + FCM HTTP v1 API — Baeldung](https://www.baeldung.com/spring-fcm) — MEDIUM confidence (Baeldung, verified against Firebase docs)
- [FCM HTTP v1 Spring Boot reference implementation — GitHub](https://github.com/mdtalalwasim/Firebase-Cloud-Messaging-FCM-v1-API-Spring-boot) — LOW confidence (community; used for pattern reference only)
- [Guide to Spring Email — Baeldung](https://www.baeldung.com/spring-email) — HIGH confidence (verified against Spring Boot official docs)
- [Sending Email — Spring Boot official docs](https://docs.spring.io/spring-boot/reference/io/email.html) — HIGH confidence (official)
- [Mail Support — Spring Integration official docs](https://docs.spring.io/spring-integration/reference/mail.html) — HIGH confidence (official; covers IMAP polling and IDLE)
- [Spring Boot: Monitor incoming emails indefinitely — Medium](https://medium.com/@sushant7/how-to-monitor-incoming-emails-in-a-spring-boot-application-indefinitely-7dabbdb74b2d) — LOW confidence (community; used for IMAP IDLE complexity assessment)

---

*Feature research for: FCM push notifications + SMTP/IMAP email service (Spring Boot 4 auth backend v6.0)*
*Researched: 2026-03-03*
