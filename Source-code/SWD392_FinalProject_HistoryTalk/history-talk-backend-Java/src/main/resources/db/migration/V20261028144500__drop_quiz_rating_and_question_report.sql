-- =============================================================================
-- Migration: Drop legacy quiz_rating and question_report tables
-- Date: 2026-10-28
-- Author: KhaiVDD
-- =============================================================================

DROP TABLE IF EXISTS historical_schema.quiz_rating CASCADE;
DROP TABLE IF EXISTS historical_schema.question_report CASCADE;
