-- =============================================================================
-- HistoryTalk AI Service - Supabase PgVector Schema Specification
-- Engine: PostgreSQL with PgVector Extension
-- Purpose: RAG Embedding Storage, HNSW Indexing, & Similarity Search RPC Function
-- Location: docs/services/history-talk-backend-ai/supabase_vector_schema.sql
-- =============================================================================

-- 1. Enable the vector extension for embedding calculations
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Create Vector Storage Table for RAG Documents
CREATE TABLE IF NOT EXISTS public.vector_chunk (
    chunk_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id          UUID NOT NULL,
    entity_id       UUID NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(768) NOT NULL, -- 768 dimensions (bge-m3 / nomic-embed-text / Gemini embeddings)
    sequence_number INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE,
    deleted_at      TIMESTAMP WITH TIME ZONE
);

-- 3. HNSW Index for High-Performance Vector Similarity Search (Cosine Distance)
CREATE INDEX IF NOT EXISTS idx_vector_chunk_embedding_hnsw 
ON public.vector_chunk 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. Standard Composite Indexes for Filter Queries
CREATE INDEX IF NOT EXISTS idx_vector_chunk_doc_id ON public.vector_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_vector_chunk_entity_id ON public.vector_chunk(entity_id);

-- 5. RPC Function: match_history_chunks
-- Called by Python AI Service (`retrieve_history_context` in service.py)
CREATE OR REPLACE FUNCTION match_history_chunks(
    query_embedding vector(768),
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
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        vc.chunk_id,
        vc.doc_id,
        vc.entity_id,
        vc.content,
        1 - (vc.embedding <=> query_embedding) AS similarity
    FROM public.vector_chunk vc
    WHERE vc.is_active = TRUE
      AND vc.deleted_at IS NULL
      AND (filter_entity_ids IS NULL OR vc.entity_id = ANY(filter_entity_ids))
    ORDER BY vc.embedding <=> query_embedding
    LIMIT match_limit;
END;
$$;
