-- =============================================================================
-- Migration: Make vector_chunk searchable by the AI service
-- Date: 2026-11-05
-- The AI service writes bge-m3 embeddings (1024 dimensions) to this table and
-- searches them through match_history_chunks, which no migration created yet.
-- =============================================================================

-- Supabase keeps extensions in their own schema; plain Postgres has neither.
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS vector SCHEMA extensions;
SET LOCAL search_path = historical_schema, extensions, public;

-- 1. Fails on purpose if existing rows do not have 1024 dimensions: they came from
--    another embedding model and must be re-imported rather than converted.
ALTER TABLE historical_schema.vector_chunk
    ALTER COLUMN embedding TYPE vector(1024) USING embedding::vector(1024);

CREATE INDEX IF NOT EXISTS idx_vector_chunk_entity
    ON historical_schema.vector_chunk (entity_id);

-- 2. Exact search within the requested entities. An HNSW index is left out on purpose:
--    it filters entities after picking neighbours, so it can drop relevant chunks when
--    an entity holds only a small part of the table.
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
    WHERE vc.embedding IS NOT NULL
      AND vc.is_active = TRUE
      AND vc.deleted_at IS NULL
      AND (filter_entity_ids IS NULL OR vc.entity_id = ANY (filter_entity_ids))
    ORDER BY vc.embedding <=> query_embedding
    LIMIT match_limit;
$$;

-- 3. The AI service reaches this schema through the Supabase API as service_role.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT USAGE ON SCHEMA historical_schema, extensions TO service_role;
        GRANT SELECT, INSERT, UPDATE, DELETE ON historical_schema.vector_chunk TO service_role;
        GRANT EXECUTE ON FUNCTION historical_schema.match_history_chunks(vector, INT, UUID[]) TO service_role;
    END IF;
END $$;
