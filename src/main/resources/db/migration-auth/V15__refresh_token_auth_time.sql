-- When the user last proved who they are. Rotation copies it to the new token, so an access token
-- minted by /auth/refresh still says when the login happened rather than when it was refreshed.
-- Existing tokens get their creation time: the closest record we have.
ALTER TABLE refresh_tokens ADD COLUMN authenticated_at TIMESTAMPTZ;
UPDATE refresh_tokens SET authenticated_at = created_at;
ALTER TABLE refresh_tokens ALTER COLUMN authenticated_at SET NOT NULL;
