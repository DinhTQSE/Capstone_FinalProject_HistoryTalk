-- ============================================================================
-- Flyway Migration V38: School Subscription (01/09 - 31/05), Daily Token Pool,
-- Token Boost Requests, and Academic Year Student Lifecycle
-- ============================================================================

-- 1. Extend school table for academic year subscription cycle (01/09 - 31/05) and daily student token limit
ALTER TABLE school
    ADD COLUMN IF NOT EXISTS subscription_start_date DATE,
    ADD COLUMN IF NOT EXISTS subscription_end_date DATE,
    ADD COLUMN IF NOT EXISTS renewed_for_next_year BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS next_package_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS default_daily_student_quota INTEGER NOT NULL DEFAULT 1000;

-- 2. Extend classroom table to allow optional per-classroom/grade daily token quota override
ALTER TABLE classroom
    ADD COLUMN IF NOT EXISTS daily_token_quota INTEGER;

-- 3. Extend "user" table to track account_status and daily student token usage against school shared pool
ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS deactivated_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS daily_tokens_used INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS daily_bonus_tokens INTEGER NOT NULL DEFAULT 0;

-- 4. Create token_boost_request table for daily student token boost workflow (US-SP7-17)
CREATE TABLE IF NOT EXISTS token_boost_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id),
    classroom_id UUID NOT NULL REFERENCES classroom(id),
    student_uid UUID NOT NULL REFERENCES "user"(uid),
    requested_tokens INTEGER NOT NULL,
    approved_tokens INTEGER,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_uid UUID REFERENCES "user"(uid),
    review_note VARCHAR(500),
    request_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_token_boost_req_classroom_status
    ON token_boost_request(classroom_id, status, request_date);
CREATE INDEX IF NOT EXISTS idx_token_boost_req_student_date
    ON token_boost_request(student_uid, request_date);
