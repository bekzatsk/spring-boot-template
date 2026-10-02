-- Fail migration if multiple users already share one external identity.
-- Investigate and resolve duplicates before adding this constraint; never merge accounts automatically.
CREATE UNIQUE INDEX uq_user_provider_external_id
    ON user_provider_ids (provider, provider_id);
