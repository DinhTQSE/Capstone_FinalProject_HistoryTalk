# 📋 USER STORIES: CLASS ASSOCIATION & ASSIGNMENT MANAGEMENT
> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Phân hệ:** Gán Học sinh vào Lớp & Quản lý Giao Bài tập Đa nhiệm kèm Deadline  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP6-01`** | Gán & Chuyển Học sinh vào Lớp học theo danh sách | `School Admin` / `Teacher` | 🟢 Khải (KhaiVDD) | `POST /api/v1/classrooms/{classId}/students/batch` |
| **`US-SP6-04`** | Giáo viên Giao Bài tập Đa nhiệm cho Lớp kèm Deadline | `Teacher` | 🔵 Dinh (DinhTQ) | `POST /api/v1/teacher/assignments` |
| **`US-SP6-05`** | Học sinh Nộp bài & Tự động Đánh giá Tiến độ | `School Student` / System | 🔵 Dinh (DinhTQ) | `POST /api/v1/student/assignments/{id}/submit` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP6-01: Gán & Chuyển Học sinh vào Lớp học (Class Roster Association)
* **Actor:** `School Admin` / `Teacher`
* **Thời gian thực hiện:** 11/10/2026 – 12/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin hoặc Giáo viên, tôi muốn gán hàng loạt học sinh vào lớp học hoặc chuyển học sinh ra khỏi lớp, để quản lý danh sách học sinh theo từng lớp học chính xác.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/classrooms/{classId}/students/batch` tiếp nhận danh sách `student_ids` để thêm vào Lớp.
  2. API `DELETE /api/v1/classrooms/{classId}/students/{studentId}` xóa/gỡ học sinh khỏi Lớp.
  3. Validate: Tất cả học sinh được thêm phải thuộc cùng `school_id` với Lớp học.

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
