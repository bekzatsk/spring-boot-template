-- When the user last proved who they are. Rotation copies it to the new token, so an access token
-- minted by /auth/refresh still says when the login happened rather than when it was refreshed.
--
-- A constant default makes this a metadata-only change on PostgreSQL 11+: no table rewrite and no
-- long exclusive lock on refresh_tokens, which every login and refresh writes to. Existing tokens
-- read as "logged in at the epoch", so actions that need a recent login ask those sessions to log
-- in again — the safe side. The default stays: instances still running the previous version
-- during a rolling deploy insert tokens without this column.
ALTER TABLE refresh_tokens
    ADD COLUMN authenticated_at TIMESTAMPTZ NOT NULL DEFAULT '1970-01-01 00:00:00+00';
