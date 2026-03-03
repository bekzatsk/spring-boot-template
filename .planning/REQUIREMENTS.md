# Requirements: Spring Boot Auth Template

**Defined:** 2026-03-03
**Core Value:** Mobile/web clients can authenticate with Google, Apple, email+password, or phone+SMS OTP and receive JWT tokens. Push notifications via Firebase Cloud Messaging. Email service with SMTP sending and IMAP/POP3 receiving.

## v6.0 Requirements

Requirements for v6.0 Notifications milestone. Each maps to roadmap phases.

### Push Notifications (FCM)

- [ ] **FCM-01**: Authenticated user can register a device token with platform type (ANDROID/IOS/WEB)
- [ ] **FCM-02**: User can delete a device token (e.g., on logout)
- [ ] **FCM-03**: User can send a push notification to a single device token with title, body, and custom data payload
- [ ] **FCM-04**: User can send a push notification to multiple device tokens (batch/multicast, max 500 per request)
- [ ] **FCM-05**: User can send a push notification to a topic
- [ ] **FCM-06**: User can subscribe/unsubscribe a device token to/from a topic
- [ ] **FCM-07**: Stale device tokens are automatically removed when FCM returns UNREGISTERED/INVALID_ARGUMENT errors
- [ ] **FCM-08**: Firebase Admin SDK is initialized with service account credentials from environment variable, guarded against double-init
- [ ] **FCM-09**: Dev profile logs notifications to console when Firebase credentials are not configured

### Email Service

- [ ] **MAIL-01**: User can send email via SMTP with plain text and HTML body support
- [ ] **MAIL-02**: User can send email with file attachments
- [ ] **MAIL-03**: Email sending is async to avoid blocking Virtual Threads
- [ ] **MAIL-04**: Failed email sends are retried with configurable max attempts and delay
- [ ] **MAIL-05**: User can list inbox emails via IMAP/POP3 with unread filter and pagination
- [ ] **MAIL-06**: User can fetch a single email with full body and attachment metadata
- [ ] **MAIL-07**: User can mark an email as read/unread via IMAP
- [ ] **MAIL-08**: SMTP and IMAP/POP3 settings are configurable via environment variables (host, port, username, password, SSL/TLS)
- [ ] **MAIL-09**: Dev profile logs emails to console when SMTP is not configured

### Notification Management

- [ ] **NMGT-01**: Sent notifications are persisted in a notification history table (type, recipient, title, body, status, timestamp)
- [ ] **NMGT-02**: User can view their notification history via API endpoint
- [ ] **NMGT-03**: User can configure notification preferences (which types to receive: push, email, or both)
- [ ] **NMGT-04**: Notification preferences are checked before dispatching push/email notifications

### Infrastructure

- [ ] **NFRA-01**: Flyway migration creates device_tokens table (UUID v7 PK, user_id FK, platform enum, token, timestamps)
- [ ] **NFRA-02**: Flyway migration creates notification_history table
- [ ] **NFRA-03**: Flyway migration creates notification_preferences table
- [ ] **NFRA-04**: New notification/ domain package follows existing project structure conventions (model/repository/service/controller/dto)
- [ ] **NFRA-05**: Integration tests use GreenMail (in-process SMTP/IMAP) and mocked Firebase

## v7.0 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Push Notifications

- **FCM-10**: Platform-specific notification overrides (Android channel ID, iOS APNs priority/sound)
- **FCM-11**: Scheduled/delayed push notifications
- **FCM-12**: Notification analytics (delivery rate, open rate)

### Email

- **MAIL-10**: HTML email templates via Thymeleaf
- **MAIL-11**: IMAP IDLE real-time push (persistent connection)
- **MAIL-12**: Email attachment download endpoint

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Frontend notification UI | Backend API only — clients handle display |
| SMS notifications via this service | Existing SmsService handles SMS separately |
| In-app notification center (WebSocket) | Over-scoped for a template; add when needed |
| Email template designer/editor | Consuming project concern, not template |
| Push notification scheduling/queue | Simple send-on-request for template; consuming project adds queue |
| FCM legacy HTTP API | Shut down June 2024 — only HTTP v1 API via firebase-admin SDK |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| FCM-01 | Phase 1 | Pending |
| FCM-02 | Phase 1 | Pending |
| FCM-03 | Phase 1 | Pending |
| FCM-04 | Phase 1 | Pending |
| FCM-05 | Phase 1 | Pending |
| FCM-06 | Phase 1 | Pending |
| FCM-07 | Phase 1 | Pending |
| FCM-08 | Phase 1 | Pending |
| FCM-09 | Phase 1 | Pending |
| MAIL-01 | Phase 2 | Pending |
| MAIL-02 | Phase 2 | Pending |
| MAIL-03 | Phase 2 | Pending |
| MAIL-04 | Phase 2 | Pending |
| MAIL-05 | Phase 2 | Pending |
| MAIL-06 | Phase 2 | Pending |
| MAIL-07 | Phase 2 | Pending |
| MAIL-08 | Phase 2 | Pending |
| MAIL-09 | Phase 2 | Pending |
| NMGT-01 | Phase 1 | Pending |
| NMGT-02 | Phase 1 | Pending |
| NMGT-03 | Phase 2 | Pending |
| NMGT-04 | Phase 2 | Pending |
| NFRA-01 | Phase 1 | Pending |
| NFRA-02 | Phase 1 | Pending |
| NFRA-03 | Phase 2 | Pending |
| NFRA-04 | Phase 1 | Pending |
| NFRA-05 | Phase 2 | Pending |

**Coverage:**
- v6.0 requirements: 27 total
- Mapped to phases: 27
- Unmapped: 0

---
*Requirements defined: 2026-03-03*
*Last updated: 2026-03-03 after roadmap creation (v6.0 Notifications — all 27 requirements mapped)*
