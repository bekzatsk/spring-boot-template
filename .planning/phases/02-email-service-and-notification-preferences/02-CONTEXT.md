# Phase 2: Email Service and Notification Preferences - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Build an email service with SMTP sending (plain text, HTML, attachments) and IMAP inbox reading. Add notification preferences so users can control which channels (push, email) they receive notifications on. Notification preferences are checked before dispatching any notification.

This phase does NOT include: email templates (Thymeleaf), IMAP IDLE real-time push, attachment download endpoints, or scheduled email sending.

</domain>

<decisions>
## Implementation Decisions

### Email Sending API
- MailService interface with send method accepting to, subject, body (text or HTML), and optional attachments
- SmtpMailService implements both the existing EmailService (for auth verification codes) and the new MailService (for general email) — no changes to existing VerificationCodeService callers
- Attachments passed as list of (filename, contentType, bytes) — controller accepts multipart/form-data for file uploads
- Send is always async (JavaMailSender.send() pins Virtual Threads via synchronized blocks) — use same @Async dispatcher pattern from Phase 1
- Return 202 Accepted with a tracking ID; email history stored with PENDING/SENT/FAILED status (similar to notification history)
- Retry on failure: configurable max attempts (default 3) with configurable delay (default 5s) — exponential backoff not needed for a template
- ConsoleMailService fallback when SMTP is not configured (same @ConditionalOnMissingBean pattern as ConsolePushService/ConsoleSmsService)

### IMAP Inbox Access
- IMAP Store opened and closed per-request in try/finally — never held as a Spring bean singleton (prevents connection-limit exhaustion, documented decision in STATE.md)
- Per-user IMAP credentials are NOT managed by this template — the API uses a single configured IMAP account (the template's own mailbox)
- List inbox: return message list with subject, from, date, read/unread flag, has-attachments flag
- Fetch single email: return full body (text + HTML) and attachment metadata (filename, contentType, size) — not the attachment bytes
- Mark read/unread: set/clear the SEEN flag on the IMAP message
- Pagination: offset-based (IMAP message numbers), not cursor-based (IMAP doesn't support UUID-based cursors)

### Notification Preferences
- New notification_preferences table: user_id (FK), channel (PUSH/EMAIL), enabled (boolean), with unique constraint on (user_id, channel)
- Default: both channels enabled when no preference row exists (opt-out model, not opt-in)
- GET /api/v1/notifications/preferences — returns current preference state for authenticated user
- PUT /api/v1/notifications/preferences — update preferences (partial update: only channels included in request are changed)
- NotificationService and future MailService check preferences before dispatching — if channel disabled, skip dispatch silently (no error)
- Granularity is per-channel only (PUSH, EMAIL) — per-type or per-topic granularity deferred to v7.0

### Configuration
- SMTP settings via environment variables: SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_FROM, SMTP_SSL_ENABLED
- IMAP settings via environment variables: IMAP_HOST, IMAP_PORT, IMAP_USERNAME, IMAP_PASSWORD, IMAP_SSL_ENABLED
- Dev profile: all mail settings optional; ConsoleMailService active when SMTP not configured
- application.yaml structure under `app.mail.smtp.*` and `app.mail.imap.*`

### Claude's Discretion
- Exact retry implementation (Spring @Retryable vs manual retry loop)
- IMAP folder selection strategy (INBOX only vs configurable)
- Email history table structure (can reuse notification_history pattern or create separate mail_history)
- GreenMail test setup specifics (in-process vs testcontainer)
- HTML email content sanitization (if any)

</decisions>

<specifics>
## Specific Ideas

- SmtpMailService must implement BOTH EmailService (existing interface for auth codes) and MailService (new interface for general email) — this is a documented decision in STATE.md to avoid breaking existing VerificationCodeService callers
- Follow the exact same async dispatch pattern from Phase 1: save PENDING, dispatch via separate @Async bean, update to SENT/FAILED
- Follow the exact same @ConditionalOnMissingBean/@ConditionalOnProperty pattern for service fallbacks
- GreenMail is specified in requirements (NFRA-05) for integration tests — use in-process SMTP/IMAP server
- IMAP connections are explicitly per-request, never pooled as Spring beans (STATE.md decision about connection-limit exhaustion)

</specifics>

<deferred>
## Deferred Ideas

- HTML email templates via Thymeleaf — v7.0 (MAIL-10)
- IMAP IDLE real-time push (persistent connection) — v7.0 (MAIL-11)
- Email attachment download endpoint — v7.0 (MAIL-12)
- Per-notification-type preference granularity — v7.0
- Per-topic preference granularity — v7.0

</deferred>

---

*Phase: 02-email-service-and-notification-preferences*
*Context gathered: 2026-03-03*
