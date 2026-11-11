-- =============================================================================
-- Migration: Lip-sync portrait settings of each character
-- Date: 2026-11-11
-- Opaque JSON object saved by the FE portrait editor (mode, mouthOpen, zoom,
-- points, imageUrl...). NULL means the character has no saved settings.
-- =============================================================================

ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS portrait_settings JSONB;
