-- Align DB schema with HistoricalContextDocument entity
-- Fixes runtime errors like: column d1_0.document_type does not exist
ALTER TABLE historical_schema.historical_context_document
    ADD COLUMN IF NOT EXISTS document_type VARCHAR(50);

UPDATE historical_schema.historical_context_document
SET document_type = 'TEXT'
WHERE document_type IS NULL;

ALTER TABLE historical_schema.historical_context_document
    ALTER COLUMN document_type SET DEFAULT 'TEXT';

ALTER TABLE historical_schema.historical_context_document
    ALTER COLUMN document_type SET NOT NULL;
