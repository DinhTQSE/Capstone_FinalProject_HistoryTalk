ALTER TABLE historical_schema.quiz
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Ensure context_id is non-null and indexed for lookups
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
