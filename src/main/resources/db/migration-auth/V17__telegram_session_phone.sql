-- Phone number the user shared in the bot when app.auth.telegram.require-phone is on.
-- E.164 is at most 16 characters with the '+'.
ALTER TABLE telegram_auth_sessions ADD COLUMN phone VARCHAR(20);
