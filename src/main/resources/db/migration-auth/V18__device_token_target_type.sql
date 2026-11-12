-- FCM addresses an app install by its registration token or by its Firebase Installation ID
-- (FID). firebase-admin 9.12 deprecates token targeting in favour of FIDs, so a registration
-- records which of the two its fcm_token column holds. Existing rows are registration tokens.
ALTER TABLE device_tokens
    ADD COLUMN target_type VARCHAR(16) NOT NULL DEFAULT 'TOKEN'
        CHECK (target_type IN ('TOKEN', 'FID'));
