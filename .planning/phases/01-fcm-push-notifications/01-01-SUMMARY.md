---
phase: 01-fcm-push-notifications
plan: 01
subsystem: database, infra
tags: [firebase, fcm, jpa, flyway, kotlin, spring-data]

requires:
  - phase: v5.0-05-account-mgmt
    provides: existing BaseEntity with UUID v7, Flyway V2 migration, project conventions
provides:
  - firebase-admin SDK on classpath with version-aligned Google dependencies
  - FirebaseConfig bean with conditional activation and double-init guard
  - V3 Flyway migration for device_tokens, notification_history, notification_topics
  - DeviceToken, NotificationHistory, NotificationTopic JPA entities
  - Platform, NotificationType, NotificationStatus enums
  - DeviceTokenRepository, NotificationHistoryRepository, NotificationTopicRepository
affects: [01-02-service-layer, 01-03-controllers]

tech-stack:
  added: [firebase-admin 9.4.3, com.google.cloud:libraries-bom 26.55.0]
  patterns: [conditional-bean-activation, version-bom-alignment, cursor-based-pagination]

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/config/FirebaseConfig.kt
    - src/main/resources/db/migration/V3__add_notifications.sql
    - src/main/kotlin/kz/innlab/template/notification/model/DeviceToken.kt
    - src/main/kotlin/kz/innlab/template/notification/model/NotificationHistory.kt
    - src/main/kotlin/kz/innlab/template/notification/model/NotificationTopic.kt
    - src/main/kotlin/kz/innlab/template/notification/repository/DeviceTokenRepository.kt
    - src/main/kotlin/kz/innlab/template/notification/repository/NotificationHistoryRepository.kt
    - src/main/kotlin/kz/innlab/template/notification/repository/NotificationTopicRepository.kt
  modified:
    - pom.xml
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml

key-decisions:
  - "Added libraries-bom 26.55.0 to align google-http-client versions between firebase-admin and google-api-client"
  - "Used firebase-admin 9.4.3 (latest available) instead of plan-specified 9.8.0"

patterns-established:
  - "ConditionalOnProperty for Firebase: app.firebase.enabled gates FirebaseApp bean creation"
  - "Cursor-based pagination: UUID v7 IDs serve as cursors (time-ordered), no offset needed"

requirements-completed: [FCM-08, NFRA-01, NFRA-02, NFRA-04]

duration: 3min
completed: 2026-03-03
---

# Plan 01-01: Firebase SDK + Data Foundation Summary

**Firebase Admin SDK with version-aligned dependencies, V3 Flyway migration for 3 notification tables, and complete JPA entity/repository layer**

## Performance

- **Duration:** 3 min
- **Tasks:** 2
- **Files created:** 11
- **Files modified:** 3

## Accomplishments
- firebase-admin 9.4.3 on classpath with libraries-bom resolving google-http-client version conflicts
- FirebaseConfig creates FirebaseApp bean only when app.firebase.enabled=true with double-init guard
- V3 Flyway migration creates device_tokens, notification_history, and notification_topics with FK constraints and indexes
- All 3 JPA entities extend BaseEntity (UUID v7) with correct column mappings
- 3 repositories with cursor-based pagination for NotificationHistory

## Task Commits

1. **Task 1: Add firebase-admin, FirebaseConfig, application.yaml** - `88c5389` (feat)
2. **Task 2: V3 migration, entities, enums, repositories** - `06882ea` (feat)

## Files Created/Modified
- `pom.xml` - Added firebase-admin 9.4.3 + libraries-bom 26.55.0
- `src/main/kotlin/kz/innlab/template/config/FirebaseConfig.kt` - FirebaseApp bean with conditional activation
- `src/main/resources/application.yaml` - Added firebase.enabled and notification.token.max-per-user config
- `src/test/resources/application.yaml` - Firebase disabled for tests
- `src/main/resources/db/migration/V3__add_notifications.sql` - 3 notification tables
- `src/main/kotlin/kz/innlab/template/notification/model/*.kt` - 3 entities + 3 enums
- `src/main/kotlin/kz/innlab/template/notification/repository/*.kt` - 3 repositories

## Decisions Made
- Used firebase-admin 9.4.3 instead of plan-specified 9.8.0 (9.8.0 does not exist in Maven Central)
- Added libraries-bom 26.55.0 dependencyManagement to align google-http-client versions (firebase-admin brought 2.0.0, google-api-client brought 1.45.3 - BOM unified to 1.46.1)

## Deviations from Plan
None - plan executed as written. Firebase version adjusted to latest available.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Data foundation complete: entities, repositories, and Firebase SDK ready
- Service layer (Plan 01-02) can now build PushService, DeviceTokenService, NotificationService on top of these repositories

---
*Plan: 01-01 of 01-fcm-push-notifications*
*Completed: 2026-03-03*
