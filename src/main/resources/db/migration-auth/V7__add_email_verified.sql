-- Email verification flag. Existing users are grandfathered as verified (TRUE default),
-- so enabling app.auth.email-verification does not lock out pre-existing accounts.
-- New LOCAL registrations set this FALSE and carry a VERIFY_EMAIL required action until confirmed.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
