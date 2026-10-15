# 📋 USER STORIES: CLASS ASSOCIATION & ASSIGNMENT MANAGEMENT
> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Phân hệ:** Gán Học sinh vào Lớp & Quản lý Giao Bài tập Đa nhiệm kèm Deadline  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP6-01`** | Gán & Chuyển Học sinh vào Lớp học (Batch, CSV & Transfer) | `School Admin` / `Teacher` | 🟢 Khải (KhaiVDD) | `POST /api/v1/classrooms/{classId}/students/batch`<br>`POST /api/v1/classrooms/{classId}/students/import-csv`<br>`GET /api/v1/classrooms/{classId}/students`<br>`GET /api/v1/classrooms/{classId}/available-students`<br>`DELETE /api/v1/classrooms/{classId}/students/{studentId}`<br>`POST /api/v1/classrooms/{fromClassId}/students/transfer` |
| **`US-SP6-04`** | Giáo viên Giao Bài tập Đa nhiệm cho Lớp kèm Deadline | `Teacher` | 🔵 Dinh (DinhTQ) | `POST /api/v1/teacher/assignments` |
| **`US-SP6-05`** | Học sinh Nộp bài & Tự động Đánh giá Tiến độ | `School Student` / System | 🔵 Dinh (DinhTQ) | `POST /api/v1/student/assignments/{id}/submit` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP6-01: Gán & Chuyển Học sinh vào Lớp học (Class Roster Association & Import Integration)
* **Actor:** `School Admin` / `Teacher`
* **Thời gian thực hiện:** 11/10/2026 – 12/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin hoặc Giáo viên, tôi muốn gán hàng loạt học sinh vào lớp học (bằng cách chọn trên UI hoặc tải file CSV), xem danh sách học sinh của lớp, tra cứu học sinh chưa có lớp, chuyển lớp và gỡ học sinh khỏi lớp, để quản lý danh sách học sinh theo từng lớp học chính xác.
* **Acceptance Criteria (AC):**
  1. **API Batch Assign (JSON):** `POST /api/v1/classrooms/{classId}/students/batch` tiếp nhận mảng `studentIds` để thêm vào lớp. Xử lý idempotent (học sinh đã có trong lớp thì bỏ qua, không crash cả batch).
  2. **API CSV Import vào Lớp:** `POST /api/v1/classrooms/{classId}/students/import-csv` kế thừa Import Engine Sprint 5:
     - Học sinh đã có tài khoản: Chỉ liên kết vào lớp, không trừ token.
     - Học sinh mới: Tạo tài khoản, trừ token từ quỹ trường, và gán ngay vào lớp.
  3. **API Xem Roster Lớp:** `GET /api/v1/classrooms/{classId}/students` trả về danh sách học sinh hiện có trong lớp (hỗ trợ tìm kiếm theo họ tên, mã HS, phân trang).
  4. **API Danh sách Học sinh khả dụng:** `GET /api/v1/classrooms/{classId}/available-students` trả về học sinh của trường chưa thuộc lớp này để hiển thị Modal chọn học sinh trên UI.
  5. **API Gỡ Học sinh khỏi Lớp:** `DELETE /api/v1/classrooms/{classId}/students/{studentId}` xóa liên kết khỏi lớp, bảo toàn lịch sử học tập.
  6. **API Chuyển Lớp:** `POST /api/v1/classrooms/{fromClassId}/students/transfer` chuyển học sinh sang lớp mới trong cùng 1 transaction.
  7. **Tenant & Role Isolation:** Tất cả học sinh và lớp học phải cùng thuộc `school_id`. Giáo viên chỉ được thao tác trên lớp do mình phụ trách.

---

### 🆔 US-SP6-04: Giáo viên Giao Bài tập Đa nhiệm cho Lớp kèm Deadline (Assignment Creation)
* **Actor:** `Teacher`
* **Thời gian thực hiện:** 14/10/2026 – 15/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là Giáo viên, tôi muốn giao Bài tập cho Lớp phụ trách (gồm: Đọc Bối cảnh, Chat AI với Nhân vật, hoặc Làm Quiz kiểm tra) kèm Hạn nộp (`deadline`), để học sinh làm bài và thu thập kết quả.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/teacher/assignments` nhận: `class_id`, `title`, `assignment_type` (`CONTEXT_READING`, `AI_CHAT_ROLEPLAY`, `QUIZ_TEST`), `target_id`, `deadline`.
  2. Hỗ trợ chọn bài tập từ cả Kho Nội dung Chung (Global) lẫn Kho Lịch sử Địa phương (`PUBLISHED`) của trường.
  3. Tự động khởi tạo trạng thái `assignment_submission` là `NOT_STARTED` cho toàn bộ học sinh có trong Lớp.

---

### 🆔 US-SP6-05: Học sinh Nộp bài & Tự động Đánh giá Tiến độ (Assignment Submission & Auto-Grading)
* **Actor:** `School Student` / System
* **Thời gian thực hiện:** 15/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là Học sinh hoặc Hệ thống, tôi muốn tự động chấm điểm bài Quiz bấm giờ và đếm số tin nhắn AI trò chuyện, để tự động hoàn thành bài tập và ghi nhận điểm số.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/student/assignments/{id}/submit`:
     * Với Bài Quiz: Tính điểm % câu trả lời đúng và lưu `score`.
     * Với Bài Chat AI: Đếm tổng số tin nhắn học sinh đã trò chuyện với nhân vật AI (`ai_chat_message_count`).
  2. Cập nhật trạng thái `COMPLETED` (nếu nộp trước hạn) hoặc `LATE_SUBMITTED` (nếu nộp sau hạn).
