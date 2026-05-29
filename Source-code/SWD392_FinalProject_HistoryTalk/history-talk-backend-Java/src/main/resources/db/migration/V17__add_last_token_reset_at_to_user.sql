ALTER TABLE historical_schema."user"
    ADD COLUMN IF NOT EXISTS last_token_reset_at TIMESTAMP(6);
