-- Server-side resend throttling for Telegram auth sessions:
-- code_sent_at enforces the resend cooldown, resend_count caps resends per session.
ALTER TABLE telegram_auth_sessions ADD COLUMN code_sent_at TIMESTAMPTZ;
ALTER TABLE telegram_auth_sessions ADD COLUMN resend_count INTEGER NOT NULL DEFAULT 0;
