# 📋 USER STORIES: LOCAL HISTORY CONTENT WORKFLOW & DISCLAIMER POLICY
> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Phân hệ:** Quản lý Nội dung Lịch sử Địa phương, Cơ chế Chia sẻ Nội bộ & Phê duyệt  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP6-02`** | Giáo viên Tạo & Chia sẻ Nội dung Lịch sử Địa phương | `Teacher` | 🟢 Khải (KhaiVDD) | `POST /api/v1/teacher/local-contents` |
| **`US-SP6-03`** | School Admin Duyệt Local Content & Policy Disclaimer | `School Admin` | 🟢 Khải (KhaiVDD) | `PUT /api/v1/school-admin/local-contents/{id}/status` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP6-02: Giáo viên Tạo & Chia sẻ Nội dung Lịch sử Địa phương Nội bộ Trường (Local Content Sharing)
* **Actor:** `Teacher`
* **Thời gian thực hiện:** 12/10/2026 – 13/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là Giáo viên, tôi muốn tạo mới Bối cảnh, Nhân vật hoặc Quiz mang tính chất Lịch sử Địa phương và tự động chia sẻ nội bộ với các Giáo viên khác trong trường, để phục vụ việc giao bài tập đặc thù của nhà trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/teacher/local-contents` tạo nội dung địa phương gắn liền với `school_id` và `created_by_teacher_id`. Mặc định trạng thái là `DRAFT`.
  2. Tất cả Giáo viên thuộc **cùng `school_id` đó** đều xem và dùng chung được nội dung địa phương này để tham khảo và giao bài tập.
  3. Người dùng thuộc trường khác hoặc B2C Customer **tuyệt đối không thể truy cập** được nội dung địa phương này.

---

### 🆔 US-SP6-03: School Admin Duyệt Local Content & Policy Disclaimer Enforcement
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 13/10/2026 – 14/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin, tôi muốn kiểm duyệt nội dung Lịch sử Địa phương do Giáo viên trường mình soạn thảo và bấm Duyệt (`PUBLISHED`), để học sinh trong trường làm bài tập và chịu trách nhiệm nội dung theo đúng Policy nhà trường.
* **Acceptance Criteria (AC):**
  1. API `PUT /api/v1/school-admin/local-contents/{id}/status` cho phép chuyển trạng thái giữa `PUBLISHED`, `DRAFT`, và `INACTIVE`.
  2. Lưu trường `approved_by_school_admin_id` để phục vụ đối soát nội bộ nhà trường.
  3. Theo Điều khoản dịch vụ (Policy), bên cung cấp ứng dụng HistoryTalk hoàn toàn **miễn trừ trách nhiệm pháp lý** đối với tính xác thực của các nội dung Lịch sử Địa phương do nhà trường tự đăng tải.
