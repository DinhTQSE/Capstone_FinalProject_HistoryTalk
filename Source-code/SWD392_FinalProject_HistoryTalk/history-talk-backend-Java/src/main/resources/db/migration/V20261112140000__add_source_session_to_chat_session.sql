-- =============================================================================
-- Migration: Exam sessions started from a chat
-- Date: 2026-11-12
-- =============================================================================

-- An exam started from a chat asks about what was said there, and the learner can go back to that chat afterwards.
ALTER TABLE historical_schema.chat_session
    ADD COLUMN IF NOT EXISTS source_session_id UUID
        REFERENCES historical_schema.chat_session (session_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_chat_session_source_session_id
    ON historical_schema.chat_session (source_session_id);
