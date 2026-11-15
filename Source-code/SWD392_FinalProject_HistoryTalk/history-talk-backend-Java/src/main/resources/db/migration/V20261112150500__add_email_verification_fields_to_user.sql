ALTER TABLE historical_schema."user"
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS email_verification_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS email_verification_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS email_verification_sent_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_user_email_verification_token_hash
    ON historical_schema."user" (email_verification_token_hash)
    WHERE email_verification_token_hash IS NOT NULL;
