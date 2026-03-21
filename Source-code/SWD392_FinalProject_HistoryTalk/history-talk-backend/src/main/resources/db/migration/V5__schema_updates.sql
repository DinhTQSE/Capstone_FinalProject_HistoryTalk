-- Disable statement timeout for this migration
SET statement_timeout = 0;

-- ============================================================
-- V5: Schema updates – Many-to-Many + Soft Delete columns
-- ============================================================

-- ============================================================
-- Part 1: Character ↔ HistoricalContext many-to-many
-- ============================================================
CREATE TABLE IF NOT EXISTS historical_schema.character_historical_context (
    character_id uuid NOT NULL,
    context_id uuid NOT NULL,
    CONSTRAINT pk_character_historical_context PRIMARY KEY (character_id, context_id),
    CONSTRAINT fk_chc_character FOREIGN KEY (character_id)
        REFERENCES historical_schema."character" (character_id) ON DELETE CASCADE,
    CONSTRAINT fk_chc_context FOREIGN KEY (context_id)
        REFERENCES historical_schema.historical_context (context_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chc_context_id ON historical_schema.character_historical_context (context_id);
CREATE INDEX IF NOT EXISTS idx_chc_character_id ON historical_schema.character_historical_context (character_id);

INSERT INTO historical_schema.character_historical_context (character_id, context_id)
SELECT character_id, context_id FROM historical_schema."character"
WHERE context_id IS NOT NULL
ON CONFLICT DO NOTHING;

ALTER TABLE historical_schema.chat_session
    ADD COLUMN IF NOT EXISTS context_id uuid;

UPDATE historical_schema.chat_session cs
SET context_id = c.context_id
FROM historical_schema."character" c
WHERE cs.character_id = c.character_id
  AND cs.context_id IS NULL;

ALTER TABLE historical_schema.chat_session
    ALTER COLUMN context_id SET NOT NULL;

ALTER TABLE historical_schema.chat_session
    ADD CONSTRAINT fk_chat_session_context FOREIGN KEY (context_id)
        REFERENCES historical_schema.historical_context (context_id) ON DELETE CASCADE;

ALTER TABLE historical_schema."character"
    DROP COLUMN IF EXISTS context_id;

-- ============================================================
-- Part 2: Add soft-delete columns to all entities
-- ============================================================
ALTER TABLE "character" ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE character_document ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE historical_context ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE historical_context_document ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE chat_session ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE message ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE quiz_result ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE quiz_answer_detail ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE quiz_session ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE "user" ADD COLUMN deleted_at TIMESTAMP NULL;

-- Reset statement timeout
RESET statement_timeout;

