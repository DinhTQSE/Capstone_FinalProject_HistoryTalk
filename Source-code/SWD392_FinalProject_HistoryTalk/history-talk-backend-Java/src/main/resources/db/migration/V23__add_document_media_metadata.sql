-- Create document_media_metadata table
CREATE TABLE document_media_metadata (
    metadata_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document(doc_id) ON DELETE CASCADE,
    media_type VARCHAR(20) NOT NULL,
    file_format VARCHAR(20),
    file_size_bytes BIGINT,
    width INTEGER,
    height INTEGER,
    storage_path VARCHAR(500) NOT NULL,
    thumbnail_path VARCHAR(500),
    extended_metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on document_id for fast lookups
CREATE INDEX idx_document_media_metadata_document_id ON document_media_metadata(document_id);

-- Create index on media_type for filtering
CREATE INDEX idx_document_media_metadata_media_type ON document_media_metadata(media_type);

-- Create index on storage_path for quick deletion lookups
CREATE INDEX idx_document_media_metadata_storage_path ON document_media_metadata(storage_path);
