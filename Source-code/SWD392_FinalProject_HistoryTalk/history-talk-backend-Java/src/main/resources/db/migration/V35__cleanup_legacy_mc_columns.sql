-- V35: Remove legacy Multiple Choice columns now that all types use question_data.
-- Data will be re-imported manually; no backfill needed.

-- 1. Drop legacy MC columns from question table
ALTER TABLE historical_schema.question
    DROP COLUMN IF EXISTS options,
    DROP COLUMN IF EXISTS correct_answer;

-- 2. Drop legacy MC answer column from quiz_answer_detail
ALTER TABLE historical_schema.quiz_answer_detail
    DROP COLUMN IF EXISTS selected_option;

-- 3. Allow question_report.quiz_id to be NULL (bank questions have no quiz)
ALTER TABLE historical_schema.question_report
    ALTER COLUMN quiz_id DROP NOT NULL;
