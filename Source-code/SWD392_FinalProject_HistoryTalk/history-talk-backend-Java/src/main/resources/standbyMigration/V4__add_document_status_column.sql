-- Add status column to document table for draft/published workflow
ALTER TABLE document ADD COLUMN status VARCHAR(50) DEFAULT 'DRAFT';
