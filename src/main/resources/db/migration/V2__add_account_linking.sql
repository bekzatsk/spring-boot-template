-- Step 1: Create collection tables BEFORE dropping old columns
CREATE TABLE user_providers (
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    CONSTRAINT uq_user_providers UNIQUE (user_id, provider)
);

CREATE TABLE user_provider_ids (
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_user_provider_ids UNIQUE (user_id, provider)
);

-- Step 2: Migrate existing data from old single-provider columns to new tables
INSERT INTO user_providers (user_id, provider)
SELECT id, provider FROM users;

-- Only GOOGLE and APPLE have external provider IDs; LOCAL email users use email as providerId (not stored in providerIds map)
INSERT INTO user_provider_ids (user_id, provider, provider_id)
SELECT id, provider, provider_id FROM users
WHERE provider IN ('GOOGLE', 'APPLE');

-- Step 3: Add partial unique index for email uniqueness (excludes phone users with email = '')
CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE email != '';

-- Step 4: Drop old single-provider columns and constraint
ALTER TABLE users DROP CONSTRAINT uq_provider_provider_id;
ALTER TABLE users DROP COLUMN provider;
ALTER TABLE users DROP COLUMN provider_id;
