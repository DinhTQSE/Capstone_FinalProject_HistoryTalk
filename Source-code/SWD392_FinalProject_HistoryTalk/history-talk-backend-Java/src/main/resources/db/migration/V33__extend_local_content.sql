-- V33: Extend local content tables for FE feedback (Sprint 6)
-- Changes:
--   1. Add content_status column (DRAFT | PENDING_REVIEW | ACTIVE | INACTIVE) — replaces is_published-based derivation
--   2. Add review_note column — admin feedback note saved on PUT .../status
--   3. Add latitude, longitude to historical_context — for map display
--   4. Create class_local_content join table — assign local content to specific classroom(s)

-- ── historical_context: new columns ──────────────────────────────────────────
ALTER TABLE historical_schema.historical_context
    ADD COLUMN IF NOT EXISTS content_status VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS review_note    TEXT        NULL,
    ADD COLUMN IF NOT EXISTS latitude       DOUBLE PRECISION NULL,
    ADD COLUMN IF NOT EXISTS longitude      DOUBLE PRECISION NULL;

-- Backfill: derive content_status from existing is_published + deleted_at
UPDATE historical_schema.historical_context
SET content_status = CASE
    WHEN deleted_at IS NOT NULL THEN 'INACTIVE'
    WHEN is_published = true    THEN 'ACTIVE'
    ELSE                             'DRAFT'
END
WHERE school_id IS NOT NULL AND content_status IS NULL;

-- ── character: new columns ────────────────────────────────────────────────────
ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS content_status VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS review_note    TEXT        NULL;

-- Backfill character
UPDATE historical_schema."character"
SET content_status = CASE
    WHEN deleted_at IS NOT NULL THEN 'INACTIVE'
    WHEN is_published = true    THEN 'ACTIVE'
    ELSE                             'DRAFT'
END
WHERE school_id IS NOT NULL AND content_status IS NULL;

-- ── class_local_content: M2M — classroom ↔ local content ─────────────────────
-- Stores which classrooms have been assigned a local context or character.
-- entity_type: 'CONTEXT' | 'CHARACTER'
CREATE TABLE IF NOT EXISTS class_local_content (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    class_id    UUID        NOT NULL,   -- FK to classroom (no cross-schema FK, enforced at app layer)
    entity_id   UUID        NOT NULL,   -- contextId or characterId
    entity_type VARCHAR(20) NOT NULL,   -- 'CONTEXT' | 'CHARACTER'
    assigned_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    assigned_by UUID        NULL,       -- Teacher who assigned it
    CONSTRAINT uq_class_entity UNIQUE (class_id, entity_id, entity_type)
);

CREATE INDEX IF NOT EXISTS idx_clc_class_id   ON class_local_content (class_id);
CREATE INDEX IF NOT EXISTS idx_clc_entity     ON class_local_content (entity_id, entity_type);

-- ── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_hctx_content_status
    ON historical_schema.historical_context (content_status) WHERE school_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_char_content_status
    ON historical_schema."character" (content_status) WHERE school_id IS NOT NULL;

-- ── Comments ─────────────────────────────────────────────────────────────────
COMMENT ON COLUMN historical_schema.historical_context.content_status IS 'DRAFT | PENDING_REVIEW | ACTIVE | INACTIVE — explicit status for local content.';
COMMENT ON COLUMN historical_schema.historical_context.review_note    IS 'Feedback note from School Admin when approving/rejecting.';
COMMENT ON COLUMN historical_schema.historical_context.latitude       IS 'Latitude for map pin display (local context only).';
COMMENT ON COLUMN historical_schema.historical_context.longitude      IS 'Longitude for map pin display (local context only).';
COMMENT ON COLUMN historical_schema."character".content_status        IS 'DRAFT | PENDING_REVIEW | ACTIVE | INACTIVE — explicit status for local character.';
COMMENT ON COLUMN historical_schema."character".review_note           IS 'Feedback note from School Admin.';
COMMENT ON TABLE  class_local_content                                  IS 'M2M: assigns a local context or character to one or more classrooms.';
