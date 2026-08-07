-- Phone OTPs move into verification_codes under purpose = 'PHONE_LOGIN'.
--
-- The two tables held the same shape and the same brute-force protections in two copies,
-- which had already drifted apart. sms_verifications is a strict subset of verification_codes
-- (phone -> identifier; no new_value / user_id), so the rows carry over unchanged.
--
-- Only live rows are migrated: used or expired codes are worthless and the cleanup job
-- would delete them on its next run anyway.
INSERT INTO verification_codes (
    id, user_id, identifier, purpose, code_hash, new_value,
    expires_at, used, attempts, created_at
)
SELECT
    sv.id,
    NULL,
    sv.phone,
    'PHONE_LOGIN',
    sv.code_hash,
    NULL,
    sv.expires_at,
    sv.used,
    sv.attempts,
    sv.created_at
FROM sms_verifications sv
WHERE sv.used = FALSE
  AND sv.expires_at > NOW();

DROP TABLE sms_verifications;
