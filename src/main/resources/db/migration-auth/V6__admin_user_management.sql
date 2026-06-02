-- Temporary password + required actions on users
ALTER TABLE users ADD COLUMN password_temporary BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE user_required_actions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action  VARCHAR(50) NOT NULL,
    CONSTRAINT uq_user_required_actions UNIQUE (user_id, action)
);

CREATE INDEX idx_user_required_actions_user_id ON user_required_actions(user_id);

-- Admin audit log
CREATE TABLE admin_audit_log (
    id           UUID PRIMARY KEY,
    admin_id     UUID NOT NULL,
    action       VARCHAR(100) NOT NULL,
    target_id    UUID,
    before_value TEXT,
    after_value  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_log_admin_id ON admin_audit_log(admin_id);
CREATE INDEX idx_admin_audit_log_target_id ON admin_audit_log(target_id);
CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log(created_at);
