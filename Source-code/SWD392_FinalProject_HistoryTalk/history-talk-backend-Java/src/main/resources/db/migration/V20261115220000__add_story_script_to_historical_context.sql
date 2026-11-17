-- =============================================
-- Story script JSON on historical_context
-- =============================================
-- Stores the node-graph story ("kịch bản kể chuyện") authored by content staff:
-- { version, title, narratorId, status: draft|published, updatedAt, nodes[], edges[] }.
-- Nullable: existing contexts have no story yet. Validated in the service layer.

ALTER TABLE historical_context
    ADD COLUMN IF NOT EXISTS story_script JSONB;

COMMENT ON COLUMN historical_context.story_script IS
    'Nullable JSON story script (version, title, narratorId, status, updatedAt, nodes[], edges[]). '
    'Learners only receive it when status = published.';
