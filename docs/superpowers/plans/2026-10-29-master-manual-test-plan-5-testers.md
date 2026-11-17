# Kế Hoạch & Ma Trận Kiểm Thử Thủ Công Toàn Diện HistoryTalk (5 Tester & 6 Vai Trò)

> **Tài liệu kiểm thử dự án:** Nền tảng Học Lịch Sử Tương Tác HistoryTalk (SWD392)  
> **Quy mô:** 5 Tester | 6 Vai trò | 70 Màn hình web  
> **Môi trường:** `https://historytalk.online`  
> **Thời gian chiến dịch:** 4 ngày làm việc  
> **Tệp Excel đánh giá tương tác:** [`HistoryTalk_Master_Test_Matrix_5_Testers.xlsx`](file:///c:/Users/trand/OneDrive/Documents/Semester%207/SWD392/SWD392_FinalProject_Git/docs/HistoryTalk_Master_Test_Matrix_5_Testers.xlsx)

---

## I. Lịch Trình Thử Nghiệm 4 Ngày & Bàn Giao Chéo

### 1. Phân Bổ 4 Ngày Làm Việc
* **Ngày 1:**
  * **Sáng (08:00 - 11:30):** Tester 3 tạo nội dung mẫu (Nhân vật, Bối cảnh, Quiz, Map). Tester 4 tạo Trường D1, tài khoản `admin.d1@example.com`, các GV (Lan, Khoa, Hùng, Mai, Hồng), các lớp (10A1, 10A2, 10A3, 11A1, 11A2, 12A1, 12A2), 175 học sinh và cấp token.
  * **Chiều (13:30 - 17:30):** Mỗi tester chạy đường đúng (Happy Path) độc lập trong phạm vi vai trò của mình.
* **Ngày 2:**
  * Kiểm thử dữ liệu sai, trường hợp biên (Boundary), ngắt mạng giữa chừng, reload trang; đối chiếu và thực hiện 7 luồng bàn giao giữa các vai trò.
* **Ngày 3:**
  * Test chéo vòng tròn: Mỗi tester chạy lại luồng chính của tester kế tiếp (`1` $\rightarrow$ `2` $\rightarrow$ `3` $\rightarrow$ `4` $\rightarrow$ `5` $\rightarrow$ `1`).
  * Kiểm tra hiển thị trên mobile viewport `375px` và trình duyệt phụ (Firefox, Safari).
* **Ngày 4:**
  * Retest các lỗi đã được dev sửa, kiểm tra hồi quy (Regression test), chốt danh sách lỗi còn mở và nghiệm thu chất lượng (UAT Sign-off).

### 2. Danh Sách 7 Lần Bàn Giao Giữa Các Tester
1. **Tester 3 $\rightarrow$ Tester 2:** Tester 3 tạo nhân vật, bối cảnh, quiz, tài liệu mẫu $\rightarrow$ Tester 2 kiểm tra hiển thị, làm quiz và chat AI nhập vai.
2. **Tester 4 $\rightarrow$ Tester 5:** Tester 4 tạo trường, 7 lớp, 5 giáo viên, 175 học sinh $\rightarrow$ Tester 5 đăng nhập GV thấy đúng lớp, HS thấy đúng bạn cùng lớp.
3. **Tester 4 $\rightarrow$ Tester 1:** Tester 4 tạo/sửa giá/bật/tắt gói dịch vụ $\rightarrow$ Tester 1 kiểm tra phản ánh tức thì tại `/pricing`.
4. **Tester 1 $\rightarrow$ Tester 4:** Tester 1 thanh toán mua gói PayOS $\rightarrow$ Tester 4 đối soát lịch sử giao dịch tại `/staff/admin/payment/history`.
5. **Tester 5 $\rightarrow$ Tester 4:** Tester 5 (GV) gửi Ticket xin cấp thêm Token cho lớp $\rightarrow$ Tester 4 (School Admin) duyệt hoặc từ chối tại `/school/tokens`.
6. **Tester 5 $\rightarrow$ Tester 4:** Tester 5 (GV) submit nội dung lịch sử địa phương $\rightarrow$ Tester 4 kiểm tra và duyệt (ACTIVE) hoặc ẩn (INACTIVE) tại `/school/content`.
7. **Tester 4 $\rightarrow$ Tester 5:** Tester 4 duyệt nội dung hoặc khóa trường/gỡ HS $\rightarrow$ Tester 5 kiểm tra: HS chỉ thấy nội dung duyệt; khi bị khóa trường bị bật modal chặn ngay.

---

## II. Danh Mục Tài Khoản Test (Mật khẩu chung: `123456789`)

| Vai trò (Role) | Họ tên / Đối tượng | Email đăng nhập | Phân công | Nhiệm vụ chính |
| :--- | :--- | :--- | :---: | :--- |
| **SYSTEM_ADMIN** | Quản trị Hệ thống | `systemadmin@historytalk.com` | Tester 4 | Quản trị trường, tài khoản theo role, gói, doanh thu |
| **CONTENT_ADMIN** | Quản trị Nội dung | `contentadmin@historytalk.com` | Tester 3 | Bối cảnh, nhân vật 3D, quiz ngân hàng, map editor |
| **SCHOOL_ADMIN** | Quản trị Trường D1 | `admin.d1@example.com` | Tester 4 | Quản lý 7 lớp, 175 học sinh, giáo viên, token, duyệt bài |
| **TEACHER** | GV Nguyễn Thị Lan | `gv.lan.d1@example.com` | Tester 5 | Phụ trách 10A1, 10A3; soạn tư liệu riêng, giao bài, sổ điểm |
| **TEACHER** | GV Đặng Minh Khoa | `gv.khoa.d1@example.com` | Tester 5 | Phụ trách 12A1, 12A2 (Giáo viên dạy 2 lớp khối 12) |
| **TEACHER** | GV Trần Văn Hùng | `gv.hung.d1@example.com` | Tester 5 | Phụ trách 10A2; kiểm tra phân quyền lớp học |
| **TEACHER** | GV Lê Thị Mai | `gv.mai.d1@example.com` | Tester 5 | Phụ trách 11A1; kiểm tra phân quyền lớp học |
| **TEACHER** | GV Võ Thị Hồng | `gv.hong.d1@example.com` | Tester 5 | Phụ trách 11A2; kiểm tra phân quyền lớp học |
| **SCHOOL_STUDENT**| Học sinh Khối 10 | `hs.10a1.01.d1@example.com` | Tester 5 | Lớp 10A1: Trang lớp, kho tư liệu, làm bài tập & quiz |
| **SCHOOL_STUDENT**| Học sinh Khối 12 | `hs.12a1.05.d1@example.com` | Tester 5 | Lớp 12A1: Kiểm tra tư liệu khác hẳn so với khối 10 |
| **CUSTOMER (Free)**| Khách hàng Miễn phí | `customer.free@example.com` | Tester 2 | Trải nghiệm học gói Free, kiểm tra giới hạn token & tính năng |
| **CUSTOMER (Paid)**| Khách hàng Trả phí | `customer.pro@example.com` | Tester 2 | Trải nghiệm học gói Pro, quyền lợi không giới hạn, bản đồ full |
| **GUEST** | Khách vãng lai | *(Chưa có tài khoản)* | Tester 1 | Kiểm tra marketing page, luồng mua gói dẫn về login |

---

## III. Tóm Tắt Kịch Bản Kiểm Thử Theo Từng Tester

### Tester 1 — Khách, Xác thực, Thanh toán & Phân quyền Route
* **Trang Marketing:** Trang chủ, Tính năng, Bảng giá, Giới thiệu; Nút mua gói khi chưa login dẫn sang login rồi quay lại; Viewport 375px menu không tràn ngang.
* **Đăng ký & Đăng nhập:** Form validation (email tồn tại, sai định dạng, mật khẩu yếu, không khớp, bỏ trống); Brute-force & disable button; Google OAuth (thành công & cancel); Đăng nhập bằng từng vai trò về đúng trang chủ; Đăng xuất và bấm Back không xem lại trang bảo mật; Token hết hạn đưa về login không treo trang.
* **Quên mật khẩu:** Link reset dùng được 1 lần, link hết hạn báo lỗi, đổi xong mật khẩu cũ bị từ chối.
* **Hồ sơ:** Sửa thông tin, upload avatar (quá lớn >5MB, sai định dạng), hiển thị streak & gói.
* **Gói & Thanh toán PayOS:** Chọn gói sang PayOS đúng tiền; Thanh toán thành công về `/payment/success` kích hoạt gói ngay; Hủy thanh toán về `/payment/cancel`; Xem `/payment/history` đủ giao dịch; Mở trực tiếp `/payment/success` khi chưa thanh toán không tự kích hoạt gói.
* **Phân quyền Route:** Chặn triệt để các URL trái quyền (Guest, Customer, Teacher, Student, Content Admin, School Admin, System Admin).

### Tester 2 — Trải Nghiệm Học của CUSTOMER (Free vs Pro)
* **Trang chủ & Điểm danh:** Banner, tính năng dẫn đúng trang; Streak chỉ tăng 1 lần/ngày, reload không tăng; Sidebar thu gọn/mở rộng chuẩn.
* **Sự kiện & Nhân vật:** Tìm kiếm, lọc, phân trang; Chi tiết sự kiện full-screen đóng được; Model 3D tải mượt, xoay 360 độ; Lưu / bỏ lưu hiển thị đúng tại `/saved`.
* **Chat AI Nhập vai:** Nhân vật trả lời chuẩn bối cảnh; Gợi ý câu hỏi tiếp theo bấm gửi được; Hộp trích dẫn tài liệu khớp tư liệu gốc; Markdown đậm/link/danh sách hiển thị đúng; Voice chat & lip-sync theo tiếng; Stress test tin rỗng, >2000 ký tự, mất mạng khi chờ; Free hết token báo lỗi nâng cấp, Pro không bị chặn; Lịch sử chat `/chat-history`.
* **Quiz:** Làm đủ 3 loại câu (trắc nghiệm, điền khuyết, nối cặp kéo thả cả trên touch 375px); Nộp bài có cảnh báo câu chưa làm, chấm điểm chuẩn; Lịch sử làm bài tại `/grades`; Reload giữa chừng không mất bài hoặc có cảnh báo.
* **Thư viện, Bản đồ & Battle Room:** Đọc PDF tại `/library`; Bản đồ tương tác zoom/pan/layers/timeline; Battle mode thuyết minh nổi trên cùng; Phòng `/map/room/[id]` dẫn sang chat; Ghi chú học tập lưu lại sau reload.

### Tester 3 — Quản Lý Nội Dung (CONTENT_ADMIN)
* **Dashboard:** Thẻ thống kê khớp thực tế.
* **Bối cảnh lịch sử:** Tạo bối cảnh đủ trường, preview media, đính kèm PDF lưu cùng nút tạo; Validate năm bắt đầu > năm kết thúc, file lỗi; Ngắt mạng form vẫn giữ dữ liệu; Đổi PDF không mất file cũ nếu chưa chọn file mới; Đặt Map Focus; Toggle xuất bản sync qua Customer.
* **Nhân vật:** CRUD nhân vật, gắn 3D model, bối cảnh, PDF; Chat thử tại staff portal trả lời đúng tài liệu gắn và KHÔNG tính streak; Toggle xuất bản sync sang Customer.
* **Tài liệu:** Upload PDF, xem chi tiết, xóa có modal xác nhận, kiểm tra cascade khi nhân vật đang dùng.
* **Quiz & Ngân hàng:** Trình soạn 3 loại câu; Tải template CSV điền và import, báo lỗi theo dòng; Question bank sửa, xóa, gắn/gỡ quiz; Sync sang Customer.
* **Nhiệm vụ Quests:** CRUD nhiệm vụ, stats thẻ cập nhật; Tester 2 làm hành động và trigger nhận thưởng.
* **Trình chỉnh bản đồ trận đánh (`/staff/map`):** Spotlight tour chạy hết và bỏ qua được; Tạo giai đoạn, gán đối tượng, nhãn nhanh, thuyết minh; Phím tắt hoạt động; Z-index nổi trên cùng không bị sidebar che; Reload dữ liệu còn nguyên, preview giống bản học sinh.

### Tester 4 — Quản Trị Hệ Thống & Quản Trị Trường
* **System Admin (`/staff/admin/*`):** Tạo trường tự sinh mã, tạo kèm School Admin, không hiện phiếu in School Admin; Danh sách trường search/filter/pagination, 404 handler; Khóa trường chặn GV/HS toàn trường; Thẻ số liệu tài khoản khớp bảng 100%; Validate form tạo tài khoản; Khóa tài khoản real-time (thu hồi session đang active); CRUD gói dịch vụ sync tức thì bên `/pricing` của Tester 1; Đối soát doanh thu tổng gói và lịch sử PayOS.
* **School Admin (`/school/*`):** Tạo GV bắt trùng username, khóa/reset pass/xóa có xác nhận, batch actions; Import CSV học sinh có Preview Grid cho inline edit từng ô, highlight dòng lỗi, kiểm tra file 500 dòng/rỗng/trùng mã, khóa/reset pass HS; Tạo lớp bỏ lựa chọn "Tạm ngưng", thêm HS vào lớp qua file cập nhật sĩ số; Đổi GV phụ trách bàn giao Tester 5; Chặn cấp token quá hạn mức trường, duyệt/từ chối ticket GV; Phê duyệt/ẩn nội dung địa phương của GV; Dashboard trường khớp số liệu các trang con.

### Tester 5 — Giáo Viên & Học Sinh của Trường
* **Lưu ý đặc thù:** Đã bỏ/ẩn hoàn toàn tính năng Import CSV của giáo viên.
* **Teacher:** `/teaching` hiển thị đúng lớp của tôi, GV chưa có lớp thấy thông báo; Chi tiết lớp sĩ số/tiến độ, `/classes/materials` tư liệu, `/map/class` bản đồ lớp; Soạn tư liệu riêng (bối cảnh, nhân vật, quiz riêng 3 loại câu); Chế độ công khai (toàn trường vs chỉ lớp mình); Thùng rác, khôi phục & xóa vĩnh viễn; Giao bài đọc (min thời gian), chat AI (min messages), quiz; Validate hạn nộp quá khứ, miễn bài cho HS, xem sổ điểm và tiến độ; Gửi ticket xin token lớp lên School Admin.
* **School Student:** `/classes` thấy đúng lớp và tên GV; `/assignments` đủ bài, bài được miễn hiện rõ; Làm bài đọc (đủ/thiếu thời gian), bài chat AI (đếm tin nhắn, hết token lớp), bài quiz (chấm điểm khớp GV, xử lý nộp trễ); `/grades` và `/classes/materials`; Điểm danh streak cho HS trường; Mất quyền truy cập ngay khi trường bị khóa hoặc bị gỡ khỏi lớp.

---

## IV. Tiêu Chuẩn Nghiệm Thu Hoàn Thành (Definition of Done - DoD)
1. **100% Test Cases** trên file Excel đã được đánh dấu trạng thái (`Passed`, `Failed`, `Blocked`).
2. **0 lỗi Nghiêm trọng (Critical Blocker)** còn mở vào cuối ngày 4.
3. **Mọi lỗi mức Cao** đều được tạo bug ticket, có người nhận và deadline sửa.
4. **Cả 7 lần bàn giao chéo** trong sơ đồ được 2 bên xác nhận hoàn tất.
5. **Ma trận phân quyền Route RBAC** đạt tỷ lệ bảo vệ 100%.
