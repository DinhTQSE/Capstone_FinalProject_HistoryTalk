ALTER TABLE historical_schema."user"
    ADD COLUMN IF NOT EXISTS password_reset_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_user_password_reset_token_hash
    ON historical_schema."user" (password_reset_token_hash)
    WHERE password_reset_token_hash IS NOT NULL;
