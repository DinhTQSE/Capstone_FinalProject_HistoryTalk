-- V30: Add School Table and SaaS Fields for Multi-Tenancy (Sprint 5)

-- 1. Bảng Trường học (School)
CREATE TABLE IF NOT EXISTS school (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    school_code VARCHAR(50) NOT NULL UNIQUE,
    address VARCHAR(500),
    contact_email VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(20),
    package_type VARCHAR(50) NOT NULL,
    total_school_token_quota INT NOT NULL DEFAULT 0,
    unallocated_token_quota INT NOT NULL DEFAULT 0,
    local_history_policy_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

-- 2. Bổ sung trường liên kết SaaS vào bảng "user"
ALTER TABLE "user" 
    ADD COLUMN IF NOT EXISTS school_id UUID REFERENCES school(id),
    ADD COLUMN IF NOT EXISTS student_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS subject_department VARCHAR(100);

-- 3. Tạo Index tối ưu hóa truy vấn Multi-tenancy
CREATE INDEX IF NOT EXISTS idx_user_school_id ON "user"(school_id);
CREATE INDEX IF NOT EXISTS idx_user_school_student_code ON "user"(school_id, student_code);
