# Phase 1: FCM Push Notifications - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can register device tokens (Android/iOS/Web), send push notifications to individual devices, multicast to up to 500 tokens, or send to topics via FCM. Stale tokens are cleaned up automatically on FCM rejection. Sent notifications are logged in a history table. Firebase initializes safely with a console fallback for dev. Email service and notification preferences are Phase 2.

</domain>

<decisions>
## Implementation Decisions

### Send API design
- Async delivery: API accepts notification requests with 202 Accepted and processes in background
- Endpoint structure: Claude's discretion (separate endpoints vs unified)
- Notification status tracking approach: Claude's discretion (individual check vs history-only)
- Data payload validation: Claude's discretion (pass-through vs server-side validation)

### Device token lifecycle
- Max tokens per user: configurable via application.yml (not hardcoded)
- Hard delete on token removal — no soft delete, row is removed entirely
- Stale token cleanup: reactive only — delete when FCM returns UNREGISTERED/INVALID_ARGUMENT during send, no scheduled job
- Duplicate registration handling: Claude's discretion
- Device identifier strategy: Claude's discretion
- Registration response: return only the single registered token details (not full list)
- GET /notifications/tokens endpoint to list all user's registered tokens
- Bulk delete: support both DELETE /notifications/tokens/{deviceId} (single) and DELETE /notifications/tokens (all user tokens)

### Notification history
- Log all send attempts — PENDING, SENT, FAILED statuses for complete audit trail
- Access model: users see own history + admin role can view all notifications
- Cursor-based pagination using UUID v7 (aligns with existing project pattern)
- Read/dismiss marking: Claude's discretion

### Topic management
- Predefined topics only — users can only subscribe to topics that exist in the system
- Topics stored in database table, managed via admin API (dynamic, no restart needed)
- No list endpoint — clients know topic names from app logic
- Topic send authorization: Claude's discretion

### Claude's Discretion
- Endpoint structure (separate vs unified send endpoints)
- Notification status tracking (individual vs history-only)
- Data payload validation strategy
- Device token deduplication on re-register
- Device identifier approach (user-provided deviceId vs FCM token as key)
- Notification history read/dismiss interaction model
- Topic send authorization (admin-only vs any authenticated user)

</decisions>

<specifics>
## Specific Ideas

- Token limit should be a property in application.yml — consistent with how other configurable values are handled in the project
- Cursor-based pagination chosen to leverage existing UUID v7 infrastructure for natural time-ordering

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-fcm-push-notifications*
*Context gathered: 2026-03-03*
