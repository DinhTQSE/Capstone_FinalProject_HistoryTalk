-- =====================================================================
-- V40: Teacher Daily Token Quota & Multi-Stage Ticket Workflow
-- 1. Add default_daily_teacher_quota to school (default 10000 tokens/day)
-- 2. Extend token_boost_request to support:
--    - Teacher approving directly from teacher's daily allowance
--    - Teacher creating or escalating tickets to School Admin (PENDING_ADMIN)
-- =====================================================================

ALTER TABLE historical_schema.school
    ADD COLUMN IF NOT EXISTS default_daily_teacher_quota INTEGER NOT NULL DEFAULT 10000;

ALTER TABLE historical_schema.token_boost_request
    ADD COLUMN IF NOT EXISTS requester_uid VARCHAR(36) REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS requester_role VARCHAR(30) NOT NULL DEFAULT 'SCHOOL_STUDENT',
    ADD COLUMN IF NOT EXISTS teacher_reviewed_by_uid VARCHAR(36) REFERENCES historical_schema."user"(uid) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS teacher_review_note TEXT,
    ADD COLUMN IF NOT EXISTS teacher_reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS admin_reviewed_by_uid VARCHAR(36) REFERENCES historical_schema."user"(uid) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS admin_review_note TEXT,
    ADD COLUMN IF NOT EXISTS admin_reviewed_at TIMESTAMP;

ALTER TABLE historical_schema.token_boost_request
    ALTER COLUMN student_uid DROP NOT NULL,
    ALTER COLUMN classroom_id DROP NOT NULL,
    ALTER COLUMN status TYPE VARCHAR(30);

UPDATE historical_schema.token_boost_request
SET requester_uid = student_uid
WHERE requester_uid IS NULL AND student_uid IS NOT NULL;
