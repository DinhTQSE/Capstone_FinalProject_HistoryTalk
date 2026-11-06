-- =============================================================================
-- Migration: Each character speaks with a fixed voice of the self-hosted TTS server
-- Date: 2026-11-06
-- NULL means the default voice (see CharacterVoices.DEFAULT_VOICE).
-- =============================================================================

ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS voice_id VARCHAR(50);
