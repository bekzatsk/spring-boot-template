# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-03)

**Core value:** Mobile/web clients can authenticate with Google, Apple, email+password, or phone+SMS OTP and receive JWT tokens. Account linking ensures one email = one user across all providers. Full account management: forgot-password, change-password, change-email, change-phone. Push notifications via FCM. Email service with SMTP send (async, retry) and IMAP receive. Notification preferences (opt-out model).
**Current focus:** v6.0 Notifications — Phase 2 complete, milestone ready for completion

## Current Position

Milestone: v6.0 Notifications
Phase: 2 of 2 complete (Email Service and Notification Preferences)
Current Plan: — (all plans complete)
Last activity: 2026-03-03 — Phase 2 complete (3/3 plans, 63 tests pass)

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**
- Total plans completed: 23
- Average duration: 4.1 min
- Total execution time: 1.5 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-foundation | 2/2 | 12 min | 6 min |
| 02-security-wiring | 2/3 | 15 min | 7.5 min |
| 03-google-auth-and-token-management | 2/2 | 8 min | 4 min |
| 04-apple-auth | 1/1 | 13 min | 13 min |
| 05-hardening | 1/1 | 2 min | 2 min |
| 06-restructure | 2/2 | 7 min | 3.5 min |
| 01-local-auth (v2) | 3/3 | 12 min | 4 min |
| 02-account-linking | 2/2 | 7 min | 3.5 min |
| 03-self-managed-sms | 2/2 | 15 min | 7.5 min |
| 04-uuid-v7 | 1/1 | 3 min | 3 min |
| 05-account-mgmt | 3/3 | 6 min | 2 min |
| 01-fcm-push (v6) | 3/3 | 9 min | 3 min |
| 02-email-and-prefs (v6) | 3/3 | 18 min | 6 min |

**Recent Trend:**
- Last 5 plans: 3 min, 3 min, 5 min, 5 min, 8 min
- Trend: stable (email/test plans slightly longer due to bean config debugging)

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Firebase Admin SDK singleton guarded by FirebaseApp.getApps().isEmpty() — prevents double-init crash; set app.firebase.enabled=false in test profile
- ConsolePushService registered via @ConditionalOnMissingBean(PushService) — mirrors existing SmsService/EmailService fallback pattern exactly
- SmtpMailService implements both EmailService (existing auth codes) and MailService (new general email) — no changes to existing VerificationCodeService callers
- IMAP Store opened and closed per-request in try/finally — never held as a Spring bean singleton; prevents connection-limit exhaustion
- FCM and SMTP sends must be async from day one — JavaMailSender.send() uses synchronized blocks that pin Virtual Threads; FCM send is a 100-500ms HTTP call
- @ConditionalOnProperty(app.mail.enabled=true) gates SMTP beans — user @Configuration processes before auto-config, so @ConditionalOnBean(JavaMailSender) is unreliable
- ConsoleEmailService (implements only EmailService) separate from ConsoleMailService (implements only MailService) — prevents NoUniqueBeanDefinitionException from dual-interface fallback

### Pending Todos

None.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 1 | Fix DataSource 'url' not specified error — add default spring profile | 2026-03-01 | cd51f12 | [1-fix-datasource-url-not-specified-error-a](./quick/1-fix-datasource-url-not-specified-error-a/) |
| 2 | Fix 500 on POST /api/v1/auth/refresh — JOIN FETCH User in findByTokenHash, add exception logging | 2026-03-01 | ce3560f | [2-fix-500-internal-server-error-on-api-v1-](./quick/2-fix-500-internal-server-error-on-api-v1-/) |
| 3 | Fix Flyway "Unsupported Database: PostgreSQL 18.2" — add flyway-database-postgresql module | 2026-03-02 | 0cc5bd8 | [3-fix-flyway-unsupported-postgresql-18-2-e](./quick/3-fix-flyway-unsupported-postgresql-18-2-e/) |
| 4 | Fix RsaKeyPair bean NPE on startup — invert isNullOrBlank() conditional in RsaKeyConfig | 2026-03-02 | aef19cc | [4-fix-rsakeypair-bean-creation-failure-in-](./quick/4-fix-rsakeypair-bean-creation-failure-in-/) |
| 5 | Add verificationId to phone OTP flow — /phone/request returns UUID, /phone/verify requires UUID | 2026-03-02 | 673106f | [5-add-smsverification-id-to-phone-otp-flow](./quick/5-add-smsverification-id-to-phone-otp-flow/) |
| 6 | Dev profile uses hardcoded SMS OTP code 123456 — @Value config-driven, SecureRandom preserved for prod/test | 2026-03-02 | 3a8d486 | [6-dev-profile-uses-hardcoded-sms-code-1234](./quick/6-dev-profile-uses-hardcoded-sms-code-1234/) |
| 7 | Consolidate Flyway migrations into single V1 — complete 6-table schema, no gen_random_uuid, dev clean-on-validation-error | 2026-03-02 | e89675a | [7-consolidate-flyway-migrations-into-singl](./quick/7-consolidate-flyway-migrations-into-singl/) |
| 8 | Fix _id column mapping in BaseEntity — add @Column(name = "id") to match Flyway schema | 2026-03-03 | ef8d39a | [8-fix-id-column-mapping-in-baseentity-add-](./quick/8-fix-id-column-mapping-in-baseentity-add-/) |

### Blockers/Concerns

- Developer note: If running Postgres.app locally on port 5432, create the `template` role or stop Postgres.app and use Docker only
- RESOLVED: google-http-client version conflict resolved with libraries-bom 26.55.0 (unified at 1.46.1)

## Session Continuity

Last session: 2026-03-03
Stopped at: v6.0 milestone complete — both phases (FCM Push, Email Service) done. Ready for milestone audit/completion.
Resume file: None
