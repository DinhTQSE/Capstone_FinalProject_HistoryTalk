-- Seed data for the clean ERD baseline.
-- Source: C:\Users\trand\Downloads\seed_data.sql, transformed to the current Flyway schema.
-- No payment/tier/order/transaction/quiz seed data in this pass.
-- Legacy source mappings applied:
--   STAFF -> CONTENT_ADMIN
--   ADMIN -> SYSTEM_ADMIN
--   before_tcn -> is_bc
--   is_draft -> inverse of is_published
--   character_historical_context -> context_character_mapping
--   uploaded_date/updated_date -> uploaded_at/updated_at


-- 1) Users

INSERT INTO historical_schema."user" (
    uid,
    user_name,
    email,
    password,
    role,
    token,
    last_active_date,
    is_active,
    created_at,
    updated_at,
    deleted_at
) VALUES
    ('53ae6ae3-fdcd-4e41-bcc1-711989f5da7c', 'pthaodotcom', 'phuongthao2005daodao@gmail.com', '$2a$10$5TGk1cDsUi.8iRGL9U0x4Ors2..K0/MNfOB.bTxWULWikIlCWC1XK', 'CUSTOMER', 0, NULL, TRUE, '2026-03-21 13:01:16.500784', NULL, NULL),
    ('687b57fe-865c-496a-83b3-f83690885dd1', 'STAFF1', 'user@historytalk.com', '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy', 'CONTENT_ADMIN', 0, NULL, TRUE, '2026-03-21 13:01:16.500784', NULL, NULL),
    ('8e4cdce8-0c04-4e65-8b86-9bf3b14f93fb', 'thanh', 'thanh@gmail.com', '$2a$10$BvCrkFaXf8d2LKMkKzv5yOul5cv1cDYcMa.MSpJl0xsyzJpS9BVBC', 'CUSTOMER', 0, NULL, TRUE, '2026-03-21 13:01:16.500784', NULL, NULL),
    ('a0000000-0000-0000-0000-000000000001', 'ADMIN1', 'admin@historytalk.com', '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy', 'SYSTEM_ADMIN', 0, NULL, TRUE, '2026-03-21 13:01:16.500784', NULL, NULL),
    ('a0000000-0000-0000-0000-000000000002', 'STAFF_DEMO', 'staff@historytalk.com', '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy', 'CONTENT_ADMIN', 0, NULL, TRUE, '2026-03-21 13:01:16.500784', NULL, NULL),
    ('a0000000-0000-0000-0000-000000000003', 'CUSTOMER_DEMO', 'demo@historytalk.com', '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy', 'CUSTOMER', 0, NULL, FALSE, '2026-03-21 13:01:16.500784', NULL, '2026-03-23 09:42:13.589635'),
    ('ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'CUSTOMER1', 'customer@historytalk.com', '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy', 'CUSTOMER', 0, NULL, TRUE, '2026-03-21 13:01:16.500784', NULL, NULL),
    ('d9ce19a9-db7b-40a3-84fb-2088714d7647', 'Bao', 'doquocbao2009@gmail.com', '$2a$10$BAFpewqNI//a2rHmRiewHO0Jk1wv7f9DgS79uto6iWdWPZjKJky/.', 'CONTENT_ADMIN', 0, NULL, TRUE, '2026-03-21 13:01:16.500784', NULL, NULL),
    ('f25f568a-79fa-4c8b-b56c-069d9bc12eeb', 'Dinh', 'nguyensoi0966622100@gmail.com', '$2a$10$/8p3jqZJn4CYcKktjWqDbeh1aBrHCrRxzdfREB94siV.ph/vj5uzi', 'CUSTOMER', 0, NULL, TRUE, '2026-03-21 13:01:16.500784', NULL, NULL)
ON CONFLICT (email) DO UPDATE
SET uid = EXCLUDED.uid,
    user_name = EXCLUDED.user_name,
    password = EXCLUDED.password,
    role = EXCLUDED.role,
    token = EXCLUDED.token,
    last_active_date = EXCLUDED.last_active_date,
    is_active = EXCLUDED.is_active,
    updated_at = EXCLUDED.updated_at,
    deleted_at = EXCLUDED.deleted_at;

-- 2) Historical contexts

INSERT INTO historical_schema.historical_context (
    context_id,
    created_by,
    name,
    description,
    era,
    category,
    year,
    start_year,
    end_year,
    is_bc,
    location,
    image_url,
    video_url,
    is_published,
    is_active,
    created_at,
    updated_at,
    deleted_at
) VALUES
    ('1b4f0e79-ae50-404e-a92f-725c1c93b818', 'd9ce19a9-db7b-40a3-84fb-2088714d7647', 'string', 'newdescription', 'ANCIENT', 'WAR', 0, 0, 0, TRUE, 'string', 'string', 'string', TRUE, FALSE, '2026-03-23 22:15:31.916441', '2026-03-25 09:39:24.423738', '2026-03-25 09:39:23.20239'),
    ('37c9e97c-0233-46b4-ae99-00047b897b51', '687b57fe-865c-496a-83b3-f83690885dd1', 'VoDongDucKHai', 'testtesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttest', 'ANCIENT', 'WAR', 0, 0, 0, TRUE, 'string', 'string', 'string', TRUE, FALSE, '2026-03-24 12:37:18.377663', '2026-03-25 09:39:29.834221', '2026-03-25 09:39:29.064405'),
    ('6d132b53-56c5-4d23-8048-9926e701c7f5', '687b57fe-865c-496a-83b3-f83690885dd1', 'Trận Rạch Gầm – Xoài Mút2', 'Trận chiến năm 1785 khi quân Tây Sơn do Nguyễn Huệ chỉ huy đánh bại quân Xiêm trên sông Tiền, bảo vệ chủ quyền của Đại Việt.', 'MEDIEVAL', 'WAR', 1785, 1785, 1785, FALSE, 'Rạch Gầm – Xoài Mút, Tiền Giang, Việt Nam', 'https://file3.qdnd.vn/data/images/5/2023/06/28/tranhang/a3.jpg', 'https://www.youtube.com/watch?v=wREgKak9V9I', TRUE, TRUE, '2026-03-25 03:07:30.627063', '2026-03-26 12:22:38.615479', NULL),
    ('a63aac83-feff-4f58-99ce-4093b5e9e403', '687b57fe-865c-496a-83b3-f83690885dd1', 'Trận Rạch Gầm – Xoài Mút', 'Trận chiến năm 1785 khi quân Tây Sơn do Nguyễn Huệ chỉ huy đánh bại quân Xiêm trên sông Tiền, bảo vệ chủ quyền của Đại Việt.', 'MODERN', 'WAR', 1785, 1785, 1785, FALSE, 'Rạch Gầm – Xoài Mút, Tiền Giang, Việt Nam', 'https://file3.qdnd.vn/data/images/5/2023/06/28/tranhang/a3.jpg', 'https://www.youtube.com/watch?v=wREgKak9V9I', TRUE, FALSE, '2026-03-24 12:56:12.62433', '2026-03-25 03:03:04.263366', '2026-03-25 03:03:03.478602'),
    ('b0000000-0000-0000-0000-000000000001', 'd9ce19a9-db7b-40a3-84fb-2088714d7647', 'Kháng chiến chống quân Nguyên Mông', 'Ba lần kháng chiến chống quân Nguyên Mông (1258, 1285, 1288) dưới triều đại nhà Trần là một trong những trang sử hào hùng nhất của dân tộc Việt Nam.', 'ANCIENT', 'WAR', 1258, 1258, 1288, FALSE, 'Đại Việt', 'https://photo.znews.vn/w660/Uploaded/mdf_nsozxd/2019_12_26/10.jpg', 'https://www.youtube.com/watch?v=AgcOKuAI-C8', TRUE, TRUE, '2026-03-21 13:01:16.500784', '2026-03-24 14:32:28.709441', NULL),
    ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002', 'Chiến thắng Điện Biên Phủ 1954', 'Chiến dịch Điện Biên Phủ (13/3 – 7/5/1954) là trận đánh quyết định kết thúc cuộc kháng chiến chống thực dân Pháp, mở ra kỷ nguyên mới cho dân tộc Việt Nam.', 'CONTEMPORARY', 'WAR', 1954, 1954, 1954, FALSE, 'Điện Biên Phủ, Tây Bắc Việt Nam', 'https://cdn.thuvienphapluat.vn//phap-luat/2022-2/TTL/280425/chien-thang-%C4%91bp-1.jpg', 'https://www.youtube.com/watch?v=CD8sKixEDsI&t=2s', TRUE, TRUE, '2026-03-21 13:01:16.500784', '2026-03-24 14:39:10.63008', NULL),
    ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001', 'Khởi nghĩa Hai Bà Trưng', 'Cuộc khởi nghĩa của Hai Bà Trưng (năm 40) là cuộc khởi nghĩa đầu tiên trong lịch sử Việt Nam chống lại ách đô hộ của nhà Hán, giành lại độc lập trong 3 năm.', 'ANCIENT', 'WAR', 40, 40, 43, FALSE, 'Mê Linh, Giao Chỉ', NULL, NULL, TRUE, FALSE, '2026-03-21 13:01:16.500784', '2026-03-25 09:40:14.893888', '2026-03-25 09:40:13.656823'),
    ('d99b373f-dde4-49e8-8c93-1f31d6e8b5e7', '687b57fe-865c-496a-83b3-f83690885dd1', 'Trận Bạch Đằng', 'Trận chiến năm 938 nơi Ngô Quyền đánh bại quân Nam Hán trên sông Bạch Đằng bằng chiến thuật cọc gỗ dưới lòng sông, mở ra thời kỳ độc lập lâu dài cho Việt Nam.', 'MEDIEVAL', 'WAR', 938, 938, 939, FALSE, 'Sông Bạch Đằng, Quảng Ninh - Hải Phòng', 'https://bachdanggiang.vn/wp-content/uploads/2019/12/su-kienls3-1.png', 'https://www.youtube.com/watch?v=ZPIUuLtRUFY&t=4s', TRUE, TRUE, '2026-03-24 18:26:00.401582', '2026-05-21 09:47:31.773958', NULL)
ON CONFLICT (context_id) DO UPDATE
SET created_by = EXCLUDED.created_by,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    era = EXCLUDED.era,
    category = EXCLUDED.category,
    year = EXCLUDED.year,
    start_year = EXCLUDED.start_year,
    end_year = EXCLUDED.end_year,
    is_bc = EXCLUDED.is_bc,
    location = EXCLUDED.location,
    image_url = EXCLUDED.image_url,
    video_url = EXCLUDED.video_url,
    is_published = EXCLUDED.is_published,
    is_active = EXCLUDED.is_active,
    updated_at = EXCLUDED.updated_at,
    deleted_at = EXCLUDED.deleted_at;

-- 3) Characters

INSERT INTO historical_schema."character" (
    character_id,
    created_by,
    name,
    title,
    background,
    image_url,
    born_date,
    death_date,
    personality,
    is_published,
    is_active,
    created_at,
    updated_at,
    deleted_at
) VALUES
    ('1ad59495-ba3d-45c2-af63-5b2cd5777568', '687b57fe-865c-496a-83b3-f83690885dd1', 'Nguyễn Huệ', 'Danh tướng phong trào Tây Sơn', 'Nguyễn Huệ là vị tướng kiệt xuất của phong trào Tây Sơn. Ông đã chỉ huy quân Tây Sơn đánh bại quân Xiêm trong trận Rạch Gầm – Xoài Mút năm 1785, một trong những chiến thắng lớn trong lịch sử quân sự Việt Nam.', 'https://bna.1cdn.vn/2018/01/05/uploaded-thanhngabna-2018_01_06-_quang_trung729475_612018.jpg', '1753-01-01', '1792-01-01', 'Mưu lược, quyết đoán, thiên tài quân sự và có khả năng chỉ huy xuất sắc.', FALSE, TRUE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', NULL),
    ('3373d7c8-e748-49a1-b126-4ef871b77419', 'd9ce19a9-db7b-40a3-84fb-2088714d7647', 'string', 'string', 'string', 'string', NULL, NULL, 'string', TRUE, FALSE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', '2026-03-25 09:39:23.20239'),
    ('55ae763f-2c8f-4918-bf31-b43566c3c355', '687b57fe-865c-496a-83b3-f83690885dd1', 'Ngô Quyền', 'Vị vua khai sáng thời kỳ độc lập', 'Ngô Quyền là người chỉ huy trận Bạch Đằng năm 938, sử dụng chiến thuật cọc gỗ dưới lòng sông để đánh bại quân Nam Hán.', 'https://cdn.eva.vn/upload/4-2025/images/lienntt1/quiz240x160/1763541682-923-thumbnail-width679height453.jpg', '0898-01-01', '0944-01-01', 'Quyết đoán, sáng tạo và yêu nước.', TRUE, TRUE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', NULL),
    ('648348b7-7e45-459b-bf0b-22173cb3241c', '687b57fe-865c-496a-83b3-f83690885dd1', 'VoDongDucKhai', 'test', 'test', 'string', NULL, NULL, 'string', TRUE, FALSE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', '2026-03-25 09:39:29.064405'),
    ('bf5f5ad9-8b4e-49e8-8d77-398e48ded1f3', '687b57fe-865c-496a-83b3-f83690885dd1', 'Ngô Quyền', 'Tiết Độ sứ', 'bối cảnh', 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRjG0iT2CHwqjrnnCdZSP2jEofAa5TZTfc7RCHi9STj_O9rlbJpx16xlBfs1T0s4EuhZLpO0KAi4JrykeG6rFlQtMfU2t98QsPTpfb7rIc&s=10', '2004-01-01', NULL, 'Vui vẻ', TRUE, FALSE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', '2026-03-25 09:40:13.656823'),
    ('c574276d-4063-436c-a5c1-6b9565c221b5', '687b57fe-865c-496a-83b3-f83690885dd1', 'Nguyễn Huệ', 'Danh tướng phong trào Tây Sơn', 'Nguyễn Huệ là vị tướng kiệt xuất của phong trào Tây Sơn. Ông đã chỉ huy quân Tây Sơn đánh bại quân Xiêm trong trận Rạch Gầm – Xoài Mút năm 1785, một trong những chiến thắng lớn trong lịch sử quân sự Việt Nam.', 'https://bna.1cdn.vn/2018/01/05/uploaded-thanhngabna-2018_01_06-_quang_trung729475_612018.jpg', '1753-01-01', '1792-01-01', 'Mưu lược, quyết đoán, thiên tài quân sự và có khả năng chỉ huy xuất sắc.', TRUE, FALSE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', '2026-03-25 03:03:03.478602'),
    ('d0000000-0000-0000-0000-000000000001', 'd9ce19a9-db7b-40a3-84fb-2088714d7647', 'Trần Hưng Đạo', 'Quốc công Tiết chế', 'Trần Quốc Tuấn (1228–1300), tước Hưng Đạo Vương, là vị tướng tài ba đã ba lần đánh bại quân Nguyên-Mông xâm lược Đại Việt.', 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQkDBZw0y1hpCDWLH4ZJOddL9fxoN-aQglxmw&s', '1228-01-01', '1300-01-01', 'Cương nghị, mưu lược, yêu nước, khoan dung', TRUE, TRUE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', NULL),
    ('d0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002', 'Trần Quốc Toản', 'Hoài Văn Hầu', 'Trần Quốc Toản (1267–1285) là một thiếu niên anh hùng trong cuộc kháng chiến chống Nguyên lần thứ hai, nổi tiếng với lá cờ "Phá cường địch, báo hoàng ân".', 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRwlvPyNaefXuqykWG-j-o62GGdzx8-Up2VrQ&s', '1267-01-01', '1285-01-01', 'Dũng cảm, nhiệt huyết, trung thành', TRUE, TRUE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', NULL),
    ('d0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000002', 'Võ Nguyên Giáp', 'Đại tướng', 'Đại tướng Võ Nguyên Giáp (1911–2013) là vị tướng huyền thoại, Tổng tư lệnh Quân đội Nhân dân Việt Nam, chỉ huy chiến thắng Điện Biên Phủ 1954.', 'https://vstatic.vietnam.vn/vietnam/resource/IMAGE/2025/8/25/0bfa178a4735448cba0ce9236438024b', '1911-01-01', '2013-01-01', 'Thông minh, bình tĩnh, quyết đoán, nhân hậu', TRUE, TRUE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', NULL),
    ('d0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000001', 'Trưng Trắc', 'Trưng Nữ Vương', 'Trưng Trắc (?–43) là nữ vương đầu tiên trong lịch sử Việt Nam, lãnh đạo cuộc khởi nghĩa chống nhà Hán năm 40, xưng vương và đóng đô tại Mê Linh.', NULL, NULL, NULL, 'Kiên cường, quả cảm, yêu nước, trọng nghĩa', TRUE, FALSE, '2026-03-21 13:01:16.500784', '2026-03-21 13:01:16.500784', '2026-03-25 09:40:13.656823')
ON CONFLICT (character_id) DO UPDATE
SET created_by = EXCLUDED.created_by,
    name = EXCLUDED.name,
    title = EXCLUDED.title,
    background = EXCLUDED.background,
    image_url = EXCLUDED.image_url,
    born_date = EXCLUDED.born_date,
    death_date = EXCLUDED.death_date,
    personality = EXCLUDED.personality,
    is_published = EXCLUDED.is_published,
    is_active = EXCLUDED.is_active,
    updated_at = EXCLUDED.updated_at,
    deleted_at = EXCLUDED.deleted_at;

-- 4) Context-character mappings

INSERT INTO historical_schema.context_character_mapping (
    context_id,
    character_id
) VALUES
    ('6d132b53-56c5-4d23-8048-9926e701c7f5', '1ad59495-ba3d-45c2-af63-5b2cd5777568'),
    ('1b4f0e79-ae50-404e-a92f-725c1c93b818', '3373d7c8-e748-49a1-b126-4ef871b77419'),
    ('d99b373f-dde4-49e8-8c93-1f31d6e8b5e7', '55ae763f-2c8f-4918-bf31-b43566c3c355'),
    ('37c9e97c-0233-46b4-ae99-00047b897b51', '648348b7-7e45-459b-bf0b-22173cb3241c'),
    ('b0000000-0000-0000-0000-000000000003', 'bf5f5ad9-8b4e-49e8-8d77-398e48ded1f3'),
    ('a63aac83-feff-4f58-99ce-4093b5e9e403', 'c574276d-4063-436c-a5c1-6b9565c221b5'),
    ('b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001'),
    ('b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002'),
    ('b0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003'),
    ('b0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004')
ON CONFLICT (context_id, character_id) DO NOTHING;

-- 5) Documents

INSERT INTO historical_schema.document (
    doc_id,
    uploaded_by,
    entity_id,
    entity_type,
    title,
    file_url,
    content,
    document_type,
    is_active,
    uploaded_at,
    updated_at,
    deleted_at
) VALUES
    ('04337983-60de-445e-8074-787860f1cc4e', 'd9ce19a9-db7b-40a3-84fb-2088714d7647', '1b4f0e79-ae50-404e-a92f-725c1c93b818', 'CONTEXT', 'string', NULL, 'stringstri', 'TEXT', FALSE, '2026-03-23 23:16:04.082195', '2026-03-25 09:39:24.607753', '2026-03-25 09:39:23.20239'),
    ('c0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001', 'CONTEXT', 'string', NULL, 'stringstri', 'MARKDOWN', TRUE, '2026-03-21 13:01:16.500784', '2026-03-23 23:14:20.167158', NULL),
    ('c0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', 'CONTEXT', 'Chiến dịch Điện Biên Phủ – 56 ngày đêm', NULL, 'Chiến dịch diễn ra trong 56 ngày đêm, chia làm 3 đợt tấn công. Đợt 1 (13-17/3): tiêu diệt cứ điểm Him Lam và Độc Lập...', 'TEXT', FALSE, '2026-03-21 13:01:16.500784', '2026-03-24 14:39:10.732034', '2026-03-24 14:39:09.629352'),
    ('c0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000003', 'CONTEXT', 'Hai Bà Trưng – Nữ vương đầu tiên', NULL, 'Trưng Trắc và Trưng Nhị là hai chị em, con gái Lạc tướng huyện Mê Linh. Năm 40, hai bà phất cờ khởi nghĩa tại cửa sông Hát...', 'TEXT', FALSE, '2026-03-21 13:01:16.500784', '2026-03-25 09:40:15.008361', '2026-03-25 09:40:13.656823')
ON CONFLICT (doc_id) DO UPDATE
SET uploaded_by = EXCLUDED.uploaded_by,
    entity_id = EXCLUDED.entity_id,
    entity_type = EXCLUDED.entity_type,
    title = EXCLUDED.title,
    file_url = EXCLUDED.file_url,
    content = EXCLUDED.content,
    document_type = EXCLUDED.document_type,
    is_active = EXCLUDED.is_active,
    uploaded_at = EXCLUDED.uploaded_at,
    updated_at = EXCLUDED.updated_at,
    deleted_at = EXCLUDED.deleted_at;

-- 6) Chat sessions

INSERT INTO historical_schema.chat_session (
    session_id,
    uid,
    context_id,
    character_id,
    title,
    last_message_at,
    is_active,
    created_at,
    updated_at,
    deleted_at
) VALUES
    ('019fa516-c23a-4981-a71f-800aa112efd3', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', '', NULL, TRUE, '2026-03-25 03:10:05.910755', NULL, NULL),
    ('0faa073e-6261-431d-86b1-b496f07b1412', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002', 'Trần Quốc Toản chuẩn bị kháng chiến chống quân Nguyên', '2026-03-25 10:15:56.308179', TRUE, '2026-03-25 03:13:33.131857', NULL, NULL),
    ('0fc54009-0125-4d84-a2b4-4b1f2d26e12c', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002', 'Trần Quốc Toản rèn binh chống quân Nguyên', '2026-03-25 09:46:01.932914', TRUE, '2026-03-24 11:13:45.798552', NULL, NULL),
    ('10000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'Trò chuyện với Trần Hưng Đạo', '2026-03-21 13:01:16.500784', TRUE, '2026-03-21 13:01:16.500784', NULL, NULL),
    ('10000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003', 'Trò chuyện với Đại tướng Võ Nguyên Giáp', '2026-03-21 13:01:16.500784', TRUE, '2026-03-21 13:01:16.500784', NULL, NULL),
    ('222106f8-0fcf-4c65-bab8-c2a9ef1e8c3f', '687b57fe-865c-496a-83b3-f83690885dd1', 'a63aac83-feff-4f58-99ce-4093b5e9e403', 'c574276d-4063-436c-a5c1-6b9565c221b5', '', '2026-03-24 13:50:34.145848', FALSE, '2026-03-24 13:50:34.149034', NULL, '2026-03-25 03:03:03.478602'),
    ('2bdd0c3d-68e4-4c55-9d3b-4c6387ddcd8f', 'f25f568a-79fa-4c8b-b56c-069d9bc12eeb', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', '', '2026-03-25 15:04:36.007215', TRUE, '2026-03-25 15:04:36.008177', NULL, NULL),
    ('3a9b6c24-3fd9-4c50-9411-064cb2adabc5', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', '', '2026-03-25 09:41:07.529271', TRUE, '2026-03-25 09:41:07.5313', NULL, NULL),
    ('46fbc9a7-2687-499a-9118-57ff465a66e6', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'd99b373f-dde4-49e8-8c93-1f31d6e8b5e7', '55ae763f-2c8f-4918-bf31-b43566c3c355', 'Ngô Quyền và tình hình đất nước trước Bạch Đằng', '2026-03-25 09:43:38.163065', TRUE, '2026-03-25 09:39:17.919469', NULL, NULL),
    ('6d235149-b6a4-4f4a-ba4e-8f73f3db4ea1', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004', '', '2026-03-23 19:34:25.189479', FALSE, '2026-03-23 19:34:25.189992', NULL, '2026-03-25 09:40:13.656823'),
    ('6e1cfc8e-d481-4469-b0a5-6bf1fc5ddb47', '53ae6ae3-fdcd-4e41-bcc1-711989f5da7c', 'b0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003', 'Võ Nguyên Giáp: Vị tướng của dân tộc', '2026-03-26 08:07:07.217693', TRUE, '2026-03-26 08:06:16.883033', NULL, NULL),
    ('75e2b9be-47a7-4f6f-9ff6-c87e90bd9110', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004', '', '2026-03-25 05:16:52.61037', FALSE, '2026-03-25 05:16:52.635365', NULL, '2026-03-25 09:40:13.656823'),
    ('7fecb68b-efb5-475c-b8d5-94854d8286a2', 'f25f568a-79fa-4c8b-b56c-069d9bc12eeb', 'd99b373f-dde4-49e8-8c93-1f31d6e8b5e7', '55ae763f-2c8f-4918-bf31-b43566c3c355', '', '2026-03-26 09:32:09.892657', TRUE, '2026-03-26 09:32:09.893438', NULL, NULL),
    ('8ec6ad44-187e-40f3-bcdf-392dfc80c861', '687b57fe-865c-496a-83b3-f83690885dd1', 'd99b373f-dde4-49e8-8c93-1f31d6e8b5e7', '55ae763f-2c8f-4918-bf31-b43566c3c355', 'Ngô Quyền và kế sách cọc gỗ sông Bạch Đằng', '2026-03-25 04:20:23.997736', TRUE, '2026-03-24 18:28:52.555143', NULL, NULL),
    ('8ed01cc5-d7db-41d9-9e7c-aa9d940a17aa', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'd99b373f-dde4-49e8-8c93-1f31d6e8b5e7', '55ae763f-2c8f-4918-bf31-b43566c3c355', '', '2026-03-25 09:39:06.334444', TRUE, '2026-03-25 09:39:06.367475', NULL, NULL),
    ('902f6dbc-dc4a-42b6-8799-22e6a153883d', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', '', NULL, TRUE, '2026-03-25 03:09:53.074188', NULL, NULL),
    ('9778c1ce-9c99-45bc-8be7-e638fa3e8780', '687b57fe-865c-496a-83b3-f83690885dd1', 'b0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003', '', '2026-03-24 14:34:39.434608', TRUE, '2026-03-24 14:34:39.436202', NULL, NULL),
    ('97f9c21b-2cac-4bc1-bc9b-425c8ce53f77', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'Ba lần kháng chiến chống Nguyên Mông', '2026-03-24 11:06:55.647', TRUE, '2026-03-24 11:06:13.867814', NULL, NULL),
    ('9bc9b11a-61c0-4d2a-928f-8b837a41ab5b', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003', '', NULL, TRUE, '2026-05-20 16:39:10.672622', NULL, NULL),
    ('af7231d2-7fd3-42ff-b0bf-0c11a9f7f7da', '687b57fe-865c-496a-83b3-f83690885dd1', '6d132b53-56c5-4d23-8048-9926e701c7f5', '1ad59495-ba3d-45c2-af63-5b2cd5777568', '', NULL, TRUE, '2026-03-25 03:09:05.638923', NULL, NULL),
    ('b09eed76-1566-4c63-bf90-8562ce315dc2', 'd9ce19a9-db7b-40a3-84fb-2088714d7647', '1b4f0e79-ae50-404e-a92f-725c1c93b818', '3373d7c8-e748-49a1-b126-4ef871b77419', '', NULL, FALSE, '2026-03-24 10:13:58.54908', NULL, '2026-03-25 09:39:23.20239'),
    ('b41768a7-1e4d-4287-8cb8-1244dec027aa', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'b0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004', 'Trưng Trắc hy sinh giữ khí tiết dân tộc', '2026-03-25 05:19:21.522196', FALSE, '2026-03-25 05:16:58.514527', NULL, '2026-03-25 09:40:13.656823'),
    ('b525928b-1270-41af-8ebb-2594599f370b', '687b57fe-865c-496a-83b3-f83690885dd1', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002', '', '2026-03-24 14:34:10.282461', TRUE, '2026-03-24 14:34:10.285938', NULL, NULL),
    ('bfd098b0-0c78-49f4-bd9a-186c6ad86661', 'f25f568a-79fa-4c8b-b56c-069d9bc12eeb', 'b0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003', 'Tình cảm với Võ Nguyên Giáp', '2026-03-26 09:33:36.029215', TRUE, '2026-03-25 15:00:22.521908', NULL, NULL),
    ('c0000000-0000-0000-0000-000000000002', 'd9ce19a9-db7b-40a3-84fb-2088714d7647', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', NULL, NULL, TRUE, '2026-03-20 10:52:10', NULL, NULL),
    ('d6201244-1788-420f-b22a-53c9e4029d58', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', '1b4f0e79-ae50-404e-a92f-725c1c93b818', '3373d7c8-e748-49a1-b126-4ef871b77419', '', NULL, FALSE, '2026-03-24 10:22:49.551844', NULL, '2026-03-25 09:39:23.20239'),
    ('db12bbea-dd8e-4c59-9881-f7c9b3ad19d7', '8e4cdce8-0c04-4e65-8b86-9bf3b14f93fb', 'd99b373f-dde4-49e8-8c93-1f31d6e8b5e7', '55ae763f-2c8f-4918-bf31-b43566c3c355', '', NULL, TRUE, '2026-05-22 12:32:26.531664', NULL, NULL),
    ('ea46b7fa-383b-468e-b332-b47097f00d7a', 'ae2352c6-704b-4125-978f-4ef4c80c3d4e', 'd99b373f-dde4-49e8-8c93-1f31d6e8b5e7', '55ae763f-2c8f-4918-bf31-b43566c3c355', 'Ngô Quyền và chiến thắng Bạch Đằng lịch sử', '2026-03-27 12:11:09.936658', TRUE, '2026-03-25 12:37:06.393183', NULL, NULL),
    ('f362a0a2-383b-476a-9be7-4b007864018d', '53ae6ae3-fdcd-4e41-bcc1-711989f5da7c', 'd99b373f-dde4-49e8-8c93-1f31d6e8b5e7', '55ae763f-2c8f-4918-bf31-b43566c3c355', 'Ngô Quyền giới thiệu và hỏi thăm', '2026-03-26 10:00:49.110644', TRUE, '2026-03-26 08:02:08.541556', NULL, NULL),
    ('fafe2690-ccd9-4179-8a91-1ca608f1456b', '687b57fe-865c-496a-83b3-f83690885dd1', 'b0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', '', NULL, TRUE, '2026-03-24 08:48:24.261164', NULL, NULL)
ON CONFLICT (session_id) DO UPDATE
SET uid = EXCLUDED.uid,
    context_id = EXCLUDED.context_id,
    character_id = EXCLUDED.character_id,
    title = EXCLUDED.title,
    last_message_at = EXCLUDED.last_message_at,
    is_active = EXCLUDED.is_active,
    updated_at = EXCLUDED.updated_at,
    deleted_at = EXCLUDED.deleted_at;
