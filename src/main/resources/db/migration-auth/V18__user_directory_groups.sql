-- Group memberships from Active Directory / Entra ID, replaced on every directory login and
-- published in the access token's `groups` claim.
CREATE TABLE user_directory_groups (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_name VARCHAR(512) NOT NULL,
    CONSTRAINT uq_user_directory_groups UNIQUE (user_id, group_name)
);
