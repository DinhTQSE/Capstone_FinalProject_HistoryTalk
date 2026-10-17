-- =========================================================================
-- Flyway V37: Add quiz_session_id and chat_session_id to assignment_submission
-- Author: KhaiVDD
-- =========================================================================

ALTER TABLE historical_schema.assignment_submission
    ADD COLUMN IF NOT EXISTS quiz_session_id UUID REFERENCES historical_schema.quiz_session(session_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS chat_session_id UUID REFERENCES historical_schema.chat_session(session_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_submission_quiz_session_id ON historical_schema.assignment_submission(quiz_session_id);
CREATE INDEX IF NOT EXISTS idx_submission_chat_session_id ON historical_schema.assignment_submission(chat_session_id);
