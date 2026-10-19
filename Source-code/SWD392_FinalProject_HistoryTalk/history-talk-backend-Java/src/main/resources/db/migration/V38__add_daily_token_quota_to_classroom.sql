-- =========================================================================
-- Flyway V38: Add daily_token_quota to classroom
-- =========================================================================

ALTER TABLE classroom
    ADD COLUMN IF NOT EXISTS daily_token_quota INT NOT NULL DEFAULT 0;
