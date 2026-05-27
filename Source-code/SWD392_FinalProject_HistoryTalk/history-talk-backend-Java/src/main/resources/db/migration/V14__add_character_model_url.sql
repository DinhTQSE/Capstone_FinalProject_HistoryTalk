ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS model_url VARCHAR(500);
