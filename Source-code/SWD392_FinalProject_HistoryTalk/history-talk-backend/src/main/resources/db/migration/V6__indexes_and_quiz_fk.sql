-- Disable statement timeout for this migration
SET statement_timeout = 0;

-- ============================================================
-- V6: Indexes + Quiz FK constraints
-- ============================================================

-- ============================================================
-- Part 1: Quiz soft-delete column + context FK
-- ============================================================
ALTER TABLE historical_schema.quiz
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE historical_schema.quiz
    ALTER COLUMN context_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'fk_quiz_context'
    ) THEN
        ALTER TABLE historical_schema.quiz
            ADD CONSTRAINT fk_quiz_context FOREIGN KEY (context_id)
            REFERENCES historical_schema.historical_context (context_id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_quiz_context_deleted_at
    ON historical_schema.quiz (context_id, deleted_at);

-- ============================================================
-- Part 2: Soft-delete indexes for all entities
-- ============================================================
CREATE INDEX idx_character_deleted_at ON "character"(deleted_at);
CREATE INDEX idx_character_doc_deleted_at ON character_document(deleted_at);
CREATE INDEX idx_context_deleted_at ON historical_context(deleted_at);
CREATE INDEX idx_context_doc_deleted_at ON historical_context_document(deleted_at);
CREATE INDEX idx_chat_session_deleted_at ON chat_session(deleted_at);
CREATE INDEX idx_message_deleted_at ON message(deleted_at);
CREATE INDEX idx_quiz_result_deleted_at ON quiz_result(deleted_at);
CREATE INDEX idx_quiz_answer_detail_deleted_at ON quiz_answer_detail(deleted_at);
CREATE INDEX idx_quiz_session_deleted_at ON quiz_session(deleted_at);
CREATE INDEX idx_user_deleted_at ON "user"(deleted_at);
