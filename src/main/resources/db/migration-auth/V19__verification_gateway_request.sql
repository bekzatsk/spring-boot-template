-- Telegram Gateway request_id of the message that carries the code, so a delivery report (which
-- names only the request_id) finds its verification. Null for codes sent through other channels.
ALTER TABLE verification_codes ADD COLUMN gateway_request_id VARCHAR(128);
CREATE INDEX idx_verification_codes_gateway_request_id ON verification_codes (gateway_request_id);
