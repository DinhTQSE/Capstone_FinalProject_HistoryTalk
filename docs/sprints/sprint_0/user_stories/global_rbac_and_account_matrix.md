# 📋 USER STORIES: MA TRẬN PHÂN QUYỀN TOÀN CỤC & KIỂM SOÁT TRUY CẬP (GLOBAL RBAC & ACCESS CONTROL)
> **Sprint:** Sprint 0 (13/10/2026 – 19/10/2026)  
> **Phân hệ:** Kiểm soát Truy cập & Phân quyền Toàn hệ thống (Global Access Control & RBAC Matrix)  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Người phụ trách | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP0-04`** | Đặc tả & Chuẩn hóa Ma trận Phân quyền 27 tính năng cho 6 Vai trò | Toàn bộ 6 Vai trò | 🟢 Khải (KhaiVDD) | Toàn bộ API Endpoints |
| **`US-SP0-05`** | Thiết lập Bộ lọc Phân quyền Controller & Prefix Endpoint Security | System/School Admin, Teachers | 🟢 Khải (KhaiVDD) | `SecurityConfig`, Controllers |

---

## 📊 MA TRẬN PHÂN QUYỀN TOÀN HỆ THỐNG (RBAC MATRIX)

> **Ký hiệu:**  
> ✅ : Có toàn quyền Thao tác (CRUD / Quản lý / Tạo mới / Chỉnh sửa / Xóa)  
> 👁️ : Chỉ có quyền Xem dữ liệu (Read-only / View)  
> ❌ : Không có quyền truy cập (Bị chặn 403 Forbidden)  

| STT | Phân hệ / Tính năng Nghiệp vụ | System Admin | Content Admin | School Admin | Teacher (Giáo viên) | School Student (Học sinh) | Customer (Free / Premium) | Ghi chú Phân quyền / Scope |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **I** | **XÁC THỰC & QUẢN LÝ TÀI KHOẢN** | | | | | | | |
| 1 | Quản lý tài khoản System / Content Admin | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | Chỉ Super Admin có quyền tạo/quản trị |
| 2 | Cấp / Quản lý tài khoản School Admin | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | Tạo tài khoản đại diện trường B2B |
| 3 | Tạo & Quản lý tài khoản Giáo viên (Teacher) | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | Giới hạn trong trường của School Admin |
| 4 | Tạo & Import hàng loạt tài khoản Học sinh | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | Import file Excel/CSV học sinh toàn trường |
| 5 | Đăng ký cá nhân & Nâng cấp tài khoản B2C | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Luồng tự do cho người dùng cá nhân |
| **II** | **QUẢN LÝ LỚP HỌC (SaaS B2B)** | | | | | | | |
| 6 | CRUD Lớp học Toàn trường | 👁️ | ❌ | ✅ | ❌ | ❌ | ❌ | School Admin tạo lớp, System Admin xem hỗ trợ |
| 7 | Thêm / Chuyển / Xóa Học sinh khỏi Lớp | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | GV chỉ thao tác trong lớp mình phụ trách |
| 8 | Xem danh sách Lớp học mình tham gia | ❌ | ❌ | ❌ | 👁️ | 👁️ | ❌ | GV & HS chỉ xem lớp của chính mình |
| **III** | **QUẢN LÝ NỘI DUNG HỌC THUẬT CHUNG** | | | | | | | |
| 9 | CRUD Context, Character, Document dùng chung | 👁️ | ✅ | ❌ | ❌ | ❌ | ❌ | Content Admin là người biên soạn chính |
| 10 | Duyệt trạng thái (Draft, Publish, Inactive, Trash) | 👁️ | ✅ | ❌ | ❌ | ❌ | ❌ | Duyệt để phát hành cho toàn quốc học |
| 11 | Xem nội dung học thuật chung (Published) | ✅ | ✅ | 👁️ | 👁️ | 👁️ | 👁️ | Mọi đối tượng đều được tiếp cận kho tri thức |
| **IV** | **NỘI DUNG LỊCH SỬ ĐỊA PHƯƠNG (LOCAL CONTENT)** | | | | | | | |
| 12 | Soạn thảo Context, Character, Quiz địa phương | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | Giáo viên tự do sáng tạo tư liệu địa phương |
| 13 | Duyệt trạng thái (Draft, Publish, Inactive, Trash) | ❌ | ❌ | ✅ | 👁️ | ❌ | ❌ | School Admin thẩm định & duyệt xuất bản |
| 14 | Xem Nội dung địa phương | ❌ | ❌ | 👁️ | 👁️ (Lớp mình) | 👁️ (Lớp mình) | ❌ | School Admin xem toàn trường; GV/HS xem lớp mình |
| **V** | **BÀI TẬP & BÀI KIỂM TRA (ASSIGNMENT & TEST)** | | | | | | | |
| 15 | Tạo Quiz tự luyện tập chung | 👁️ | ✅ | ❌ | ❌ | ❌ | ❌ | Ngân hàng đề thi chuẩn quốc gia |
| 16 | Giao bài tập (Sự kiện, Chat AI, Test) kèm Deadline | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | Giáo viên giao cho từng lớp học |
| 17 | Làm bài tập được giao & Bài Test lấy điểm | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | Học sinh làm bài theo hạn chót |
| 18 | Làm Quiz tự luyện tập cá nhân | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | Tự do luyện đề trắc nghiệm |
| **VI** | **AI CHATBOT & HẠN MỨC TOKEN** | | | | | | | |
| 19 | Trò chuyện AI với Nhân vật Lịch sử (RAG) | 👁️ | ✅ (Trải nghiệm) | 👁️ | ✅ (Trải nghiệm) | ✅ | ✅ | Chat RAG với dữ liệu nhân vật |
| 20 | Hạn mức Token hàng ngày (Daily Reset) | ❌ | ❌ | ✅ (Config cho trường) | ❌ | ✅ (Theo Quota trường) | ✅ (Free, Plus, Pro) | Quản lý định mức tiêu hao AI Token |
| **VII** | **BẢN ĐỒ ĐỊA DANH & VIDEO BÀI HỌC** | | | | | | | |
| 21 | Xem & tương tác Bản đồ địa danh (Interactive Map) | 👁️ | 👁️ | 👁️ | 👁️ | 👁️ | 👁️ | Tương tác các ghim di tích trên bản đồ |
| **VIII** | **BÁO CÁO & DASHBOARD ANALYTICS** | | | | | | | |
| 22 | Dashboard Doanh thu, User & System Health | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | Dành riêng cho System Admin |
| 23 | Dashboard Báo cáo Trường học (Số lớp, GV, HS) | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | Dành cho BGH / School Admin |
| 24 | Dashboard Giáo viên (Điểm số Test, Tương tác AI) | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | Phân tích học lực các lớp giáo viên dạy |
| 25 | Xem tiến độ học tập & Bảng điểm cá nhân | ❌ | ❌ | ❌ | ❌ | 👁️ | 👁️ | Bảng điểm và huy hiệu cá nhân |
| **IX** | **THANH TOÁN & GÓI DỊCH VỤ (B2C PAYMENT)** | | | | | | | |
| 26 | Mua / Nâng cấp gói Premium qua PayOS | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Cổng thanh toán quét mã QR VietQR |
| 27 | Báo cáo giao dịch thanh toán | ✅ | ❌ | ❌ | ❌ | ❌ | 👁️ (Cá nhân) | Admin xem toàn sàn; User xem lịch sử mình |

---

## 📝 CHI TIẾT USER STORIES

### 🆔 US-SP0-04: Đặc tả & Chuẩn hóa Ma trận Phân quyền 27 tính năng (RBAC Specification)
* **Actor:** `Tất cả các vai trò (6 Roles)`
* **Thời gian thực hiện:** 13/10/2026 – 16/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là Đội ngũ phát triển và Quản trị viên, tôi muốn có một ma trận phân quyền tường minh và nhất quán, để đảm bảo mỗi nhóm người dùng chỉ được tiếp cận đúng các tài nguyên thuộc phạm vi quyền hạn của mình, không bị lộ lọt dữ liệu liên trường hoặc dữ liệu quản trị.

* **Acceptance Criteria (AC):**
  1. Toàn bộ 6 vai trò trong enum `UserRole` được ánh xạ chính xác với 27 tính năng nghiệp vụ trong Ma trận trên.
  2. Áp dụng nguyên tắc **Tách biệt Dữ liệu Đa trường (Multi-tenant Data Isolation)**:
     - `School Admin`, `Teacher`, `School Student` chỉ được xem và sửa dữ liệu thuộc về `school_id` của trường mình.
     - `Teacher` chỉ được quản lý học sinh và giao bài cho các lớp mà mình được phân công (`ClassTeacher`).
     - Người dùng `Customer` B2C không thể xem hay truy cập vào bất kỳ dữ liệu lớp học hay tư liệu nội bộ nào của trường học B2B.
  3. Bất kỳ request nào vi phạm quyền hạn hoặc truy cập chéo trường đều bị chặn và trả về HTTP `403 Forbidden` kèm thông báo lỗi rõ ràng.

---

### 🆔 US-SP0-05: Thiết lập Bộ lọc Phân quyền Controller & Prefix Security (Enforce Endpoint Security)
* **Actor:** `System Admin, School Admin, Teacher, Content Admin`
* **Thời gian thực hiện:** 16/10/2026 – 19/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là Nhà phát triển hệ thống, tôi muốn gắn các quy tắc bảo vệ Role lên các Controller và Prefix URL bằng `@PreAuthorize` và `SecurityConfig`, để hệ thống tự động từ chối các truy cập trái phép ngay từ tầng Gateway/Filter.

* **Acceptance Criteria (AC):**
  1. Cấu hình tiền tố bảo vệ tập trung trong `SecurityConfig.java`:
     - `/api/v1/admin/**` $\rightarrow$ `hasRole('SYSTEM_ADMIN')`
     - `/api/v1/content-admin/**` $\rightarrow$ `hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')`
     - `/api/v1/school-admin/**` $\rightarrow$ `hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')`
     - `/api/v1/teacher/**` $\rightarrow$ `hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')`
  2. Các Controller nghiệp vụ chi tiết áp dụng `@PreAuthorize` ở cấp độ Class hoặc Method:
     - Cho phép nhiều vai trò truy cập đồng thời bằng biểu thức SpEL: `@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")`.
     - Với các thao tác nhạy cảm (như duyệt bài, xóa tài khoản), siết chặt bằng `@PreAuthorize("hasRole('SCHOOL_ADMIN')")`.
  3. `CustomAccessDeniedHandler` trả về cấu trúc lỗi chuẩn JSON khi bị từ chối truy cập:
     ```json
     {
       "status": 403,
       "error": "Forbidden",
       "message": "Bạn không có quyền truy cập tài nguyên này."
     }
     ```
