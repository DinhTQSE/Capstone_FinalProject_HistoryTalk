-- Add status column to document table
-- This column is needed for the Document entity's ContentStatus field

ALTER TABLE historical_schema.document 
ADD COLUMN status VARCHAR(50) DEFAULT 'DRAFT';

-- Add index on status for filtering
CREATE INDEX idx_document_status ON historical_schema.document(status);
