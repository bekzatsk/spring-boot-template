CREATE TABLE verification_codes (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    identifier VARCHAR(255) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    new_value VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_codes_identifier_purpose
    ON verification_codes(identifier, purpose);
