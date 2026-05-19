-- ============================================================
-- V7__seed_sample_data.sql
-- Sample data cho testing soft-delete (dual-delete strategy)
-- ============================================================

-- ============================================================
-- 1. USERS (3 users: ADMIN, STAFF, CUSTOMER)
-- ============================================================
-- Password hash = BCrypt của '123456789'
INSERT INTO "user" (uid, email, password, role, user_name)
VALUES
    ('a0000000-0000-0000-0000-000000000001'::uuid,
     'admin@historytalk.com',
     '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy',
     'ADMIN', 'ADMIN1'),

    ('a0000000-0000-0000-0000-000000000002'::uuid,
     'staff@historytalk.com',
     '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy',
     'STAFF', 'STAFF_DEMO'),

    ('a0000000-0000-0000-0000-000000000003'::uuid,
     'demo@historytalk.com',
     '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy',
     'CUSTOMER', 'CUSTOMER_DEMO')
ON CONFLICT (email) DO NOTHING;

-- ============================================================
-- 2. HISTORICAL CONTEXTS (3 contexts)
-- ============================================================
INSERT INTO historical_context
    (context_id, name, description, era, category, year, start_year, end_year,
     before_tcn, location, image_url, video_url, created_by, created_date, updated_date)
VALUES
    -- Nhà Trần & Kháng chiến chống Nguyên Mông
    ('b0000000-0000-0000-0000-000000000001'::uuid,
     'Kháng chiến chống quân Nguyên Mông',
     'Ba lần kháng chiến chống quân Nguyên Mông (1258, 1285, 1288) dưới triều đại nhà Trần là một trong những trang sử hào hùng nhất của dân tộc Việt Nam.',
     'MEDIEVAL', 'WAR', 1258, 1258, 1288, false,
     'Đại Việt', NULL, NULL,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NOW()),

    -- Chiến thắng Điện Biên Phủ
    ('b0000000-0000-0000-0000-000000000002'::uuid,
     'Chiến thắng Điện Biên Phủ 1954',
     'Chiến dịch Điện Biên Phủ (13/3 – 7/5/1954) là trận đánh quyết định kết thúc cuộc kháng chiến chống thực dân Pháp, mở ra kỷ nguyên mới cho dân tộc Việt Nam.',
     'CONTEMPORARY', 'WAR', 1954, 1954, 1954, false,
     'Điện Biên Phủ, Tây Bắc Việt Nam', NULL, NULL,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NOW()),

    -- Hai Bà Trưng
    ('b0000000-0000-0000-0000-000000000003'::uuid,
     'Khởi nghĩa Hai Bà Trưng',
     'Cuộc khởi nghĩa của Hai Bà Trưng (năm 40) là cuộc khởi nghĩa đầu tiên trong lịch sử Việt Nam chống lại ách đô hộ của nhà Hán, giành lại độc lập trong 3 năm.',
     'ANCIENT', 'WAR', 40, 40, 43, false,
     'Mê Linh, Giao Chỉ', NULL, NULL,
     'a0000000-0000-0000-0000-000000000001'::uuid,
     NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 3. HISTORICAL CONTEXT DOCUMENTS (3 docs)
-- ============================================================
INSERT INTO historical_context_document
    (doc_id, title, content, context_id, created_by, upload_date, updated_date)
VALUES
    ('c0000000-0000-0000-0000-000000000001'::uuid,
     'Diễn biến trận Bạch Đằng 1288',
     'Tháng 4 năm 1288, Hưng Đạo Vương Trần Quốc Tuấn đã bố trí trận địa cọc ngầm trên sông Bạch Đằng, đánh tan đoàn thuyền chiến của Ô Mã Nhi...',
     'b0000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NOW()),

    ('c0000000-0000-0000-0000-000000000002'::uuid,
     'Chiến dịch Điện Biên Phủ – 56 ngày đêm',
     'Chiến dịch diễn ra trong 56 ngày đêm, chia làm 3 đợt tấn công. Đợt 1 (13-17/3): tiêu diệt cứ điểm Him Lam và Độc Lập...',
     'b0000000-0000-0000-0000-000000000002'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NOW()),

    ('c0000000-0000-0000-0000-000000000003'::uuid,
     'Hai Bà Trưng – Nữ vương đầu tiên',
     'Trưng Trắc và Trưng Nhị là hai chị em, con gái Lạc tướng huyện Mê Linh. Năm 40, hai bà phất cờ khởi nghĩa tại cửa sông Hát...',
     'b0000000-0000-0000-0000-000000000003'::uuid,
     'a0000000-0000-0000-0000-000000000001'::uuid,
     NOW(), NOW())
ON CONFLICT (doc_id) DO NOTHING;

-- ============================================================
-- 4. CHARACTERS (4 nhân vật lịch sử)
-- ============================================================
INSERT INTO "character"
    (character_id, name, title, background, image, personality, lifespan, side, created_by)
VALUES
    ('d0000000-0000-0000-0000-000000000001'::uuid,
     'Trần Hưng Đạo',
     'Quốc công Tiết chế',
     'Trần Quốc Tuấn (1228–1300), tước Hưng Đạo Vương, là vị tướng tài ba đã ba lần đánh bại quân Nguyên-Mông xâm lược Đại Việt.',
     NULL, 'Cương nghị, mưu lược, yêu nước, khoan dung', '1228–1300', 'Nhà Trần',
     'a0000000-0000-0000-0000-000000000002'::uuid),

    ('d0000000-0000-0000-0000-000000000002'::uuid,
     'Trần Quốc Toản',
     'Hoài Văn Hầu',
     'Trần Quốc Toản (1267–1285) là một thiếu niên anh hùng trong cuộc kháng chiến chống Nguyên lần thứ hai, nổi tiếng với lá cờ "Phá cường địch, báo hoàng ân".',
     NULL, 'Dũng cảm, nhiệt huyết, trung thành', '1267–1285', 'Nhà Trần',
     'a0000000-0000-0000-0000-000000000002'::uuid),

    ('d0000000-0000-0000-0000-000000000003'::uuid,
     'Võ Nguyên Giáp',
     'Đại tướng',
     'Đại tướng Võ Nguyên Giáp (1911–2013) là vị tướng huyền thoại, Tổng tư lệnh Quân đội Nhân dân Việt Nam, chỉ huy chiến thắng Điện Biên Phủ 1954.',
     NULL, 'Thông minh, bình tĩnh, quyết đoán, nhân hậu', '1911–2013', 'Việt Nam Dân chủ Cộng hòa',
     'a0000000-0000-0000-0000-000000000002'::uuid),

    ('d0000000-0000-0000-0000-000000000004'::uuid,
     'Trưng Trắc',
     'Trưng Nữ Vương',
     'Trưng Trắc (?–43) là nữ vương đầu tiên trong lịch sử Việt Nam, lãnh đạo cuộc khởi nghĩa chống nhà Hán năm 40, xưng vương và đóng đô tại Mê Linh.',
     NULL, 'Kiên cường, quả cảm, yêu nước, trọng nghĩa', '? – 43', 'Lạc Việt',
     'a0000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (character_id) DO NOTHING;

-- Liên kết Character ↔ HistoricalContext (many-to-many)
INSERT INTO character_historical_context (character_id, context_id)
VALUES
    ('d0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000001'::uuid),
    ('d0000000-0000-0000-0000-000000000002'::uuid, 'b0000000-0000-0000-0000-000000000001'::uuid),
    ('d0000000-0000-0000-0000-000000000003'::uuid, 'b0000000-0000-0000-0000-000000000002'::uuid),
    ('d0000000-0000-0000-0000-000000000004'::uuid, 'b0000000-0000-0000-0000-000000000003'::uuid)
ON CONFLICT DO NOTHING;

-- ============================================================
-- 5. CHARACTER DOCUMENTS (2 docs)
-- ============================================================
INSERT INTO character_document
    (doc_id, title, content, character_id, created_by, upload_date)
VALUES
    ('e0000000-0000-0000-0000-000000000001'::uuid,
     'Hịch tướng sĩ – Trần Hưng Đạo',
     'Ta thường tới bữa quên ăn, nửa đêm vỗ gối; ruột đau như cắt, nước mắt đầm đìa; chỉ căm tức chưa xả thịt lột da, nuốt gan uống máu quân thù...',
     'd0000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW()),

    ('e0000000-0000-0000-0000-000000000002'::uuid,
     'Tiểu sử Đại tướng Võ Nguyên Giáp',
     'Võ Nguyên Giáp sinh ngày 25 tháng 8 năm 1911 tại làng An Xá, huyện Lệ Thủy, tỉnh Quảng Bình. Ông là một trong những vị tướng lỗi lạc nhất thế kỷ 20...',
     'd0000000-0000-0000-0000-000000000003'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW())
ON CONFLICT (doc_id) DO NOTHING;

-- ============================================================
-- 6. QUIZZES (2 quizzes)
-- ============================================================
INSERT INTO quiz
    (quiz_id, title, description, grade, chapter_number, chapter_title,
     era, duration_seconds, play_count, rating, context_id, created_by)
VALUES
    ('f0000000-0000-0000-0000-000000000001'::uuid,
     'Trắc nghiệm: Kháng chiến chống Nguyên Mông',
     'Kiểm tra kiến thức về ba lần kháng chiến chống quân Nguyên Mông của nhà Trần.',
     7, 1, 'Nhà Trần và cuộc kháng chiến chống Nguyên',
     'MEDIEVAL', 600, 0, 0.0,
     'b0000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid),

    ('f0000000-0000-0000-0000-000000000002'::uuid,
     'Trắc nghiệm: Chiến thắng Điện Biên Phủ',
     'Tìm hiểu về chiến dịch lịch sử Điện Biên Phủ năm 1954.',
     9, 5, 'Kháng chiến chống Pháp',
     'CONTEMPORARY', 900, 0, 0.0,
     'b0000000-0000-0000-0000-000000000002'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid)
ON CONFLICT (quiz_id) DO NOTHING;

-- ============================================================
-- 7. QUESTIONS (4 câu hỏi, mỗi quiz 2 câu)
-- ============================================================
INSERT INTO question
    (question_id, content, options, correct_answer, order_index, explanation, quiz_id)
VALUES
    -- Quiz 1: Kháng chiến chống Nguyên Mông
    ('f1000000-0000-0000-0000-000000000001'::uuid,
     'Ai là Tổng chỉ huy quân đội nhà Trần trong cuộc kháng chiến chống Nguyên Mông lần thứ 2 và 3?',
     '["Trần Thái Tông","Trần Hưng Đạo","Trần Nhân Tông","Trần Quốc Toản"]',
     1, 1,
     'Trần Hưng Đạo (Trần Quốc Tuấn) được phong làm Quốc công Tiết chế, tổng chỉ huy quân đội.',
     'f0000000-0000-0000-0000-000000000001'::uuid),

    ('f1000000-0000-0000-0000-000000000002'::uuid,
     'Trận Bạch Đằng năm 1288 sử dụng chiến thuật gì nổi tiếng?',
     '["Phục kích trên bộ","Cọc ngầm trên sông","Hỏa công","Vây thành"]',
     1, 2,
     'Trần Hưng Đạo đã cho đóng cọc nhọn bịt sắt xuống lòng sông Bạch Đằng, lợi dụng thủy triều để tiêu diệt đoàn thuyền địch.',
     'f0000000-0000-0000-0000-000000000001'::uuid),

    -- Quiz 2: Chiến thắng ĐBP
    ('f1000000-0000-0000-0000-000000000003'::uuid,
     'Chiến dịch Điện Biên Phủ diễn ra trong bao nhiêu ngày đêm?',
     '["45 ngày đêm","56 ngày đêm","60 ngày đêm","75 ngày đêm"]',
     1, 1,
     'Chiến dịch Điện Biên Phủ kéo dài 56 ngày đêm, từ 13/3 đến 7/5/1954.',
     'f0000000-0000-0000-0000-000000000002'::uuid),

    ('f1000000-0000-0000-0000-000000000004'::uuid,
     'Ai là Tổng tư lệnh chiến dịch Điện Biên Phủ phía Việt Nam?',
     '["Hồ Chí Minh","Phạm Văn Đồng","Võ Nguyên Giáp","Nguyễn Chí Thanh"]',
     2, 2,
     'Đại tướng Võ Nguyên Giáp là Tổng tư lệnh trực tiếp chỉ huy chiến dịch Điện Biên Phủ.',
     'f0000000-0000-0000-0000-000000000002'::uuid)
ON CONFLICT (question_id) DO NOTHING;

-- ============================================================
-- 8. CHAT SESSIONS (2 sessions)
-- ============================================================
INSERT INTO chat_session
    (session_id, uid, character_id, context_id, title, last_message_at, create_date)
VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     'd0000000-0000-0000-0000-000000000001'::uuid,
     'b0000000-0000-0000-0000-000000000001'::uuid,
     'Trò chuyện với Trần Hưng Đạo',
     NOW(), NOW()),

    ('10000000-0000-0000-0000-000000000002'::uuid,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     'd0000000-0000-0000-0000-000000000003'::uuid,
     'b0000000-0000-0000-0000-000000000002'::uuid,
     'Trò chuyện với Đại tướng Võ Nguyên Giáp',
     NOW(), NOW())
ON CONFLICT (session_id) DO NOTHING;

-- ============================================================
-- 9. MESSAGES (4 messages, mỗi session 2 tin)
-- ============================================================
INSERT INTO message
    (message_id, content, is_from_ai, role, session_id, "timestamp", suggested_questions)
VALUES
    -- Session 1: User hỏi Trần Hưng Đạo
    ('11000000-0000-0000-0000-000000000001'::uuid,
     'Chào Hưng Đạo Vương, ngài có thể kể về trận Bạch Đằng không ạ?',
     false, 'USER',
     '10000000-0000-0000-0000-000000000001'::uuid,
     NOW(), NULL),

    ('11000000-0000-0000-0000-000000000002'::uuid,
     'Ta rất vui khi hậu thế quan tâm đến lịch sử dân tộc! Trận Bạch Đằng năm 1288 là trận đánh quyết định trong cuộc kháng chiến lần thứ ba chống quân Nguyên...',
     true, 'ASSISTANT',
     '10000000-0000-0000-0000-000000000001'::uuid,
     NOW(),
     '["Chiến thuật cọc ngầm được chuẩn bị như thế nào?","Quân Nguyên có bao nhiêu thuyền chiến?","Sau trận Bạch Đằng, nhà Nguyên có xâm lược lại không?"]'),

    -- Session 2: User hỏi Võ Nguyên Giáp
    ('11000000-0000-0000-0000-000000000003'::uuid,
     'Thưa Đại tướng, quyết định chuyển từ "đánh nhanh thắng nhanh" sang "đánh chắc tiến chắc" được đưa ra như thế nào?',
     false, 'USER',
     '10000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NULL),

    ('11000000-0000-0000-0000-000000000004'::uuid,
     'Đó là quyết định khó khăn nhất trong đời chỉ huy của tôi. Sau khi phân tích kỹ tình hình, tôi nhận thấy phương án "đánh nhanh thắng nhanh" tiềm ẩn nhiều rủi ro...',
     true, 'ASSISTANT',
     '10000000-0000-0000-0000-000000000002'::uuid,
     NOW(),
     '["Quân Pháp phòng thủ Điện Biên Phủ mạnh cỡ nào?","Vai trò của dân công trong chiến dịch?","Cảm xúc của Đại tướng khi chiến thắng?"]')
ON CONFLICT (message_id) DO NOTHING;

-- ============================================================
-- 10. QUIZ RESULTS (2 results: Customer làm 2 quiz)
-- ============================================================
INSERT INTO quiz_result
    (result_id, score, duration_seconds, uid, quiz_id, taken_date)
VALUES
    ('12000000-0000-0000-0000-000000000001'::uuid,
     2, 180,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     'f0000000-0000-0000-0000-000000000001'::uuid,
     NOW()),

    ('12000000-0000-0000-0000-000000000002'::uuid,
     1, 420,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     'f0000000-0000-0000-0000-000000000002'::uuid,
     NOW())
ON CONFLICT (result_id) DO NOTHING;

-- ============================================================
-- 11. QUIZ ANSWER DETAILS (4 answers)
-- ============================================================
INSERT INTO quiz_answer_detail
    (detail_id, selected_option, is_correct, result_id, question_id)
VALUES
    -- Result 1: Quiz Nguyên Mông – 2/2 đúng
    ('13000000-0000-0000-0000-000000000001'::uuid,
     'Trần Hưng Đạo', true,
     '12000000-0000-0000-0000-000000000001'::uuid,
     'f1000000-0000-0000-0000-000000000001'::uuid),

    ('13000000-0000-0000-0000-000000000002'::uuid,
     'Cọc ngầm trên sông', true,
     '12000000-0000-0000-0000-000000000001'::uuid,
     'f1000000-0000-0000-0000-000000000002'::uuid),

    -- Result 2: Quiz ĐBP – 1/2 đúng
    ('13000000-0000-0000-0000-000000000003'::uuid,
     '56 ngày đêm', true,
     '12000000-0000-0000-0000-000000000002'::uuid,
     'f1000000-0000-0000-0000-000000000003'::uuid),

    ('13000000-0000-0000-0000-000000000004'::uuid,
     'Hồ Chí Minh', false,
     '12000000-0000-0000-0000-000000000002'::uuid,
     'f1000000-0000-0000-0000-000000000004'::uuid)
ON CONFLICT (detail_id) DO NOTHING;

-- ============================================================
-- 12. QUIZ SESSIONS (2 sessions: 1 submitted, 1 in-progress)
-- ============================================================
INSERT INTO quiz_session
    (session_id, quiz_id, uid, started_at, expires_at, is_submitted)
VALUES
    -- Session đã submit
    ('14000000-0000-0000-0000-000000000001'::uuid,
     'f0000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     NOW() - INTERVAL '1 hour', NOW() - INTERVAL '50 minutes', true),

    -- Session đang làm (chưa hết hạn)
    ('14000000-0000-0000-0000-000000000002'::uuid,
     'f0000000-0000-0000-0000-000000000002'::uuid,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     NOW(), NOW() + INTERVAL '15 minutes', false)
ON CONFLICT (session_id) DO NOTHING;
