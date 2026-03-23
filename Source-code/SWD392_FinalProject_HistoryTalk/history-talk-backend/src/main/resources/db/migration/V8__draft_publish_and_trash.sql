-- Add draft flags to character and historical_context
ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS is_draft boolean NOT NULL DEFAULT true;

ALTER TABLE historical_schema.historical_context
    ADD COLUMN IF NOT EXISTS is_draft boolean NOT NULL DEFAULT true;

-- Backfill legacy data: mark existing records as published
UPDATE historical_schema."character" SET is_draft = false WHERE is_draft IS NULL;
UPDATE historical_schema.historical_context SET is_draft = false WHERE is_draft IS NULL;

-- Helpful indexes for filtering
CREATE INDEX IF NOT EXISTS idx_character_publish_filter
    ON historical_schema."character" (deleted_at, is_draft, name);

CREATE INDEX IF NOT EXISTS idx_context_publish_filter
    ON historical_schema.historical_context (deleted_at, is_draft, created_date);
