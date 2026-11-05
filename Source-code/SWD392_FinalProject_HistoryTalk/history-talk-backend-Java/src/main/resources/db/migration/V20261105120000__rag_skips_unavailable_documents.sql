-- =============================================================================
-- Migration: RAG search skips documents that are not available
-- Date: 2026-11-05
-- Documents default to DRAFT and no flow publishes them yet, so DRAFT stays searchable.
-- Only documents that were deleted, deactivated or are waiting for review are skipped.
-- =============================================================================

SET LOCAL search_path = historical_schema, extensions, public;

CREATE OR REPLACE FUNCTION historical_schema.match_history_chunks(
    query_embedding vector(1024),
    match_limit INT DEFAULT 10,
    filter_entity_ids UUID[] DEFAULT NULL
)
RETURNS TABLE (
    chunk_id UUID,
    doc_id UUID,
    entity_id UUID,
    content TEXT,
    similarity DOUBLE PRECISION
)
LANGUAGE sql
STABLE
SET search_path = historical_schema, extensions, public
AS $$
    SELECT vc.chunk_id,
           vc.doc_id,
           vc.entity_id,
           vc.content,
           1 - (vc.embedding <=> query_embedding) AS similarity
    FROM historical_schema.vector_chunk vc
    JOIN historical_schema.document d ON d.doc_id = vc.doc_id
    WHERE vc.embedding IS NOT NULL
      AND vc.is_active = TRUE
      AND vc.deleted_at IS NULL
      AND d.deleted_at IS NULL
      AND COALESCE(d.status, 'DRAFT') NOT IN ('INACTIVE', 'PENDING_REVIEW')
      AND (filter_entity_ids IS NULL OR vc.entity_id = ANY (filter_entity_ids))
    ORDER BY vc.embedding <=> query_embedding
    LIMIT match_limit;
$$;

-- The function runs with the caller's rights, so the AI service must be able to read document.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT SELECT ON historical_schema.document TO service_role;
    END IF;
END $$;
