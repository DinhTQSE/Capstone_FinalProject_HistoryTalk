-- =============================================================================
-- Migration: Add subscription_start_date and subscription_end_date to b2b_quote_request
-- Date: 2026-11-12
-- =============================================================================

ALTER TABLE b2b_quote_request
    ADD COLUMN IF NOT EXISTS subscription_start_date DATE,
    ADD COLUMN IF NOT EXISTS subscription_end_date DATE;
