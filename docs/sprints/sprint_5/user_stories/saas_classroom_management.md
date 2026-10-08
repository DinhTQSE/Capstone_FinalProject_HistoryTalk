# 📋 USER STORIES: SAAS CLASSROOM MANAGEMENT & TOKEN ALLOCATION
> **Sprint:** Sprint 5 (04/10/2026 – 10/10/2026)  
> **Phân hệ:** Quản lý Lớp học SaaS & Cơ chế Phân bổ Token Trường học  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP5-05`** | School Admin Phân bổ Token cho từng Học sinh | `School Admin` | 🔵 Dinh (DinhTQ) | `PUT /api/v1/school-admin/students/token-allocation` |
| **`US-SP5-06`** | CRUD Lớp học Toàn trường & Sinh Mã Class Code | `School Admin` / `Teacher` | 🔵 Dinh (DinhTQ) | `POST /api/v1/classrooms` |
| **`US-SP5-07`** | API Xem Danh sách Lớp học Phụ trách theo Role | `School Admin` / `Teacher` / `Student` | 🔵 Dinh (DinhTQ) | `GET /api/v1/teacher/classrooms` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP5-05: School Admin Phân bổ Hạn mức Token cho từng Học sinh (Student Token Allocation)
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 08/10/2026 – 09/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là School Admin, tôi muốn tự phân bổ (allocate) hạn mức Token riêng cho từng tài khoản học sinh từ tổng quỹ Token của trường, để kiểm soát mức độ sử dụng AI linh hoạt.
* **Acceptance Criteria (AC):**
  1. API `PUT /api/v1/school-admin/students/token-allocation` hỗ trợ 2 chế độ:
     * Phân bổ riêng cho 1 học sinh cụ thể (`student_id`, `token_amount`).
     * Phân bổ đều hàng loạt cho toàn bộ học sinh trong trường hoặc theo khối lớp.
  2. Ràng buộc: Tổng Token phân bổ cho tất cả học sinh **không được vượt quá `total_school_token_quota`**.
  3. Tự động trừ và cập nhật lại số Token chưa phân bổ `unallocated_token_quota`.

---

### 🆔 US-SP5-06: CRUD Lớp học Toàn trường & Sinh Mã Class Code duy nhất (Classroom Management)
* **Actor:** `School Admin` / `Teacher`
* **Thời gian thực hiện:** 09/10/2026 – 10/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là School Admin hoặc Giáo viên, tôi muốn tạo mới, chỉnh sửa thông tin Lớp học và gán Giáo viên phụ trách, để thiết lập danh mục lớp học cho năm học.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/classrooms` tiếp nhận: Tên lớp, Năm học, Mô tả, `teacher_id` phụ trách.
  2. Hệ thống tự động sinh `class_code` ngẫu nhiên 6-8 ký tự duy nhất (ví dụ: `HIS10A1-2026`).
  3. Đảm bảo ràng buộc tính toàn vẹn: Không cho phép tạo trùng Tên lớp trong cùng một Trường và Năm học.

---

### 🆔 US-SP5-07: Xem Danh sách Lớp học Phụ trách theo Role (Classroom Access by Role)
* **Actor:** `School Admin` / `Teacher` / `School Student`
* **Thời gian thực hiện:** 10/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là người dùng trong trường, tôi muốn xem danh sách các Lớp học phù hợp với vai trò của mình, để theo dõi quá trình học tập và giảng dạy.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/school-admin/classrooms`: School Admin xem danh sách toàn bộ các Lớp học trong trường.
  2. API `GET /api/v1/teacher/classrooms`: Giáo viên xem danh sách các Lớp học do mình chủ nhiệm/phụ trách.
  3. API `GET /api/v1/student/classrooms`: Học sinh xem danh sách Lớp học mà mình đã được phân vào.
