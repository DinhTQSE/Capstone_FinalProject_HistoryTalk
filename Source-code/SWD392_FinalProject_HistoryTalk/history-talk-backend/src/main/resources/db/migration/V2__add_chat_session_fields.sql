ALTER TABLE historical_schema.chat_session
    ADD COLUMN IF NOT EXISTS title VARCHAR(255) DEFAULT '',
    ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMP;
