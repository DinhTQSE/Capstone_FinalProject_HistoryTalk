ALTER TABLE historical_schema.quiz
    ADD COLUMN IF NOT EXISTS is_published BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE historical_schema.quiz
SET is_published = COALESCE(is_active, FALSE)
WHERE is_published IS DISTINCT FROM COALESCE(is_active, FALSE);

ALTER TABLE historical_schema."user" DROP COLUMN IF EXISTS is_active;
ALTER TABLE historical_schema.historical_context DROP COLUMN IF EXISTS is_active;
ALTER TABLE historical_schema."character" DROP COLUMN IF EXISTS is_active;
ALTER TABLE historical_schema.document DROP COLUMN IF EXISTS is_active;
ALTER TABLE historical_schema.chat_session DROP COLUMN IF EXISTS is_active;
ALTER TABLE historical_schema.message DROP COLUMN IF EXISTS is_active;
ALTER TABLE historical_schema.quiz DROP COLUMN IF EXISTS is_active;
ALTER TABLE historical_schema.question DROP COLUMN IF EXISTS is_active;
ALTER TABLE historical_schema.quiz_answer_detail DROP COLUMN IF EXISTS is_active;
ALTER TABLE historical_schema.quiz_session DROP COLUMN IF EXISTS is_active;

CREATE INDEX IF NOT EXISTS idx_quiz_deleted_at ON historical_schema.quiz (deleted_at);
CREATE INDEX IF NOT EXISTS idx_quiz_publish_filter ON historical_schema.quiz (deleted_at, is_published, title);

CREATE INDEX IF NOT EXISTS idx_character_name_lookup_all
    ON historical_schema."character" (lower(name));

CREATE INDEX IF NOT EXISTS idx_historical_context_name_lookup_all
    ON historical_schema.historical_context (lower(name));

CREATE INDEX IF NOT EXISTS idx_quiz_title_lookup_all
    ON historical_schema.quiz (lower(title));
