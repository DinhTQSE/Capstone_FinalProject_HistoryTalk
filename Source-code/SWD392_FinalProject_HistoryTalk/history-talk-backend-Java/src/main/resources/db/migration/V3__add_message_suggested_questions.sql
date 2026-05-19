ALTER TABLE historical_schema.message
    ADD COLUMN IF NOT EXISTS suggested_questions TEXT;
