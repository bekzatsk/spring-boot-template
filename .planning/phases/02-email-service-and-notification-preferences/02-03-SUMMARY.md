---
phase: 02-email-service-and-notification-preferences
plan: 03
subsystem: controllers-and-tests
tags: [mail-controller, imap, preferences, greenmail, integration-tests, dto]

requires:
  - phase: 02-email-service-and-notification-preferences
    plan: 02
    provides: SmtpMailService, MailDispatcher, ConsoleMailService, ImapService, NotificationPreferenceService
provides:
  - MailController with 6 REST endpoints (send, send/with-attachments, inbox, inbox/{n}, read/unread, history)
  - Preference endpoints on NotificationController (GET/PUT /preferences)
  - 6 DTOs (SendEmailRequest, MailHistoryResponse, InboxMessageResponse, EmailMessageResponse, NotificationPreferenceRequest, NotificationPreferenceResponse)
  - MailIntegrationTest (8 tests) with GreenMail in-process SMTP/IMAP
  - NotificationPreferenceIntegrationTest (6 tests) for opt-out preference model
  - ConsoleEmailService as dedicated EmailService-only fallback
  - app.mail.enabled property for conditional SMTP bean activation
affects: []

tech-stack:
  added: [awaitility-kotlin]
  patterns: [conditional-on-property, greenmail-integration, dynamic-property-source, console-email-fallback]

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/notification/controller/MailController.kt
    - src/main/kotlin/kz/innlab/template/notification/dto/SendEmailRequest.kt
    - src/main/kotlin/kz/innlab/template/notification/dto/MailHistoryResponse.kt
    - src/main/kotlin/kz/innlab/template/notification/dto/InboxMessageResponse.kt
    - src/main/kotlin/kz/innlab/template/notification/dto/EmailMessageResponse.kt
    - src/main/kotlin/kz/innlab/template/notification/dto/NotificationPreferenceRequest.kt
    - src/main/kotlin/kz/innlab/template/notification/dto/NotificationPreferenceResponse.kt
    - src/main/kotlin/kz/innlab/template/notification/service/ConsoleEmailService.kt
    - src/test/kotlin/kz/innlab/template/MailIntegrationTest.kt
    - src/test/kotlin/kz/innlab/template/NotificationPreferenceIntegrationTest.kt
  modified:
    - src/main/kotlin/kz/innlab/template/notification/controller/NotificationController.kt
    - src/main/kotlin/kz/innlab/template/config/MailConfig.kt
    - src/main/kotlin/kz/innlab/template/config/MailProperties.kt
    - src/main/kotlin/kz/innlab/template/notification/service/MailDispatcher.kt
    - src/main/resources/application.yaml
    - pom.xml

key-decisions:
  - "Switched from @ConditionalOnBean(JavaMailSender) to @ConditionalOnProperty(app.mail.enabled=true) — user @Configuration classes process before auto-config, so JavaMailSender bean is not visible at evaluation time"
  - "Created dedicated ConsoleEmailService (implements only EmailService) to avoid NoUniqueBeanDefinitionException when ConsoleMailService implements both MailService and EmailService"
  - "Added app.mail.enabled boolean property to MailProperties — mirrors app.firebase.enabled pattern"
  - "MailDispatcher marked as open class with open methods for CGLIB proxy (required by @Async since Kotlin classes are final by default)"
  - "GreenMail tests use @DynamicPropertySource to configure SMTP/IMAP ports from the running GreenMail instance"
  - "Async dispatch verified with Awaitility await().atMost(5s) pattern instead of Thread.sleep"

patterns-established:
  - "@ConditionalOnProperty(app.mail.enabled=true) for gating SMTP beans — avoids auto-config ordering issues"
  - "Separate ConsoleEmailService for clean single-interface fallback beans"
  - "GreenMail with @DynamicPropertySource for email integration testing"
  - "Awaitility for verifying async operations in tests"

requirements-completed: [MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06, MAIL-07, MAIL-09, NMGT-03, NMGT-04, NFRA-05]

duration: 8min
completed: 2026-03-03
---

# Plan 02-03: Controllers and Tests Summary

**MailController, preference endpoints, DTOs, GreenMail integration tests, and preference integration tests**

## Performance

- **Duration:** 8 min
- **Tasks:** 2
- **Files created:** 10
- **Files modified:** 6

## Accomplishments
- Created MailController with 6 endpoints: POST /send (JSON), POST /send/with-attachments (multipart), GET /inbox (offset pagination), GET /inbox/{n}, PUT /inbox/{n}/read, DELETE /inbox/{n}/read, GET /history (cursor pagination)
- Added GET/PUT /preferences endpoints to NotificationController for notification opt-out management
- Created 6 DTOs with jakarta.validation and companion object factory methods
- Created MailIntegrationTest (8 tests) using GreenMail in-process SMTP/IMAP server with @DynamicPropertySource
- Created NotificationPreferenceIntegrationTest (6 tests) verifying opt-out defaults, partial updates, re-enable, and auth
- Fixed critical bean wiring: switched to @ConditionalOnProperty (app.mail.enabled) from @ConditionalOnBean(JavaMailSender) which failed due to auto-config ordering
- Created ConsoleEmailService as dedicated EmailService-only fallback to avoid NoUniqueBeanDefinitionException
- Added awaitility-kotlin dependency for async test assertions
- All 63 tests pass (49 existing + 14 new)

## Task Commits

1. **Task 1: DTOs, MailController, preference endpoints** - `5ac5041` (feat)
2. **Task 2: Integration tests and bean configuration fix** - `55d621c` (feat)

## Decisions Made
- @ConditionalOnProperty with app.mail.enabled replaces @ConditionalOnBean(JavaMailSender) because user @Configuration classes are processed before Spring Boot auto-configuration
- ConsoleEmailService (implements only EmailService) separate from ConsoleMailService (implements only MailService) prevents Spring from seeing two MailService beans when both console fallbacks are active
- MailDispatcher is open class with open methods for CGLIB proxying required by @Async annotation
- sendDirect also marked open to suppress CGLIB proxy warning

## Deviations from Plan
- Plan assumed @ConditionalOnBean(JavaMailSender) would work; replaced with @ConditionalOnProperty(app.mail.enabled) due to bean evaluation ordering
- Added app.mail.enabled property to MailProperties and application.yaml (not in original plan)
- Created ConsoleEmailService class (not in original plan) to fix dual-interface bean ambiguity
- Reduced test count from planned 8-10 to 8 (omitted markRead/markUnread and multipart attachment tests to keep scope focused)

## Issues Encountered
- **ApplicationContext failure**: @ConditionalOnBean(JavaMailSender) in user @Configuration evaluates before auto-config creates JavaMailSender — resolved with @ConditionalOnProperty
- **NoUniqueBeanDefinitionException**: consoleEmailService returning ConsoleMailService (which implements MailService+EmailService) created two MailService beans — resolved with dedicated ConsoleEmailService
- **Kotlin final class prevents CGLIB proxy**: MailDispatcher needs open class/methods for @Async proxy creation
- **AccountManagementIntegrationTest bean conflict**: @MockitoBean emailService replaced the shared consoleMailService bean — resolved by separating into independent bean instances

## Next Phase Readiness
- Phase 2 complete: all plans (02-01, 02-02, 02-03) implemented and tested
- Total 63 tests pass across all test classes
- Ready for phase completion and state transition

---
*Plan: 02-03 of 02-email-service-and-notification-preferences*
*Completed: 2026-03-03*
