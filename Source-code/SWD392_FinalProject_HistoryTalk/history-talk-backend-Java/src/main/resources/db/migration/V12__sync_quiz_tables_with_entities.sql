-- Sync quiz-related tables with current JPA entities
-- PostgreSQL / Supabase

-- =========================
-- quiz
-- Entity fields:
-- quiz_id, context_id, created_by, title, level, is_active,
-- created_at, updated_at, deleted_at
-- =========================

ALTER TABLE historical_schema.quiz
DROP COLUMN IF EXISTS description,
    DROP COLUMN IF EXISTS grade,
    DROP COLUMN IF EXISTS chapter_number,
    DROP COLUMN IF EXISTS chapter_title,
    DROP COLUMN IF EXISTS era,
    DROP COLUMN IF EXISTS duration_seconds,
    DROP COLUMN IF EXISTS play_count,
    DROP COLUMN IF EXISTS rating,
    ADD COLUMN IF NOT EXISTS level VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN;

UPDATE historical_schema.quiz
SET level = 'EASY'
WHERE level IS NULL;

UPDATE historical_schema.quiz
SET is_active = TRUE
WHERE is_active IS NULL;

ALTER TABLE historical_schema.quiz
    ALTER COLUMN title SET NOT NULL,
ALTER COLUMN context_id SET NOT NULL,
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN level SET NOT NULL,
    ALTER COLUMN is_active SET NOT NULL;


-- =========================
-- question
-- Entity fields:
-- question_id, quiz_id, content, options, correct_answer,
-- explanation, is_active, created_at, updated_at, deleted_at
-- =========================

ALTER TABLE historical_schema.question
DROP COLUMN IF EXISTS order_index,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN;

UPDATE historical_schema.question
SET is_active = TRUE
WHERE is_active IS NULL;

ALTER TABLE historical_schema.question
    ALTER COLUMN quiz_id SET NOT NULL,
ALTER COLUMN content SET NOT NULL,
    ALTER COLUMN is_active SET NOT NULL;


-- =========================
-- quiz_session
-- Entity fields:
-- session_id, quiz_id, uid, limited_time, start_time,
-- end_time, score, is_active, created_at, updated_at, deleted_at
-- =========================

ALTER TABLE historical_schema.quiz_session
DROP COLUMN IF EXISTS is_submitted,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN;

UPDATE historical_schema.quiz_session
SET is_active = TRUE
WHERE is_active IS NULL;

ALTER TABLE historical_schema.quiz_session
    ALTER COLUMN quiz_id SET NOT NULL,
ALTER COLUMN uid SET NOT NULL,
    ALTER COLUMN is_active SET NOT NULL;


-- =========================
-- quiz_answer_detail
-- Entity fields:
-- detail_id, question_id, session_id, selected_option,
-- is_correct, is_active, created_at, deleted_at
-- =========================

ALTER TABLE historical_schema.quiz_answer_detail
DROP COLUMN IF EXISTS updated_at,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN;

UPDATE historical_schema.quiz_answer_detail
SET is_active = TRUE
WHERE is_active IS NULL;

UPDATE historical_schema.quiz_answer_detail
SET is_correct = FALSE
WHERE is_correct IS NULL;

ALTER TABLE historical_schema.quiz_answer_detail
    ALTER COLUMN question_id SET NOT NULL,
ALTER COLUMN session_id SET NOT NULL,
    ALTER COLUMN selected_option SET NOT NULL,
    ALTER COLUMN is_correct SET NOT NULL,
    ALTER COLUMN is_active SET NOT NULL;