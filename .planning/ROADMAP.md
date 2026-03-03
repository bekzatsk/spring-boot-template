# Roadmap: Spring Boot Auth Template

## Milestones

- ✅ **v1.0 MVP** — Phases 1-6 (shipped 2026-03-01)
- ✅ **v2.0 Local Auth** — Phase 1 (shipped 2026-03-02)
- ✅ **v3.0 Account Linking + Self-managed SMS** — Phases 2-3 (shipped 2026-03-02)
- ✅ **v4.0 UUID v7** — Phase 4 (shipped 2026-03-02)
- ✅ **v5.0 Account Management** — Phase 5 (shipped 2026-03-03)
- 🚧 **v6.0 Notifications** — Phases 1-2 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-6) — SHIPPED 2026-03-01</summary>

- [x] Phase 1: Foundation (2/2 plans) — completed 2026-03-01
- [x] Phase 2: Security Wiring (2/2 plans) — completed 2026-03-01
- [x] Phase 3: Google Auth and Token Management (2/2 plans) — completed 2026-03-01
- [x] Phase 4: Apple Auth (1/1 plan) — completed 2026-03-01
- [x] Phase 5: Hardening (1/1 plan) — completed 2026-03-01
- [x] Phase 6: Restructure (2/2 plans) — completed 2026-03-01

</details>

<details>
<summary>✅ v2.0–v5.0 Post-MVP (Phases 1-5) — SHIPPED 2026-03-03</summary>

### Phase 1: Add LOCAL authentication — email+password and phone+SMS code login

**Goal:** Users can register and login with email+password or phone+SMS OTP, with Flyway-managed schema migrations, extending the existing JWT token infrastructure
**Depends on:** v1.0 MVP (Phases 1-6)
**Plans:** 3 plans

Plans:
- [x] 01-01-PLAN.md — Foundation: Maven dependencies (Flyway, Twilio, libphonenumber), AuthProvider.LOCAL, User entity columns, Flyway migrations — completed 2026-03-02
- [x] 01-02-PLAN.md — Email+password auth: LocalUserDetailsService, LocalAuthService, DaoAuthenticationProvider, register/login endpoints, integration tests — completed 2026-03-02
- [x] 01-03-PLAN.md — Phone+SMS OTP auth: TwilioConfig, PhoneOtpService, E.164 normalization, request-otp/verify-otp endpoints, integration tests — completed 2026-03-02

### Phase 2: Implement account linking logic — email is globally unique across all providers, one user = one email = one account

**Goal:** Migrate from single-provider (provider, providerId) model to multi-provider model (providers Set + providerIds Map via @ElementCollection), with email as the universal identity key and account linking across LOCAL, GOOGLE, and APPLE providers
**Depends on:** Phase 1
**Requirements:** [LINK-01, LINK-02, LINK-03, LINK-04, LINK-05, LINK-06]
**Plans:** 2 plans

Plans:
- [x] 02-01-PLAN.md — Entity + Migration: Rewrite User entity to multi-provider model, update UserRepository to findByEmail, create V2 Flyway migration — completed 2026-03-02
- [x] 02-02-PLAN.md — Service layer + Tests: Rewrite UserService/LocalAuthService/LocalUserDetailsService for account linking, update all test files — completed 2026-03-02

### Phase 3: Replace Twilio Verify with self-managed SMS code generation and verification

**Goal:** Replace Twilio Verify with fully self-managed SMS OTP: SecureRandom 6-digit codes, BCrypt-hashed storage in sms_verifications table, rate limiting (1/phone/60s), max 3 attempts, 5-min expiry, scheduled cleanup, SmsService interface with ConsoleSmsService default, endpoint/DTO renames, and comprehensive integration tests
**Depends on:** Phase 2
**Requirements:** [SMS-01, SMS-02, SMS-03, SMS-04, SMS-05, SMS-06, SMS-07, SMS-08, SMS-09]
**Plans:** 2 plans

Plans:
- [x] 03-01-PLAN.md — SMS verification infrastructure + Twilio removal + endpoint/DTO renames + test rewrite — completed 2026-03-02
- [x] 03-02-PLAN.md — Rewrite PhoneAuthIntegrationTest with doAnswer code capture, 7 tests, rate limiting test — completed 2026-03-02

### Phase 4: Replace all UUID generation with UUID v7 — time-ordered IDs for chronological sorting and cursor-based pagination

**Goal:** Replace @GeneratedValue(UUID v4) with application-assigned UUID v7 via uuid-creator 6.1.1 and a shared BaseEntity @MappedSuperclass implementing Persistable<UUID>, across all 3 JPA entities
**Depends on:** Phase 3
**Requirements:** [UUID7-01, UUID7-02, UUID7-03]
**Plans:** 1 plan

Plans:
- [x] 04-01-PLAN.md — Add uuid-creator dependency, create BaseEntity @MappedSuperclass, migrate all entities to UUID v7, remove id!! force-unwraps — completed 2026-03-02

### Phase 5: Add account management — forgot password, change password, change email, change phone with self-managed verification codes

**Goal:** Users can recover lost passwords via email verification code, change their password (with current password verification), change their email (with code sent to new email), and change their phone (with SMS code) — all using a shared VerificationCode infrastructure with rate limiting, max attempts, and BCrypt-hashed codes
**Depends on:** Phase 4
**Requirements:** [ACCT-01, ACCT-02, ACCT-03, ACCT-04, ACCT-05]
**Plans:** 3 plans

Plans:
- [x] 05-01-PLAN.md — Infrastructure: VerificationCode entity/repo/migration, EmailService/ConsoleEmailService, VerificationCodeService, config updates — completed 2026-03-03
- [x] 05-02-PLAN.md — Service + Controller: AccountManagementService (4 flows), AccountManagementController, DTOs, AuthController forgot-password endpoints — completed 2026-03-03
- [x] 05-03-PLAN.md — Integration tests: AccountManagementIntegrationTest with 14 tests covering all flows, edge cases, and security properties — completed 2026-03-03

</details>

---

### 🚧 v6.0 Notifications (In Progress)

**Milestone Goal:** Firebase Cloud Messaging push notifications (device tokens, topics, batch send) and a full email service (SMTP send with attachments and retry, IMAP/POP3 receive) with notification history and preference management.

## Phase Details

### Phase 1: FCM Push Notifications
**Goal:** Users can register device tokens, send push notifications to individual devices and topics, and the system automatically cleans up stale tokens — with Firebase initialized safely and a console fallback for dev
**Depends on:** v5.0 (Phase 5 complete)
**Requirements:** FCM-01, FCM-02, FCM-03, FCM-04, FCM-05, FCM-06, FCM-07, FCM-08, FCM-09, NMGT-01, NMGT-02, NFRA-01, NFRA-02, NFRA-04
**Success Criteria** (what must be TRUE):
  1. Authenticated user can register a device token (Android/iOS/Web) via POST /api/v1/notifications/tokens and delete it via DELETE /api/v1/notifications/tokens/{deviceId}
  2. Authenticated user can send a push notification to a single token, up to 500 tokens (multicast), or a topic via the notifications API — all with title, body, and custom data payload
  3. Authenticated user can subscribe or unsubscribe a device token to/from a named topic
  4. When FCM returns UNREGISTERED or INVALID_ARGUMENT, the stale token is automatically deleted from the database without manual intervention
  5. Sent notifications appear in the notification history table and are retrievable via GET /api/v1/notifications/history; dev profile with no Firebase credentials logs to console instead of failing
**Plans:** 3 plans

Plans:
- [x] 01-01-PLAN.md — Foundation: firebase-admin dependency, FirebaseConfig with double-init guard, V3 Flyway migration (device_tokens, notification_history, notification_topics), JPA entities, repositories — completed 2026-03-03
- [x] 01-02-PLAN.md — Services: PushService interface, FirebasePushService, ConsolePushService fallback, DeviceTokenService, NotificationService + async dispatcher, TopicService — completed 2026-03-03
- [x] 01-03-PLAN.md — Controllers + Tests: DTOs, NotificationController (10 endpoints), TopicAdminController, SecurityConfig admin rule, 12 integration tests — completed 2026-03-03

### Phase 2: Email Service and Notification Preferences
**Goal:** Users can send emails (plain text, HTML, attachments) via SMTP with async delivery and retry, read their inbox and individual emails via IMAP, and configure which notification types they receive — backed by GreenMail integration tests
**Depends on:** Phase 1
**Requirements:** MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06, MAIL-07, MAIL-08, MAIL-09, NMGT-03, NMGT-04, NFRA-03, NFRA-05
**Success Criteria** (what must be TRUE):
  1. Authenticated user can send an email with plain text or HTML body and optional file attachments via the mail API — send is non-blocking and returns immediately
  2. Failed email sends are automatically retried up to the configured max attempts with configurable delay; dev profile with no SMTP configured logs to console instead of failing
  3. Authenticated user can list their inbox (unread filter, pagination) and fetch a full individual email with body and attachment metadata via IMAP
  4. Authenticated user can mark an email as read or unread via the IMAP API
  5. Authenticated user can set notification preferences (push, email, or both) and subsequent notifications respect those preferences before dispatching
**Plans:** 3 plans

Plans:
- [x] 02-01-PLAN.md — Foundation: spring-boot-starter-mail, greenmail, MailProperties, V4 Flyway migration (mail_history, notification_preferences), JPA entities, repositories, MailService interface — completed 2026-03-03
- [x] 02-02-PLAN.md — Services: SmtpMailService (dual interface), MailDispatcher (@Async with retry), ConsoleMailService fallback, ImapService, NotificationPreferenceService, preference integration into NotificationService — completed 2026-03-03
- [x] 02-03-PLAN.md — Controllers + Tests: MailController (6 endpoints), preference endpoints on NotificationController, DTOs, GreenMail integration tests, preference integration tests — completed 2026-03-03

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation | v1.0 | 2/2 | Complete | 2026-03-01 |
| 2. Security Wiring | v1.0 | 2/2 | Complete | 2026-03-01 |
| 3. Google Auth and Token Management | v1.0 | 2/2 | Complete | 2026-03-01 |
| 4. Apple Auth | v1.0 | 1/1 | Complete | 2026-03-01 |
| 5. Hardening | v1.0 | 1/1 | Complete | 2026-03-01 |
| 6. Restructure | v1.0 | 2/2 | Complete | 2026-03-01 |
| 1. Local Auth | v2.0 | 3/3 | Complete | 2026-03-02 |
| 2. Account Linking | v3.0 | 2/2 | Complete | 2026-03-02 |
| 3. Self-managed SMS | v3.0 | 2/2 | Complete | 2026-03-02 |
| 4. UUID v7 | v4.0 | 1/1 | Complete | 2026-03-02 |
| 5. Account Management | v5.0 | 3/3 | Complete | 2026-03-03 |
| 1. FCM Push Notifications | v6.0 | 3/3 | Complete | 2026-03-03 |
| 2. Email Service and Notification Preferences | v6.0 | 3/3 | Complete | 2026-03-03 |
