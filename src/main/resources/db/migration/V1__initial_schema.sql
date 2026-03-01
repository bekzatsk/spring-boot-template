CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    picture VARCHAR(512),
    password_hash VARCHAR(255),
    phone VARCHAR(30) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_provider_id UNIQUE (provider, provider_id)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMPTZ,
    replaced_by_token_hash VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
