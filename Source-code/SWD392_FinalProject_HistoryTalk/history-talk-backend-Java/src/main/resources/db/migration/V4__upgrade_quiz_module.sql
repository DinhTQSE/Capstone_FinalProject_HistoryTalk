-- 1. Bảng quiz: Thêm các cột thông tin mới và soft delete
ALTER TABLE historical_schema.quiz
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS grade INT,
    ADD COLUMN IF NOT EXISTS chapter_number INT,
    ADD COLUMN IF NOT EXISTS chapter_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS era VARCHAR(50),
    ADD COLUMN IF NOT EXISTS duration_seconds INT,
    ADD COLUMN IF NOT EXISTS play_count INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rating DOUBLE PRECISION DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 2. Bảng question: Thêm các cột mới và soft delete
ALTER TABLE historical_schema.question
    ADD COLUMN IF NOT EXISTS order_index INT,
    ADD COLUMN IF NOT EXISTS explanation TEXT,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Ép kiểu cột correct_answer từ VARCHAR(255) sang INT
-- ⚠️ CẢNH BÁO: Lệnh này dùng USING để ép kiểu. Nó giả định data cũ (nếu có)
-- đang lưu dạng số nhưng là chuỗi (ví dụ: '0', '1', '2').
-- Nếu data cũ lưu chữ cái ('A', 'B'), bạn cần chạy UPDATE đổi 'A' thành '0' trước, nếu không sẽ bị lỗi cast.
ALTER TABLE historical_schema.question
ALTER COLUMN correct_answer TYPE INT USING correct_answer::integer;

