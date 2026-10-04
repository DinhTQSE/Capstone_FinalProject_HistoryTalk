# 🚀 SPRINT 5 — USER STORIES (04/10/2026 – 10/10/2026)
> **Mục tiêu Sprint 5:** Fundamental SaaS Architecture & Security, SaaS Account Management, School Token Allocation Engine & Classroom Management.  
> **Phụ trách:** Trần Quốc Dinh (`DinhTQ`) & Võ Đồng Đức Khải (`KhaiVDD`)  
> **Thời gian:** 04/10/2026 – 10/10/2026  

---

## 📌 TỔNG HỢP USER STORIES IN SPRINT 5

| Story ID | Tên User Story | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- |
| **`US-SP5-01`** | Quản lý Trường học & Gói Hạn mức Token Tổng | 🟢 **Khải (KhaiVDD)** | `POST /api/v1/admin/schools` |
| **`US-SP5-02`** | Cấp Tài khoản School Admin & Security RBAC | 🟢 **Khải (KhaiVDD)** | `POST /api/v1/admin/school-admins` |
| **`US-SP5-03`** | Quản lý Tài khoản Giáo viên trong Trường | 🟢 **Khải (KhaiVDD)** | `POST /api/v1/school-admin/teachers` |
| **`US-SP5-04`** | Tạo lẻ & Import Excel Hàng loạt Tài khoản Học sinh | 🟢 **Khải (KhaiVDD)** | `POST /api/v1/school-admin/students/import-excel` |
| **`US-SP5-05`** | School Admin Phân bổ Token cho từng Học sinh | 🔵 **Định (DinhTQ)** | `PUT /api/v1/school-admin/students/token-allocation` |
| **`US-SP5-06`** | CRUD Lớp học Toàn trường & Sinh Mã Class Code | 🔵 **Định (DinhTQ)** | `POST /api/v1/classrooms` |
| **`US-SP5-07`** | API Xem Danh sách Lớp học Phụ trách theo Role | 🔵 **Định (DinhTQ)** | `GET /api/v1/teacher/classrooms` |

---

## 📑 CHI TIẾT USER STORIES SPRINT 5

### 🆔 US-SP5-01: Quản lý Trường học & Gói Hạn mức Token Tổng (Enterprise Package Setup)
* **Developer:** 🟢 **Khải (KhaiVDD)**
* **Actor:** `System Admin`
* **Thời gian:** 04/10/2026 – 05/10/2026
* **User Story:**  
  Là System Admin, tôi muốn tạo thông tin Trường học mới và gán Gói Enterprise Token cố định cho toàn trường, để thiết lập hợp đồng dịch vụ SaaS B2B với nhà trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/admin/schools` tiếp nhận: Tên trường, Mã trường (`school_code` duy nhất), Địa chỉ, Email liên hệ đại diện, Gói Enterprise (`ENTERPRISE_SMALL`: 5M Tokens/tháng, `ENTERPRISE_MEDIUM`: 20M Tokens/tháng, `ENTERPRISE_LARGE`: 50M Tokens/tháng).
  2. Lưu số Token tổng vào trường `total_school_token_quota` và khởi tạo số Token dư chưa phân bổ `unallocated_token_quota = total_school_token_quota`.
  3. Kiểm tra điều khoản Policy miễn trừ trách nhiệm về Nội dung Lịch sử Địa phương được chấp nhận trong hợp đồng trường.

---

### 🆔 US-SP5-02: Cấp Tài khoản School Admin & Security RBAC Setup
* **Developer:** 🟢 **Khải (KhaiVDD)**
* **Actor:** `System Admin`
* **Thời gian:** 05/10/2026 – 06/10/2026
* **User Story:**  
  Là System Admin, tôi muốn tạo tài khoản `School Admin` gắn liền với một `school_id` cụ thể và thiết lập kiểm soát phân quyền RBAC, để bàn giao quyền quản trị trường cho phía đại diện nhà trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/admin/school-admins` tạo user có role `SCHOOL_ADMIN` liên kết với `school_id`.
  2. Bổ sung `SCHOOL_ADMIN`, `TEACHER`, `SCHOOL_STUDENT` vào Enum `UserRole`.
  3. Cấu hình Spring Security: `School Admin` chỉ truy cập và chỉnh sửa được các tài nguyên có `school_id` khớp với tài khoản của mình.
  4. Tự động mã hóa mật khẩu tạm thời bằng BCrypt và gửi Email kích hoạt cho School Admin.

---

### 🆔 US-SP5-03: Quản lý Tài khoản Giáo viên trong Trường (Teacher Management)
* **Developer:** 🟢 **Khải (KhaiVDD)**
* **Actor:** `School Admin`
* **Thời gian:** 06/10/2026 – 07/10/2026
* **User Story:**  
  Là School Admin, tôi muốn tạo mới, cập nhật thông tin và khóa tài khoản các Giáo viên thuộc trường mình, để cấp quyền cho Giáo viên quản lý lớp học.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/school-admin/teachers` tạo user có role `TEACHER` thuộc cùng `school_id`.
  2. Yêu cầu nhập: Họ tên, Email, Số điện thoại, Tổ môn giảng dạy.
  3. API `GET /api/v1/school-admin/teachers` danh sách giáo viên trong trường có tìm kiếm và phân trang.
  4. API `PUT /api/v1/school-admin/teachers/{id}/status` cho phép chuyển trạng thái `ACTIVE` / `INACTIVE`.

---

### 🆔 US-SP5-04: Tạo lẻ & Import Excel Hàng loạt Tài khoản Học sinh (Student Bulk Import)
* **Developer:** 🟢 **Khải (KhaiVDD)**
* **Actor:** `School Admin`
* **Thời gian:** 07/10/2026 – 08/10/2026
* **User Story:**  
  Là School Admin, tôi muốn tạo lẻ từng học sinh hoặc upload file Excel danh sách học sinh, để khởi tạo tài khoản `SCHOOL_STUDENT` cho học sinh toàn trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/school-admin/students/import-excel` nhận file `.xlsx` danh sách học sinh (Mã HS, Họ tên, Email, Ngày sinh).
  2. Validate trùng lặp Email/Mã HS. Trả về thông báo lỗi chi tiết dòng trong Excel bị lỗi nếu có.
  3. Đóng gói kết xuất xuất file Excel chứa danh sách tài khoản học sinh kèm Mật khẩu khởi tạo ban đầu.

---

### 🆔 US-SP5-05: School Admin Phân bổ Hạn mức Token cho từng Học sinh (Student Token Allocation)
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `School Admin`
* **Thời gian:** 08/10/2026 – 09/10/2026
* **User Story:**  
  Là School Admin, tôi muốn tự phân bổ (allocate) hạn mức Token riêng cho từng tài khoản học sinh từ tổng quỹ Token của trường, để kiểm soát mức độ sử dụng AI linh hoạt.
* **Acceptance Criteria (AC):**
  1. API `PUT /api/v1/school-admin/students/token-allocation` hỗ trợ 2 chế độ:
     * Phân bổ riêng cho 1 học sinh cụ thể (`student_id`, `token_amount`).
     * Phân bổ đều hàng loạt cho toàn bộ học sinh trong trường hoặc theo khối lớp.
  2. Ràng buộc: Tổng Token phân bổ cho tất cả học sinh **không được vượt quá `total_school_token_quota`**.
  3. Tự động trừ và cập nhật lại số Token chưa phân bổ `unallocated_token_quota`.

---

### 🆔 US-SP5-06: CRUD Lớp học Toàn trường & Sinh Mã Class Code duy nhất
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `School Admin` / `Teacher`
* **Thời gian:** 09/10/2026 – 10/10/2026
* **User Story:**  
  Là School Admin hoặc Giáo viên, tôi muốn tạo mới, chỉnh sửa thông tin Lớp học và gán Giáo viên phụ trách, để thiết lập danh mục lớp học cho năm học.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/classrooms` tiếp nhận: Tên lớp, Năm học, Mô tả, `teacher_id` phụ trách.
  2. Hệ thống tự động sinh `class_code` ngẫu nhiên 6-8 ký tự duy nhất (ví dụ: `HIS10A1-2026`).
  3. Đảm bảo ràng buộc tính toàn vẹn: Không cho phép tạo trùng Tên lớp trong cùng một Trường và Năm học.

---

### 🆔 US-SP5-07: Xem Danh sách Lớp học Phụ trách theo Role
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `School Admin` / `Teacher` / `School Student`
* **Thời gian:** 10/10/2026
* **User Story:**  
  Là người dùng trong trường, tôi muốn xem danh sách các Lớp học phù hợp với vai trò của mình, để theo dõi quá trình học tập và giảng dạy.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/school-admin/classrooms`: School Admin xem danh sách toàn bộ các Lớp học trong trường.
  2. API `GET /api/v1/teacher/classrooms`: Giáo viên xem danh sách các Lớp học do mình chủ nhiệm/phụ trách.
  3. API `GET /api/v1/student/classrooms`: Học sinh xem danh sách Lớp học mà mình đã được phân vào.
