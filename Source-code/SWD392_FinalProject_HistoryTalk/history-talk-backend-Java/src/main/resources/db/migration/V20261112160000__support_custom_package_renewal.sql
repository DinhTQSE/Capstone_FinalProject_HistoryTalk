-- Flyway migration V20261112160000: Support Custom Package Renewal & Scale Adjustment
ALTER TABLE school
    ADD COLUMN IF NOT EXISTS next_custom_token_quota INTEGER,
    ADD COLUMN IF NOT EXISTS next_max_students INTEGER,
    ADD COLUMN IF NOT EXISTS next_max_teachers INTEGER,
    ADD COLUMN IF NOT EXISTS next_daily_student_quota INTEGER,
    ADD COLUMN IF NOT EXISTS next_daily_teacher_quota INTEGER,
    ADD COLUMN IF NOT EXISTS next_subscription_start_date DATE,
    ADD COLUMN IF NOT EXISTS next_subscription_end_date DATE,
    ADD COLUMN IF NOT EXISTS base_custom_token_quota INTEGER;

-- Initialize base_custom_token_quota for existing custom schools
UPDATE school
SET base_custom_token_quota = total_school_token_quota
WHERE package_type = 'ENTERPRISE_CUSTOM' AND base_custom_token_quota IS NULL;
