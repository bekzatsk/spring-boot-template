-- createCode replaces the previous row for each identifier and purpose. This constraint
-- also prevents two concurrent requests from committing two valid codes.
-- If duplicates already exist, stop and inspect them before applying this migration.
CREATE UNIQUE INDEX uq_verification_code_identifier_purpose
    ON verification_codes (identifier, purpose);
