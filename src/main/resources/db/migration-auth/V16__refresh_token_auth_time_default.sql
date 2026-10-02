-- V15 shipped in 0.1.4 and cannot change: an edited migration fails Flyway's checksum validation
-- for every database that already applied it.
--
-- A default lets instances still on an older version — which do not know this column — keep
-- creating refresh tokens during a rolling deploy. A constant default is metadata-only. Tokens
-- created that way read as "logged in at the epoch", so actions that need a recent login ask
-- for a new one: the safe side.
ALTER TABLE refresh_tokens
    ALTER COLUMN authenticated_at SET DEFAULT '1970-01-01 00:00:00+00';
