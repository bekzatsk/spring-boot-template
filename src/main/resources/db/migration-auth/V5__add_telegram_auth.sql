-- Telegram authentication sessions
CREATE TABLE telegram_auth_sessions (
    id              UUID PRIMARY KEY,
    session_id      VARCHAR(255) NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
    code_hash       VARCHAR(255),
    attempts        INTEGER      NOT NULL DEFAULT 0,
    max_attempts    INTEGER      NOT NULL DEFAULT 3,
    telegram_user_id   BIGINT,
    telegram_username  VARCHAR(255),
    telegram_chat_id   BIGINT,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    verified_at     TIMESTAMPTZ
);

CREATE INDEX idx_telegram_auth_sessions_session_id ON telegram_auth_sessions(session_id);
CREATE INDEX idx_telegram_auth_sessions_status ON telegram_auth_sessions(status);
CREATE INDEX idx_telegram_auth_sessions_expires_at ON telegram_auth_sessions(expires_at);

-- Add telegram fields to users table
ALTER TABLE users ADD COLUMN telegram_user_id BIGINT UNIQUE;
ALTER TABLE users ADD COLUMN telegram_username VARCHAR(255);
