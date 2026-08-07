-- Optimistic locking for every entity extending BaseEntity.
--
-- Users are updated from several independent flows (self-service account management, admin
-- edits, provider linking on login) with plain read-modify-write, so concurrent updates lost
-- one side's change with no conflict raised. The same applies to refresh-token rotation and
-- Telegram session state.
--
-- Existing rows start at 0, which is what a freshly loaded entity expects.
ALTER TABLE users                     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE refresh_tokens            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE verification_codes        ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE telegram_auth_sessions    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE device_tokens             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE notification_history      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE notification_preferences  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE notification_topics       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE mail_history              ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE admin_audit_log           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
