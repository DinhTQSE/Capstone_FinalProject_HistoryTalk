-- V32: Add local content fields for School-owned History Content (Sprint 6 - US-SP6-02, US-SP6-03)
-- Approach: Reuse existing tables, add discriminator columns.
-- school_id NULL  → global content (managed by CONTENT_ADMIN/SYSTEM_ADMIN)
-- school_id NOT NULL → local content (managed by TEACHER, approved by SCHOOL_ADMIN)
--
-- NOTE: historical_context and character live under historical_schema.
--       school and "user" live in the same default search_path schema (no prefix in V30/V31).
--       Cross-schema FKs are omitted here; integrity enforced at application layer.
--       (Alternatively set search_path to include both schemas before running this script.)

-- ── historical_context ────────────────────────────────────────────────────────
ALTER TABLE historical_schema.historical_context
    ADD COLUMN IF NOT EXISTS school_id         UUID        NULL,
    ADD COLUMN IF NOT EXISTS content_scope     VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS approved_by       UUID        NULL;

-- ── character ────────────────────────────────────────────────────────────────
ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS school_id         UUID        NULL,
    ADD COLUMN IF NOT EXISTS content_scope     VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS approved_by       UUID        NULL;

-- ── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_hctx_school_id
    ON historical_schema.historical_context (school_id) WHERE school_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_char_school_id
    ON historical_schema."character" (school_id) WHERE school_id IS NOT NULL AND deleted_at IS NULL;

COMMENT ON COLUMN historical_schema.historical_context.school_id    IS 'NULL = global content. NOT NULL = school-owned local content.';
COMMENT ON COLUMN historical_schema.historical_context.content_scope IS 'CLASS_ONLY | SCHOOL_WIDE — visibility scope for local content.';
COMMENT ON COLUMN historical_schema.historical_context.approved_by   IS 'UID of School Admin who approved this local content.';
COMMENT ON COLUMN historical_schema."character".school_id            IS 'NULL = global content. NOT NULL = school-owned local content.';
COMMENT ON COLUMN historical_schema."character".content_scope        IS 'CLASS_ONLY | SCHOOL_WIDE — visibility scope for local content.';
COMMENT ON COLUMN historical_schema."character".approved_by          IS 'UID of School Admin who approved this local character.';
