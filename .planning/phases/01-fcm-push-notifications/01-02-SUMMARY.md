---
phase: 01-fcm-push-notifications
plan: 02
subsystem: api, infra
tags: [firebase, fcm, async, spring-service, push-notifications]

requires:
  - phase: 01-fcm-push-notifications/01-01
    provides: JPA entities, repositories, FirebaseConfig, PushService infrastructure
provides:
  - PushService interface with Firebase and Console implementations
  - DeviceTokenService with upsert and max-per-user enforcement
  - NotificationService with PENDING-before-dispatch pattern
  - NotificationDispatcher as separate @Async bean
  - TopicService with DB-validated topic operations
  - NotificationConfig with ConditionalOnMissingBean fallback
affects: [01-03-controllers]

tech-stack:
  added: []
  patterns: [async-dispatch-separate-bean, conditional-on-missing-bean-fallback, upsert-pattern]

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/notification/service/PushService.kt
    - src/main/kotlin/kz/innlab/template/notification/service/FirebasePushService.kt
    - src/main/kotlin/kz/innlab/template/notification/service/ConsolePushService.kt
    - src/main/kotlin/kz/innlab/template/notification/service/DeviceTokenService.kt
    - src/main/kotlin/kz/innlab/template/notification/service/NotificationService.kt
    - src/main/kotlin/kz/innlab/template/notification/service/NotificationDispatcher.kt
    - src/main/kotlin/kz/innlab/template/notification/service/TopicService.kt
    - src/main/kotlin/kz/innlab/template/config/NotificationConfig.kt
  modified: []

key-decisions:
  - "NotificationDispatcher as separate @Service to avoid @Async self-invocation proxy bypass"
  - "ConsolePushService registered via @Bean @ConditionalOnMissingBean — mirrors ConsoleSmsService pattern"

patterns-established:
  - "Async dispatch: save PENDING synchronously, dispatch via separate bean, update to SENT/FAILED"
  - "Stale token cleanup: reactive on FCM UNREGISTERED/INVALID_ARGUMENT errors"

requirements-completed: [FCM-03, FCM-04, FCM-05, FCM-06, FCM-07, FCM-09, NMGT-01]

duration: 3min
completed: 2026-03-03
---

# Plan 01-02: Notification Service Layer Summary

**PushService with Firebase/console implementations, async dispatch via separate NotificationDispatcher bean, device token lifecycle with upsert and max-per-user limit**

## Performance

- **Duration:** 3 min
- **Tasks:** 2
- **Files created:** 8

## Accomplishments
- PushService interface with 5 methods, FirebasePushService with stale token detection, ConsolePushService for dev
- DeviceTokenService with register (upsert on userId+deviceId), configurable max-per-user limit
- NotificationService saves PENDING history before async dispatch, returns ID for 202 Accepted
- NotificationDispatcher as separate @Async @Service avoids self-invocation proxy bypass
- TopicService validates topics against DB before subscribe/send operations

## Task Commits

1. **Task 1: PushService, FirebasePushService, ConsolePushService, NotificationConfig** - `e74b725` (feat)
2. **Task 2: DeviceTokenService, NotificationService, NotificationDispatcher, TopicService** - `dfc6108` (feat)

## Decisions Made
None - followed plan as specified.

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Complete service layer ready for controller delegation
- Plan 01-03 can now build REST endpoints on top of all services

---
*Plan: 01-02 of 01-fcm-push-notifications*
*Completed: 2026-03-03*
