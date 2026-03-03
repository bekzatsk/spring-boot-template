---
phase: 02-email-service-and-notification-preferences
plan: 01
subsystem: infra
tags: [mail, smtp, imap, flyway, jpa, configuration]

requires:
  - phase: 01-fcm-push-notifications
    provides: notification package structure, BaseEntity, existing models and repositories
provides:
  - spring-boot-starter-mail and greenmail-spring Maven dependencies
  - MailProperties @ConfigurationProperties for SMTP/IMAP/retry config
  - V4 Flyway migration for mail_history and notification_preferences tables
  - MailHistory and NotificationPreference JPA entities extending BaseEntity
  - MailStatus and NotificationChannel enums
  - MailHistoryRepository with cursor-based pagination
  - NotificationPreferenceRepository with findByUserId and findByUserIdAndChannel
  - MailService interface with send and sendEmail methods
  - EmailAttachment data class for file attachments
affects: [02-02-services, 02-03-controllers]

tech-stack:
  added: [spring-boot-starter-mail, greenmail-spring]
  patterns: [configuration-properties, cursor-based-pagination, flyway-migration]

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/config/MailProperties.kt
    - src/main/resources/db/migration/V4__add_mail_and_preferences.sql
    - src/main/kotlin/kz/innlab/template/notification/model/MailHistory.kt
    - src/main/kotlin/kz/innlab/template/notification/model/MailStatus.kt
    - src/main/kotlin/kz/innlab/template/notification/model/NotificationChannel.kt
    - src/main/kotlin/kz/innlab/template/notification/model/NotificationPreference.kt
    - src/main/kotlin/kz/innlab/template/notification/repository/MailHistoryRepository.kt
    - src/main/kotlin/kz/innlab/template/notification/repository/NotificationPreferenceRepository.kt
    - src/main/kotlin/kz/innlab/template/notification/service/MailService.kt
  modified:
    - pom.xml
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - src/main/kotlin/kz/innlab/template/config/SmsSchedulerConfig.kt

key-decisions:
  - "MailService interface includes both send() and sendEmail(userId, ...) for tracked vs untracked sends"
  - "Separate mail_history table from notification_history — different schema needs"
  - "@EnableConfigurationProperties added to SmsSchedulerConfig (existing config class)"

patterns-established:
  - "MailProperties with nested data classes for SMTP, IMAP, and retry configuration"
  - "mail_history table with PENDING/SENT/FAILED status and attempts counter for retry tracking"

requirements-completed: [MAIL-08, NFRA-03]

duration: 3min
completed: 2026-03-03
---

# Plan 02-01: Mail Foundation Summary

**spring-boot-starter-mail and greenmail dependencies, MailProperties config, V4 Flyway migration (mail_history + notification_preferences), JPA entities, repositories, MailService interface**

## Performance

- **Duration:** 3 min
- **Tasks:** 2
- **Files created:** 9
- **Files modified:** 4

## Accomplishments
- Added spring-boot-starter-mail and greenmail-spring 2.1.3 Maven dependencies
- Created MailProperties @ConfigurationProperties with SmtpProperties, ImapProperties, RetryProperties
- Created V4 Flyway migration with mail_history and notification_preferences tables
- Created MailHistory and NotificationPreference JPA entities extending BaseEntity
- Created MailStatus and NotificationChannel enums
- Created MailHistoryRepository with cursor-based pagination (same pattern as NotificationHistoryRepository)
- Created NotificationPreferenceRepository with findByUserId and findByUserIdAndChannel
- Created MailService interface with send and sendEmail methods plus EmailAttachment data class

## Task Commits

1. **Task 1: Maven deps, MailProperties, YAML config** - `0bb9226` (feat)
2. **Task 2: V4 migration, entities, repositories, MailService interface** - `7a0a6ac` (feat)

## Decisions Made
- MailService includes sendEmail(userId) for tracked sends alongside send() for untracked sends
- Separate mail_history table rather than reusing notification_history

## Deviations from Plan
None.

## Issues Encountered
None.

## Next Phase Readiness
- Complete foundation ready for SmtpMailService, MailDispatcher, ImapService, and ConsoleMailService in Plan 02-02

---
*Plan: 02-01 of 02-email-service-and-notification-preferences*
*Completed: 2026-03-03*
