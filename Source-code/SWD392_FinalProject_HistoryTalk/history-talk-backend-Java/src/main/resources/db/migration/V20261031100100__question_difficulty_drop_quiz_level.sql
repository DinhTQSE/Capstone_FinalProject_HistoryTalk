-- =============================================================================
-- Migration: Difficulty moves from quiz to each question; drop quiz.level
-- Date: 2026-10-31
-- Author: Bao-2005
-- Depends on V20261031100000 (every question membership is in quiz_question)
-- =============================================================================

-- 1. New per-question difficulty column
ALTER TABLE historical_schema.question ADD COLUMN IF NOT EXISTS difficulty VARCHAR(10);

-- 2. Inherit the level of the earliest-linked quiz
UPDATE historical_schema.question q
SET difficulty = (SELECT z.level
                  FROM historical_schema.quiz_question l
                  JOIN historical_schema.quiz z ON z.quiz_id = l.quiz_id
                  WHERE l.question_id = q.question_id
                  ORDER BY l.created_at, z.quiz_id
                  LIMIT 1)
WHERE q.difficulty IS NULL;

-- 3. Bank questions never linked to a quiz
UPDATE historical_schema.question SET difficulty = 'MEDIUM' WHERE difficulty IS NULL;

-- 4. Constraints and index for the question bank filter
ALTER TABLE historical_schema.question ALTER COLUMN difficulty SET NOT NULL;
ALTER TABLE historical_schema.question DROP CONSTRAINT IF EXISTS chk_question_difficulty;
ALTER TABLE historical_schema.question
    ADD CONSTRAINT chk_question_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'));
CREATE INDEX IF NOT EXISTS idx_question_difficulty ON historical_schema.question (difficulty);

-- 5. The quiz itself no longer has a difficulty
ALTER TABLE historical_schema.quiz DROP COLUMN IF EXISTS level;
