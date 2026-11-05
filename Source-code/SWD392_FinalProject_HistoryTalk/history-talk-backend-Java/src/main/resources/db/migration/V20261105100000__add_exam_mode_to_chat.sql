-- =============================================================================
-- Migration: Oral-exam mode for chat sessions
-- Date: 2026-11-05
-- =============================================================================

-- 1. A session is either a normal chat or an oral exam
ALTER TABLE historical_schema.chat_session
    ADD COLUMN IF NOT EXISTS session_mode VARCHAR(20) NOT NULL DEFAULT 'CHAT',
    ADD COLUMN IF NOT EXISTS exam_question_count INTEGER;

-- 2. Exam state lives on the assistant message that asks the question / grades the answer.
--    exam_expected_answer and exam_source contain the answer, so they are never returned to clients.
ALTER TABLE historical_schema.message
    ADD COLUMN IF NOT EXISTS exam_question TEXT,
    ADD COLUMN IF NOT EXISTS exam_expected_answer TEXT,
    ADD COLUMN IF NOT EXISTS exam_source TEXT,
    ADD COLUMN IF NOT EXISTS exam_verdict VARCHAR(20);
