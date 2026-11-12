-- ============================================================================
-- Migration: Add School Token Topup support to payment_order table
-- Schema: historical_schema
-- Description: Allows schools (SCHOOL_ADMIN) to purchase additional tokens
--              via PayOS checkout without requiring a B2C Tier subscription.
-- ============================================================================

-- 1. Make tier_id nullable in payment_order so B2B school topups don't require a B2C tier
ALTER TABLE historical_schema.payment_order ALTER COLUMN tier_id DROP NOT NULL;

-- 2. Add order_type column (TIER_SUBSCRIPTION vs SCHOOL_TOKEN_TOPUP)
ALTER TABLE historical_schema.payment_order 
    ADD COLUMN IF NOT EXISTS order_type VARCHAR(30) NOT NULL DEFAULT 'TIER_SUBSCRIPTION';

-- 3. Add school_id column referencing school(id)
ALTER TABLE historical_schema.payment_order 
    ADD COLUMN IF NOT EXISTS school_id UUID NULL REFERENCES school(id);

-- 4. Add topup_tokens column
ALTER TABLE historical_schema.payment_order 
    ADD COLUMN IF NOT EXISTS topup_tokens BIGINT NULL;

-- 5. Index for school purchase queries
CREATE INDEX IF NOT EXISTS idx_payment_order_school_type 
    ON historical_schema.payment_order (school_id, order_type, created_at DESC);
