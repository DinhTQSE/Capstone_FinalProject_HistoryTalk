# 📋 USER STORIES: SAAS ROLES & ACCOUNT MANAGEMENT + SAAS SECURITY
> **Sprint:** Sprint 5 (04/10/2026 – 10/10/2026)  
> **Phân hệ:** Xác thực, Phân quyền SaaS RBAC & Quản lý Tài khoản Trường học  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP5-01`** | Quản lý Trường học & Gói Hạn mức Token Tổng | `System Admin` | 🟢 Khải (KhaiVDD) | `POST /api/v1/admin/schools` |
| **`US-SP5-02`** | Cấp Tài khoản School Admin & Security RBAC | `System Admin` | 🟢 Khải (KhaiVDD) | `POST /api/v1/admin/school-admins` |
| **`US-SP5-03`** | Quản lý Tài khoản Giáo viên trong Trường | `School Admin` | 🟢 Khải (KhaiVDD) | `POST /api/v1/school-admin/teachers` |
| **`US-SP5-04`** | Tạo lẻ & Import Excel Hàng loạt Tài khoản Học sinh | `School Admin` | 🟢 Khải (KhaiVDD) | `POST /api/v1/school-admin/students/import-excel` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP5-01: Quản lý Trường học & Gói Hạn mức Token Tổng (Enterprise Package Setup)
* **Actor:** `System Admin`
* **Thời gian thực hiện:** 04/10/2026 – 05/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là System Admin, tôi muốn tạo thông tin Trường học mới và gán Gói Enterprise Token cố định cho toàn trường, để thiết lập hợp đồng dịch vụ SaaS B2B với nhà trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/admin/schools` tiếp nhận: Tên trường, Mã trường (`school_code` duy nhất), Địa chỉ, Email liên hệ đại diện, Gói Enterprise (`ENTERPRISE_SMALL`: 5M Tokens/tháng, `ENTERPRISE_MEDIUM`: 20M Tokens/tháng, `ENTERPRISE_LARGE`: 50M Tokens/tháng).
  2. Lưu số Token tổng vào trường `total_school_token_quota` và khởi tạo số Token dư chưa phân bổ `unallocated_token_quota = total_school_token_quota`.
  3. Kiểm tra điều khoản Policy miễn trừ trách nhiệm về Nội dung Lịch sử Địa phương được chấp nhận trong hợp đồng trường.

---

### 🆔 US-SP5-02: Cấp Tài khoản School Admin & Security RBAC Setup
* **Actor:** `System Admin`
* **Thời gian thực hiện:** 05/10/2026 – 06/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là System Admin, tôi muốn tạo tài khoản `School Admin` gắn liền với một `school_id` cụ thể và thiết lập kiểm soát phân quyền RBAC, để bàn giao quyền quản trị trường cho phía đại diện nhà trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/admin/school-admins` tạo user có role `SCHOOL_ADMIN` liên kết với `school_id`.
  2. Bổ sung `SCHOOL_ADMIN`, `TEACHER`, `SCHOOL_STUDENT` vào Enum `UserRole`.
  3. Cấu hình Spring Security: `School Admin` chỉ truy cập và chỉnh sửa được các tài nguyên có `school_id` khớp với tài khoản của mình.
  4. Tự động mã hóa mật khẩu tạm thời bằng BCrypt và gửi Email kích hoạt cho School Admin.

---

### 🆔 US-SP5-03: Quản lý Tài khoản Giáo viên trong Trường (Teacher Management)
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 06/10/2026 – 07/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin, tôi muốn tạo mới, cập nhật thông tin và khóa tài khoản các Giáo viên thuộc trường mình, để cấp quyền cho Giáo viên quản lý lớp học.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/school-admin/teachers` tạo user có role `TEACHER` thuộc cùng `school_id`.
  2. Yêu cầu nhập: Họ tên, Email, Số điện thoại, Tổ môn giảng dạy.
  3. API `GET /api/v1/school-admin/teachers` danh sách giáo viên trong trường có tìm kiếm và phân trang.
  4. API `PUT /api/v1/school-admin/teachers/{id}/status` cho phép chuyển trạng thái `ACTIVE` / `INACTIVE`.

---

### 🆔 US-SP5-04: Tạo lẻ & Import Excel Hàng loạt Tài khoản Học sinh (Student Bulk Import)
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 07/10/2026 – 08/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin, tôi muốn tạo lẻ từng học sinh hoặc upload file Excel danh sách học sinh, để khởi tạo tài khoản `SCHOOL_STUDENT` cho học sinh toàn trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/school-admin/students/import-excel` nhận file `.xlsx` danh sách học sinh (Mã HS, Họ tên, Email, Ngày sinh).
  2. Validate trùng lặp Email/Mã HS. Trả về thông báo lỗi chi tiết dòng trong Excel bị lỗi nếu có.
  3. Đóng gói kết xuất xuất file Excel chứa danh sách tài khoản học sinh kèm Mật khẩu khởi tạo ban đầu.
