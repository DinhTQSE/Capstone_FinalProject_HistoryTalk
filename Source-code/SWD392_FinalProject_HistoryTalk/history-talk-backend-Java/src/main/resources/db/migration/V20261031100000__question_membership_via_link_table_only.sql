-- =============================================================================
-- Migration: Quiz ↔ question membership lives only in quiz_question
-- Date: 2026-10-31
-- Author: Bao-2005
-- =============================================================================

-- 1. Link every question still attached only through question.quiz_id
--    (createQuiz / addQuestion / CSV import wrote only that column after V34)
INSERT INTO historical_schema.quiz_question (quiz_id, question_id, order_index, points, created_at)
SELECT q.quiz_id,
       q.question_id,
       COALESCE((SELECT MAX(l.order_index) FROM historical_schema.quiz_question l WHERE l.quiz_id = q.quiz_id), -1)
           + ROW_NUMBER() OVER (PARTITION BY q.quiz_id ORDER BY q.created_at, q.question_id),
       1,
       COALESCE(q.created_at, CURRENT_TIMESTAMP)
FROM historical_schema.question q
WHERE q.quiz_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM historical_schema.quiz_question l
                  WHERE l.quiz_id = q.quiz_id AND l.question_id = q.question_id);

-- 2. Membership now lives only in quiz_question (FK constraint is dropped with the column)
ALTER TABLE historical_schema.question DROP COLUMN IF EXISTS quiz_id;
