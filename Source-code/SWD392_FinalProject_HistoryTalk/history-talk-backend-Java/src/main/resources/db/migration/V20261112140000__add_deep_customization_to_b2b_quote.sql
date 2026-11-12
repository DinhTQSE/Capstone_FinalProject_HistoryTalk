-- =============================================================================
-- Migration: Add deep customization fields to b2b_quote_request
-- Date: 2026-11-12
-- =============================================================================

ALTER TABLE b2b_quote_request
    ADD COLUMN IF NOT EXISTS academic_weeks INT DEFAULT 35,
    ADD COLUMN IF NOT EXISTS student_days_per_week INT DEFAULT 4,
    ADD COLUMN IF NOT EXISTS teacher_days_per_week INT DEFAULT 6;
