-- 1. Bổ sung cột password_changed_at vào bảng "user" để theo dõi vô hiệu hóa phiên tức thì khi đổi mật khẩu / logout-all
ALTER TABLE historical_schema."user"
ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- 2. Tạo bảng lưu trữ refresh_tokens cho kiến trúc Dual-Token & RTR
CREATE TABLE IF NOT EXISTS historical_schema.refresh_tokens (
    token_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    family_id      UUID NOT NULL,
    is_revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    device_info    VARCHAR(255),
    ip_address     VARCHAR(45),
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Tạo các Indexes tối ưu hóa truy vấn
CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON historical_schema.refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id ON historical_schema.refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_family_id ON historical_schema.refresh_tokens(family_id);
