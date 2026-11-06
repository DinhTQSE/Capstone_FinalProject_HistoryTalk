-- =============================================================================
-- Migration: Preferred pronouns of each character
-- Date: 2026-11-06
-- How a character refers to itself and to the learner, e.g. "ta" and "ngươi".
-- NULL means the defaults "tôi" and "bạn" (see CharacterPronouns).
-- =============================================================================

ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS self_pronoun VARCHAR(30),
    ADD COLUMN IF NOT EXISTS address_pronoun VARCHAR(30);
