-- V34: Question Bank + new question types (FILL_IN_BLANK, MATCHING)
-- Preserves all legacy columns and data; no breaking changes.

-- 1. Add question_type (default MULTIPLE_CHOICE so all existing rows stay classified)
ALTER TABLE historical_schema.question
    ADD COLUMN IF NOT EXISTS question_type VARCHAR(40) NOT NULL DEFAULT 'MULTIPLE_CHOICE';

-- 2. Add question_data TEXT for typed per-type payload (stored as JSON text)
ALTER TABLE historical_schema.question
    ADD COLUMN IF NOT EXISTS question_data TEXT NULL;

-- 3. Create quiz_question link table (many-to-many, replaces direct FK for bank-aware code)
CREATE TABLE IF NOT EXISTS historical_schema.quiz_question (
    quiz_id     UUID NOT NULL REFERENCES historical_schema.quiz(quiz_id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES historical_schema.question(question_id) ON DELETE CASCADE,
    order_index INTEGER NOT NULL DEFAULT 0,
    points      NUMERIC(8,2) NOT NULL DEFAULT 1,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (quiz_id, question_id)
);

-- 4. Backfill quiz_question from existing question rows that have a quiz_id
INSERT INTO historical_schema.quiz_question (quiz_id, question_id, order_index, points, created_at)
SELECT
    q.quiz_id,
    q.question_id,
    ROW_NUMBER() OVER (PARTITION BY q.quiz_id ORDER BY q.created_at, q.question_id) - 1 AS order_index,
    1 AS points,
    COALESCE(q.created_at, CURRENT_TIMESTAMP)
FROM historical_schema.question q
WHERE q.quiz_id IS NOT NULL
ON CONFLICT (quiz_id, question_id) DO NOTHING;

-- 5. Make question.quiz_id nullable (questions can now exist in the bank without a quiz)
ALTER TABLE historical_schema.question
    ALTER COLUMN quiz_id DROP NOT NULL;

-- 6. Add answer_data TEXT and score_earned to quiz_answer_detail
ALTER TABLE historical_schema.quiz_answer_detail
    ADD COLUMN IF NOT EXISTS answer_data TEXT NULL;

ALTER TABLE historical_schema.quiz_answer_detail
    ADD COLUMN IF NOT EXISTS score_earned NUMERIC(8,2) NULL;

-- 7. Make selected_option nullable (FILL_IN_BLANK and MATCHING don't use it)
ALTER TABLE historical_schema.quiz_answer_detail
    ALTER COLUMN selected_option DROP NOT NULL;

-- 8. Indexes for quiz_question lookups
CREATE INDEX IF NOT EXISTS idx_quiz_question_question_id
    ON historical_schema.quiz_question (question_id);

CREATE INDEX IF NOT EXISTS idx_quiz_question_quiz_order
    ON historical_schema.quiz_question (quiz_id, order_index);

-- 9. Check constraint for question_type
ALTER TABLE historical_schema.question
    DROP CONSTRAINT IF EXISTS chk_question_type;
ALTER TABLE historical_schema.question
    ADD CONSTRAINT chk_question_type
        CHECK (question_type IN ('MULTIPLE_CHOICE', 'FILL_IN_BLANK', 'MATCHING'));
