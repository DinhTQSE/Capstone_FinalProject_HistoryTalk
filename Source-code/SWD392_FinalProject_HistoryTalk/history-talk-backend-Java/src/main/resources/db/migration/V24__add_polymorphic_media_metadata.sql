-- Migration: Add Polymorphic Support to document_media_metadata
-- This migration transforms document_media_metadata from a Document-centric table
-- to a polymorphic table that can reference Characters, Contexts, Documents, and Users directly.

-- Step 1: Add new polymorphic columns
ALTER TABLE document_media_metadata
ADD COLUMN IF NOT EXISTS entity_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS entity_id UUID;

-- Step 2: Make document_id nullable (it's now optional for DOCUMENT entities)
ALTER TABLE document_media_metadata
ALTER COLUMN document_id DROP NOT NULL;

-- Step 3: Migrate existing data from Document-based structure to polymorphic structure
-- This assumes existing media was attached to Documents with entity_type and entity_id
UPDATE document_media_metadata dmm
SET 
    entity_type = (
        SELECT d.entity_type 
        FROM document d 
        WHERE d.doc_id = dmm.document_id
    ),
    entity_id = (
        SELECT d.entity_id 
        FROM document d 
        WHERE d.doc_id = dmm.document_id
    )
WHERE dmm.document_id IS NOT NULL;

-- Step 4: Add constraints for new columns
ALTER TABLE document_media_metadata
ALTER COLUMN entity_type SET NOT NULL,
ALTER COLUMN entity_id SET NOT NULL;

-- Step 5: Create indexes for polymorphic queries
CREATE INDEX IF NOT EXISTS idx_document_media_metadata_entity_type_entity_id 
ON document_media_metadata(entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_document_media_metadata_entity_type 
ON document_media_metadata(entity_type);

CREATE INDEX IF NOT EXISTS idx_document_media_metadata_entity_id 
ON document_media_metadata(entity_id);

-- Step 6: Add check constraint for valid entity types
ALTER TABLE document_media_metadata
ADD CONSTRAINT chk_entity_type 
CHECK (entity_type IN ('CHARACTER', 'CONTEXT', 'DOCUMENT', 'USER'));

-- Step 7: Add comment to document_id to clarify it's optional
COMMENT ON COLUMN document_media_metadata.document_id IS 
'Optional: Only set when media is attached to a Document entity. For CHARACTER/CONTEXT/USER, use entity_type and entity_id instead.';

COMMENT ON COLUMN document_media_metadata.entity_type IS 
'Required: The type of entity this media belongs to (CHARACTER, CONTEXT, DOCUMENT, USER).';

COMMENT ON COLUMN document_media_metadata.entity_id IS 
'Required: The ID of the entity this media belongs to (characterId, contextId, docId, or userId).';

-- Step 8: (Optional) Drop old foreign key constraint if it exists
-- Uncomment the following line if you want to remove the FK constraint entirely
-- ALTER TABLE document_media_metadata DROP CONSTRAINT IF EXISTS fk_document_media_metadata_document_id;
