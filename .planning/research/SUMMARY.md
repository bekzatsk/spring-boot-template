# Project Research Summary

**Project:** Spring Boot 4 Auth Backend — v6.0 Notifications Milestone (FCM + Email)
**Domain:** Firebase Cloud Messaging push notifications + SMTP/IMAP email on Spring Boot 4 + Kotlin
**Researched:** 2026-03-03
**Confidence:** HIGH

---

## Executive Summary

This milestone adds Firebase Cloud Messaging (FCM) push notifications and a full SMTP/IMAP email service to an existing Spring Boot 4 + Kotlin auth backend (v1–v5 already shipped: JWT auth, account linking, SMS verification, UUID v7, account management). The integration is well-understood: `firebase-admin` SDK 9.8.0 for FCM, `spring-boot-starter-mail` for SMTP, and raw Jakarta Mail `Store` for IMAP receive. All three are managed by the Boot 4 BOM or have pinned stable versions. The recommended architecture introduces two new top-level domain packages (`notification/` and `mail/`) that mirror the existing `user/` and `authentication/` structure, keeping domain concerns isolated.

The recommended approach is to treat FCM and Email as independent service layers behind interfaces (`PushService`, `MailService`) with console/stub fallbacks for dev and test profiles — exactly the pattern already used for `SmsService` and `EmailService` in this codebase. `SmtpMailService` implements both the existing `EmailService` interface (auth verification codes) and the new `MailService` interface (general email), eliminating any changes to existing callers in `VerificationCodeService`. FCM is gated behind `@ConditionalOnProperty(app.firebase.enabled)` so CI and dev profiles never touch real Firebase. No existing configs (`SecurityConfig`, `SmsSchedulerConfig`) require changes.

The principal risks are operational, not architectural. Firebase service account credentials must never enter the JAR or git history; the `FirebaseApp` singleton must be guarded against double-initialization; FCM send and SMTP send must be async (`@Async`) to avoid blocking Virtual Threads under load; IMAP `Store` connections must be opened and closed per-request to prevent connection-limit exhaustion; and FCM's `UNREGISTERED` error codes must be handled on every send to purge stale device tokens. All of these are straightforward to address from the start if the pitfalls are front-of-mind during implementation. The feature set for v6.0 is entirely P1 and maps to two coherent phases in a clear build order.

---

## Key Findings

### Recommended Stack

Spring Boot 4.0.3 manages all Spring versions. Three new dependencies are added: `firebase-admin:9.8.0` (explicit version — not in Boot BOM), `spring-boot-starter-mail` (Boot-managed), and `spring-integration-mail` (Boot-managed). `nimbus-jose-jwt:10.8` is already transitive from the auth server starter (added in v1.0). GreenMail 2.1.3 (test scope) provides in-process SMTP/IMAP for integration tests without an external server. Mailpit replaces MailHog for local dev SMTP/IMAP (MailHog unmaintained since 2022). A potential Google library version conflict between `firebase-admin` and `google-api-client` (both pull `google-http-client`) can be resolved by importing `com.google.cloud:libraries-bom` in `<dependencyManagement>` if it surfaces during `mvn dependency:tree`.

**Core technologies:**
- `firebase-admin` 9.8.0: FCM HTTP v1 API — only official JVM SDK; FCM legacy API shut down June 2024; no viable alternative
- `spring-boot-starter-mail`: SMTP via `JavaMailSender` — first-party Boot starter, auto-configured from `spring.mail.*` properties; version managed by Boot BOM
- `spring-integration-mail`: IMAP polling via `ImapMailReceiver` — Spring's standard module; handles folder lifecycle, reconnect, and IDLE
- `greenmail-junit5` 2.1.3: In-process SMTP+IMAP for integration tests — enables full send-receive path without external server
- `firebase-admin` credentials: inject via `FIREBASE_CREDENTIALS_JSON` env var (base64) or volume-mounted file — never in `src/main/resources/`

**What NOT to add:**
- FCM legacy HTTP API (`Authorization: key=<server_key>`) — shutdown June 2024; all requests return errors
- `firebase-admin` < 9.0.0 — targets the now-dead legacy API
- `javax.mail` / `com.sun.mail:javax.mail` — pre-Jakarta namespace, incompatible with Spring Boot 4
- `spring-boot-starter-thymeleaf` — unnecessary unless HTML email templates are in scope; add only for v6.x

### Expected Features

**Must have for v6.0 (P1 — launch with these):**
- FCM device token registration endpoint (`POST /api/v1/notifications/tokens/register`)
- FCM device token deletion endpoint (`DELETE /api/v1/notifications/tokens/{deviceId}`)
- FCM send to single device token (`POST /api/v1/notifications/send`)
- FCM send to topic (`POST /api/v1/notifications/send-topic`)
- FCM topic subscribe / unsubscribe endpoints
- FCM notification + data payload support in send request
- Stale token deletion on `UNREGISTERED` / `INVALID_ARGUMENT` FCM error response
- Flyway V3 migration for `device_tokens` table (UUID v7 PK, `user_id` FK, platform enum, deviceId, token, updatedAt)
- SMTP plain text + HTML email (`JavaMailSender` + `MimeMessageHelper`)
- SMTP attachments (`MimeMessageHelper.addAttachment`)
- Async email send (`@Async` on `MailService` methods)
- SMTP retry on transient failure (`@Retryable`)
- IMAP inbox listing — unread messages (subject, sender, date, snippet)
- IMAP message fetch — full body (plain text extracted from multipart)
- Env-var configuration for Firebase service account + SMTP/IMAP credentials

**Add after v6.0 validation (P2):**
- Multi-device token fan-out (send to all user devices)
- FCM batch/multicast send (`sendEachForMulticast`, up to 500 tokens per call)
- Platform-specific FCM overrides (`AndroidConfig`, `ApnsConfig`)
- Email templates (Thymeleaf or inline HTML)
- FCM dry-run validation mode

**Defer to v7+ (P3):**
- IMAP IDLE real-time receiving (polling is sufficient for template MVP)
- Notification history stored in DB (consuming app concern, not template concern)
- Token freshness enforcement (monthly re-registration prompt — app-level concern)

**Anti-features (never build):**
- In-process persistent email queue — reinvents a message broker; `@Retryable` is sufficient at template stage
- Notification preferences management — consuming app concern
- Storing raw email body content in DB — GDPR/BLOB complexity, not a template concern
- Sending FCM token in the `users` table — always use a separate `device_tokens` table

### Architecture Approach

Two new domain packages (`notification/`, `mail/`) are added alongside existing `user/` and `authentication/` packages. Infrastructure singleton beans (`FirebaseConfig`, `NotificationConfig`) live in `config/`. The existing `SecurityConfig` and `SmsSchedulerConfig` require no changes. `SmtpMailService` implements both `EmailService` (existing auth codes) and `MailService` (new general email) so no existing callers change. IMAP uses raw Jakarta Mail `Store` opened and closed per-request (never held as a Spring bean). `ConsolePushService` is registered via `@ConditionalOnMissingBean` in `NotificationConfig`, exactly mirroring the existing `SmsService`/`EmailService` fallback pattern.

**Major components:**
1. `FirebaseConfig` — `FirebaseApp` singleton init + `FirebaseMessaging` bean; guarded by `@ConditionalOnProperty(app.firebase.enabled)`; init guard `FirebaseApp.getApps().isEmpty()` prevents double-init crash
2. `NotificationConfig` — `ConsolePushService` fallback via `@ConditionalOnMissingBean(PushService::class)`; mirrors existing `SmsSchedulerConfig` pattern
3. `notification/service/FcmPushService` — Firebase Admin SDK calls; `sendAsync()` bridged to `CompletableFuture` for non-blocking delivery; inspects response for `UNREGISTERED` / `INVALID_ARGUMENT` and deletes stale tokens
4. `notification/service/DeviceTokenService` — token upsert (unique on `user_id + device_id`); topic subscribe/unsubscribe with topic list stored in DB for cleanup on logout
5. `mail/service/SmtpMailService` — implements both `EmailService` and `MailService`; `@Async` on send methods; `@Retryable` for transient failures; activated transparently when `spring.mail.host` is set
6. `mail/service/ImapMailService` — raw Jakarta Mail `Store`; open/connect/read/close per invocation inside `try/finally`; never held as a bean

**Suggested build order (each step independently testable):**
1. `FirebaseConfig` + `PushService` interface + `ConsolePushService` + `NotificationConfig`
2. `DeviceToken` entity + Flyway V3 migration
3. `FcmPushService` implementation (single token, topic send)
4. `DeviceTokenService` + `DeviceTokenController`
5. `NotificationController` (send + topic endpoints)
6. `spring-boot-starter-mail` dependency + `SmtpMailService` (replaces `ConsoleEmailService` transparently)
7. `MailController` (send endpoint — plain text, HTML, attachments)
8. `ImapMailService` + `MailController` receive endpoints (inbox listing, message fetch)

### Critical Pitfalls

1. **Firebase double-initialization crash** — Call `FirebaseApp.initializeApp()` only after checking `FirebaseApp.getApps().isEmpty()`; mock `FirebaseMessaging` bean in tests via `@TestConfiguration @Primary`; set `app.firebase.enabled=false` in test profile so `FirebaseConfig` never loads.

2. **Firebase service account JSON in JAR or git** — Never place in `src/main/resources/`; add `**/firebase-service-account*.json` to `.gitignore` immediately; inject via `FIREBASE_CREDENTIALS_JSON` env var (base64-encoded) or volume-mounted file path; validate with `jar tf target/*.jar | grep service-account` after every `mvn package`.

3. **Stale FCM tokens accumulating silently** — Inspect every FCM send response for `UNREGISTERED` and `INVALID_ARGUMENT` error codes and delete matching token rows from DB immediately; for `sendEachForMulticast`, iterate `BatchResponse.responses` by index to correlate failures with tokens.

4. **Single FCM token per user (column on `users` table)** — One user logs in on phone + tablet; a single column overwrites the other device's token silently. Always model `device_tokens` as a separate table with FK to `users` and composite unique constraint on `(user_id, device_id)`.

5. **Blocking FCM and SMTP sends on request threads** — `JavaMailSender.send()` uses synchronized blocks internally, which pin Virtual Threads to carrier threads. `FirebaseMessaging.send()` is a synchronous HTTP call holding the thread for 100–500ms. Use `sendAsync()` (FCM) and `@Async` (SMTP) from day one.

6. **IMAP Store connection leak** — `Store` and `Folder` represent open IMAP connections. Gmail limits to ~15 concurrent connections per account. Always close both in `finally` blocks using `runCatching { store.close() }`. Never hold `Store` as a Spring bean singleton.

7. **Scheduler thread starvation from shared single-threaded `TaskScheduler`** — `@EnableScheduling` already exists in `SmsSchedulerConfig`. Configure a `ThreadPoolTaskScheduler` bean with `poolSize >= number of scheduled jobs` before adding any IMAP polling `@Scheduled` method. Do not add a second `@EnableScheduling` annotation.

8. **Flyway migration version conflicts** — V1–V5 migrations already exist. New `device_tokens` table uses V3 (next in sequence after existing V2). Never skip version numbers. Ensure test profile keeps `spring.flyway.enabled=false` to avoid H2-incompatible PostgreSQL DDL in migration files.

---

## Implications for Roadmap

The feature dependency tree and the 8-step build order from architecture research map cleanly to two phases. FCM infrastructure must come first because it establishes the `@ConditionalOnProperty` guard, test-mock strategy, and Flyway V3 migration that the email phase inherits. Email is architecturally independent but benefits from the test infrastructure already in place after Phase 1.

### Phase 1: FCM Push Notifications

**Rationale:** Device token storage is the root of the entire FCM feature tree — nothing else in the push domain can be built without it. Firebase config setup here also establishes the `@ConditionalOnProperty` / `@ConditionalOnMissingBean` / test-mock pattern that the email phase reuses. This phase also cuts across all FCM-related pitfalls, which are more numerous and more severe than the email pitfalls.
**Delivers:** FCM send to individual device tokens and topics; device token register/unregister/upsert endpoints; FCM topic subscribe/unsubscribe; notification + data payload support; stale token cleanup on send errors; Flyway V3 `device_tokens` migration; `ConsolePushService` for dev/test.
**Addresses:** All P1 FCM features from FEATURES.md (token registration, send to token, send to topic, subscribe/unsubscribe, notification + data payload, stale cleanup, env-var configuration).
**Avoids:** Pitfalls 1 (double-init), 2 (credentials in JAR/git), 3 (stale tokens), 4 (single-token-per-user), 5 (blocking send), 8 (Flyway versioning), 14 (H2 tests initializing real Firebase).

### Phase 2: Email Service — SMTP Send + IMAP Receive

**Rationale:** Email is architecturally independent of FCM. `SmtpMailService` builds on `spring-boot-starter-mail` and extends the existing `EmailService` interface without any changes to existing callers. IMAP receive introduces its own pitfalls (connection leaks, scheduler conflicts) that benefit from being implemented after the FCM pattern and test infrastructure are established in Phase 1. The `@Async` and `@Retryable` patterns also established in Phase 1 are reused directly here.
**Delivers:** SMTP plain text + HTML + attachment send; async send with `@Async`; retry with `@Retryable`; IMAP inbox listing and message fetch; `MailController` endpoints; `GreenMail` integration test coverage; Mailpit local dev setup.
**Addresses:** All P1 email features from FEATURES.md (SMTP plain text, HTML, attachments, async send, retry, IMAP inbox listing, IMAP message fetch, env-var config).
**Avoids:** Pitfalls 5 (blocking SMTP on request threads), 6 (IMAP connection leak), 7 (SMTP without TLS), 10 (scheduler starvation — configure `ThreadPoolTaskScheduler` here), 12 (SecurityConfig missing webhook permits).

### Phase Ordering Rationale

- FCM before Email because the `@ConditionalOnProperty` pattern, test mock strategy (`@TestConfiguration @Primary`), and Flyway migration sequencing are all established in Phase 1 and directly reused in Phase 2 without duplication.
- Both phases are scoped to P1 features only; P2 (multi-device fan-out, batch multicast, platform overrides, email templates) are explicitly deferred to post-validation milestones.
- The 8-step build order from ARCHITECTURE.md maps directly: steps 1–5 form Phase 1; steps 6–8 form Phase 2.
- `SecurityConfig` is intentionally untouched: the existing `authorize("/api/**", authenticated)` catch-all already covers all new notification and email endpoints.

### Research Flags

Phases with standard patterns (skip `/gsd:research-phase`):
- **Phase 1 (FCM):** Firebase Admin SDK + Spring Boot integration is documented in official Firebase and Spring Security docs; the `@ConditionalOnProperty` pattern mirrors existing project conventions exactly; HIGH confidence.
- **Phase 2 (Email):** `spring-boot-starter-mail` is first-party Boot; Jakarta Mail IMAP patterns are documented in official Jakarta Mail and Spring Integration docs; GreenMail testing is standard; HIGH confidence.

No phase requires a dedicated pre-implementation research step. The gaps below are implementation-time validation checks, not research blockers.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | `firebase-admin` 9.8.0 confirmed on Maven Central (released 2026-02-25); `spring-boot-starter-mail` and `spring-integration-mail` verified via official Spring Boot docs; GreenMail 2.1.3 confirmed via official GreenMail docs |
| Features | HIGH (FCM) / MEDIUM-HIGH (IMAP) | FCM HTTP v1 API thoroughly documented on firebase.google.com; IMAP operational complexity at scale (reconnect, IDLE, high-frequency polling) has nuances not fully documented for this specific stack combination |
| Architecture | HIGH | Firebase Admin SDK singleton pattern, Spring Mail SMTP pattern, and Jakarta Mail direct Store pattern verified against official docs; domain structure follows established project conventions |
| Pitfalls | HIGH | 14 pitfalls sourced from official Firebase docs, Spring Boot docs, JDK Virtual Threads spec (JEP 444), and official Jakarta Mail docs; recovery strategies are concrete and verified |

**Overall confidence:** HIGH

### Gaps to Address

- **Google library version conflict (`firebase-admin` vs `google-api-client`):** Both transitively pull `google-http-client`. Run `mvn dependency:tree | grep google-http-client` after adding `firebase-admin` in Phase 1 to detect version skew; resolve with `com.google.cloud:libraries-bom` in `<dependencyManagement>` if needed.
- **iOS APNs end-to-end delivery:** FCM server-side send works for Android immediately. iOS delivery requires APNs configured in Firebase Console (`.p8` key upload) and end-to-end testing on a real device (simulators do not receive push). Flag as a consuming-project validation step, not a template validation step.
- **SMTP relay selection:** Template defaults to `localhost:1025` (Mailpit) in dev and env vars in production. Actual SMTP provider choice (Gmail, SendGrid, SES) is a consuming-project decision. Test coverage uses GreenMail only — document this boundary in `application-dev.yml` comments.
- **IMAP high-frequency polling:** Open-close-per-request is acceptable for scheduled polling at low frequency (< 1/min). If a consuming project requires sub-minute IMAP polling, document `ImapIdleChannelAdapter` (Spring Integration IMAP IDLE) as the extension point.

---

## Sources

### Primary (HIGH confidence)
- [Firebase Admin Java SDK Release Notes](https://firebase.google.com/support/release-notes/admin/java) — v9.8.0 confirmed (2026-02-25)
- [FCM: Send messages — Firebase official](https://firebase.google.com/docs/cloud-messaging/send-message) — HTTP v1 API, batch limit (500 tokens), error codes
- [FCM error codes reference](https://firebase.google.com/docs/reference/fcm/rest/v1/ErrorCode) — `UNREGISTERED`, `INVALID_ARGUMENT` handling
- [Firebase Admin SDK setup (initializeApp guard)](https://firebase.google.com/docs/admin/setup#initialize-sdk) — double-init prevention pattern explicitly documented
- [FCM: Best practices for token management](https://firebase.google.com/docs/cloud-messaging/manage-tokens) — stale token cleanup, monthly refresh recommendation
- [Spring Boot Email Reference](https://docs.spring.io/spring-boot/reference/io/email.html) — `spring-boot-starter-mail`, `JavaMailSender`, `spring.mail.*` auto-configuration
- [Spring Framework Email Reference](https://docs.spring.io/spring-framework/reference/integration/email.html) — SMTP patterns, `MimeMessageHelper`
- [Spring Integration Mail Reference](https://docs.spring.io/spring-integration/reference/mail.html) — `ImapMailReceiver`, `ImapIdleChannelAdapter`
- [Spring Boot Dependency Versions BOM](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html) — all Boot-managed library versions
- [Spring Boot `@Async` and scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) — async executor configuration
- [GreenMail official docs](https://greenmail-project.github.io/greenmail/) — embedded SMTP/IMAP for JUnit 5 tests
- [Jakarta Mail / Angus Mail IMAP](https://eclipse-ee4j.github.io/angus-mail/) — Store/Folder lifecycle, connection limits
- [FCM batch send limit (500)](https://firebase.google.com/docs/cloud-messaging/send-message#send-messages-to-multiple-devices) — `sendEachForMulticast` constraints
- [APNs configuration for FCM iOS](https://firebase.google.com/docs/cloud-messaging/ios/client) — iOS delivery requirements
- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444) — synchronized block pinning behavior with Jakarta Mail
- [Spring Security `authorizeHttpRequests`](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html) — SecurityConfig patterns for new endpoints
- [Flyway versioning best practices](https://documentation.red-gate.com/fd/migrations-184127470.html) — migration numbering and validation
- [Maven Central: firebase-admin](https://central.sonatype.com/artifact/com.google.firebase/firebase-admin) — 9.8.0 confirmed
- [FCM HTTP v1 Migration Guide](https://firebase.google.com/docs/cloud-messaging/migrate-v1) — legacy API shutdown June 20, 2024 confirmed

### Secondary (MEDIUM confidence)
- [Spring Boot + FCM HTTP v1 — Baeldung](https://www.baeldung.com/spring-fcm) — FCM Admin SDK usage patterns; verified against official Firebase docs
- [Guide to Spring Email — Baeldung](https://www.baeldung.com/spring-email) — verified against official Spring Boot docs
- [Firebase Admin Java SDK GitHub](https://github.com/firebase/firebase-admin-java) — Maven coordinates, Java 8+ requirement
- WebSearch: Spring Boot 4 + firebase-admin integration patterns (2025–2026)
- WebSearch: Spring Boot 4 Virtual Threads ITNEXT benchmark (Feb 2026)

### Tertiary (LOW confidence)
- [FCM HTTP v1 Spring Boot reference implementation — GitHub](https://github.com/mdtalalwasim/Firebase-Cloud-Messaging-FCM-v1-API-Spring-boot) — pattern reference only, community repo
- Community articles: IMAP IDLE reconnection complexity — operational complexity assessment for "never use IMAP IDLE as MVP" recommendation

---
*Research completed: 2026-03-03*
*Ready for roadmap: yes*
