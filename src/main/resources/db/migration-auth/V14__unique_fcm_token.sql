-- An FCM token addresses one physical app install. Push send and topic subscription accept a
-- token only when the caller has it registered, so a token held by two users let the previous
-- owner of a device keep pushing to whoever signed in on it next. Keep the most recently
-- updated registration of each token and make the token unique.
DELETE FROM device_tokens d
USING device_tokens newer
WHERE d.fcm_token = newer.fcm_token
  AND (d.updated_at, d.id) < (newer.updated_at, newer.id);

DROP INDEX IF EXISTS idx_device_tokens_fcm_token;
CREATE UNIQUE INDEX uq_device_tokens_fcm_token ON device_tokens (fcm_token);
