-- =============================================================================
-- Migration: B2C/B2B Unified Pricing Standardization & B2B Quote Request Flow
-- Date: 2026-11-06
-- =============================================================================
-- 1. Update B2C Tier Pricing based on 0.15 VND/token + 7,000 VND DB/Quiz/Exam fee:
--    - Free: 0 VND, 1,500 tokens/day
--    - Plus (Monthly): 150,000 * 0.15 (22,500) + 7,000 = 29,500 VND/month (5,000 tokens/day)
--    - Pro  (Monthly): 300,000 * 0.15 (45,000) + 7,000 = 52,000 VND/month (10,000 tokens/day)
--    - Plus (Yearly - 15% off): 354,000 * 0.85 = 300,900 VND/year
--    - Pro  (Yearly - 15% off): 624,000 * 0.85 = 530,400 VND/year

UPDATE historical_schema.tier
SET amount = 0, limited_token = 1500, updated_at = CURRENT_TIMESTAMP
WHERE tier_id = '00000000-0000-0000-0000-000000000001';

UPDATE historical_schema.tier
SET amount = 29500, no_month = 1, limited_token = 5000, updated_at = CURRENT_TIMESTAMP
WHERE tier_id = '00000000-0000-0000-0000-000000000002';

UPDATE historical_schema.tier
SET amount = 52000, no_month = 1, limited_token = 10000, updated_at = CURRENT_TIMESTAMP
WHERE tier_id = '00000000-0000-0000-0000-000000000003';

UPDATE historical_schema.tier
SET amount = 300900, no_month = 12, limited_token = 5000, updated_at = CURRENT_TIMESTAMP
WHERE tier_id = '00000000-0000-0000-0000-000000000004';

UPDATE historical_schema.tier
SET amount = 530400, no_month = 12, limited_token = 10000, updated_at = CURRENT_TIMESTAMP
WHERE tier_id = '00000000-0000-0000-0000-000000000005';

-- 2. Extend school table with max_students, max_teachers and update default daily quotas to 5,000
ALTER TABLE school
    ADD COLUMN IF NOT EXISTS max_students INTEGER,
    ADD COLUMN IF NOT EXISTS max_teachers INTEGER,
    ALTER COLUMN default_daily_student_quota SET DEFAULT 5000,
    ALTER COLUMN default_daily_teacher_quota SET DEFAULT 5000;

-- 3. Create b2b_quote_request table for Customize B2B Plan & Request a Quote workflow
CREATE TABLE IF NOT EXISTS b2b_quote_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_code VARCHAR(30) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    contact_email VARCHAR(150) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    job_title VARCHAR(150) NOT NULL,
    school_name VARCHAR(255) NOT NULL,
    school_code VARCHAR(50),
    school_address VARCHAR(500),
    selected_package VARCHAR(50) NOT NULL,
    number_of_students INTEGER NOT NULL,
    number_of_teachers INTEGER NOT NULL,
    student_daily_token_limit INTEGER NOT NULL DEFAULT 5000,
    teacher_daily_token_limit INTEGER NOT NULL DEFAULT 5000,
    total_token_pool BIGINT NOT NULL,
    ai_token_cost_vnd BIGINT NOT NULL,
    db_and_lms_cost_vnd BIGINT NOT NULL,
    subtotal_vnd BIGINT NOT NULL,
    discount_percent INTEGER NOT NULL DEFAULT 0,
    discount_amount_vnd BIGINT NOT NULL DEFAULT 0,
    final_price_vnd BIGINT NOT NULL,
    promo_code VARCHAR(50),
    message TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    admin_note TEXT,
    reviewed_by_uid UUID REFERENCES "user"(uid),
    reviewed_at TIMESTAMP,
    created_school_id UUID REFERENCES school(id),
    created_school_admin_uid UUID REFERENCES "user"(uid),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_b2b_quote_request_status_created
    ON b2b_quote_request(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_b2b_quote_request_email
    ON b2b_quote_request(contact_email);
