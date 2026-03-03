---
phase: 02-email-service-and-notification-preferences
plan: 02
subsystem: services
tags: [smtp, imap, mail-service, async, retry, preferences, notification]

requires:
  - phase: 02-email-service-and-notification-preferences
    plan: 01
    provides: MailProperties, MailHistory entity, MailHistoryRepository, NotificationPreferenceRepository, MailService interface
provides:
  - SmtpMailService implementing both MailService and EmailService for SMTP sending
  - MailDispatcher with @Async @Transactional dispatch and configurable retry loop
  - ConsoleMailService fallback implementing both MailService and EmailService
  - MailConfig with @ConditionalOnBean(JavaMailSender) for conditional SMTP wiring
  - ImapService with per-request IMAP connections for inbox listing, fetch, and mark read/unread
  - NotificationPreferenceService with opt-out model (default enabled)
  - NotificationService preference checks before push dispatch
  - IMAP data classes (InboxMessage, InboxPage, EmailMessage, AttachmentMeta)
affects: [02-03-controllers]

tech-stack:
  added: []
  patterns: [conditional-on-bean, async-dispatch, retry-loop, per-request-connections, opt-out-preferences]

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/notification/service/SmtpMailService.kt
    - src/main/kotlin/kz/innlab/template/notification/service/MailDispatcher.kt
    - src/main/kotlin/kz/innlab/template/notification/service/ConsoleMailService.kt
    - src/main/kotlin/kz/innlab/template/notification/service/ImapService.kt
    - src/main/kotlin/kz/innlab/template/notification/service/NotificationPreferenceService.kt
    - src/main/kotlin/kz/innlab/template/config/MailConfig.kt
  modified:
    - src/main/kotlin/kz/innlab/template/config/SmsSchedulerConfig.kt
    - src/main/kotlin/kz/innlab/template/notification/service/NotificationService.kt
    - src/main/kotlin/kz/innlab/template/notification/controller/NotificationController.kt
  deleted:
    - src/main/kotlin/kz/innlab/template/authentication/service/ConsoleEmailService.kt

key-decisions:
  - "MailDispatcher is a plain class (no @Service) registered via @Bean @ConditionalOnBean(JavaMailSender) — avoids context failure when SMTP not configured"
  - "SmtpMailService is a plain class registered conditionally via MailConfig @Bean @ConditionalOnBean(MailDispatcher)"
  - "ConsoleMailService implements both MailService and EmailService — single fallback replaces old ConsoleEmailService"
  - "ImapService uses per-request Store connections opened/closed in try/finally — never pooled per CONTEXT.md decision"
  - "Notification preferences use opt-out model — isChannelEnabled returns true when no preference row exists"
  - "NotificationService.sendToToken/sendMulticast/sendToTopic return UUID? (null when PUSH disabled)"

patterns-established:
  - "@ConditionalOnBean(JavaMailSender) for gating SMTP-dependent beans on mail auto-configuration"
  - "Per-request IMAP Store lifecycle with withInbox helper method"
  - "Opt-out preference model with default-enabled semantics"

requirements-completed: [MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06, MAIL-07, MAIL-09, NMGT-03, NMGT-04]

duration: 5min
completed: 2026-03-03
---

# Plan 02-02: Services Summary

**SmtpMailService, MailDispatcher, ConsoleMailService, ImapService, NotificationPreferenceService, and preference integration into NotificationService**

## Performance

- **Duration:** 5 min
- **Tasks:** 2
- **Files created:** 6
- **Files modified:** 3
- **Files deleted:** 1

## Accomplishments
- Created SmtpMailService implementing both MailService and EmailService for SMTP send with HTML, attachments, and verification codes
- Created MailDispatcher with @Async @Transactional dispatch, configurable retry (max attempts + delay), and sendDirect for synchronous code delivery
- Created ConsoleMailService fallback implementing both interfaces — replaces old ConsoleEmailService
- Created MailConfig with @ConditionalOnBean(JavaMailSender) to conditionally wire SMTP beans only when spring.mail.host is set
- Created ImapService with per-request IMAP Store connections for listInbox, getMessage, markRead, markUnread
- Created NotificationPreferenceService with opt-out model (isChannelEnabled defaults true)
- Modified NotificationService to check PUSH preference before dispatching (sendToToken/sendMulticast/sendToTopic return UUID?)
- Updated NotificationController to handle nullable notification IDs with skipped response
- Deleted old ConsoleEmailService; removed emailService() bean from SmsSchedulerConfig

## Task Commits

1. **Task 1: SmtpMailService, MailDispatcher, ConsoleMailService, MailConfig** - `7d9b8a2` (feat)
2. **Task 2: ImapService, NotificationPreferenceService, preference integration** - `4139892` (feat)

## Decisions Made
- MailDispatcher and SmtpMailService are plain classes (no @Service) to avoid context failure when SMTP not configured
- @ConditionalOnBean(JavaMailSender) chains: MailDispatcher depends on JavaMailSender, SmtpMailService depends on MailDispatcher
- ImapService is always a @Service (safe — IMAP operations check config at runtime, throw IllegalStateException if not configured)
- Preference check returns null from service, controller returns 200 with {skipped: true, reason: "..."} instead of 202

## Deviations from Plan
- Plan suggested @ConditionalOnProperty for SmtpMailService but empty string evaluation issues led to @ConditionalOnBean(JavaMailSender) approach instead
- NotificationController updated with nullable return handling (not explicitly in plan but required by service signature change)

## Issues Encountered
- @ConditionalOnProperty treats empty string "" as "present" when no havingValue is set — resolved by using @ConditionalOnBean(JavaMailSender) which correctly chains with Spring Boot's MailSenderAutoConfiguration

## Next Phase Readiness
- All service layer complete — ready for controllers, DTOs, and GreenMail integration tests in Plan 02-03

---
*Plan: 02-02 of 02-email-service-and-notification-preferences*
*Completed: 2026-03-03*
