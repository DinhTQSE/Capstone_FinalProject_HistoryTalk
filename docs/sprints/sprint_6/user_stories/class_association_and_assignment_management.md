# 📋 USER STORIES: CLASS ASSOCIATION & ASSIGNMENT MANAGEMENT
> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Phân hệ:** Gán Học sinh vào Lớp & Quản lý Giao Bài tập Đa nhiệm kèm Deadline  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP6-01`** | Gán & Chuyển Học sinh vào Lớp học (Batch, CSV & Transfer) | `School Admin` / `Teacher` | 🟢 Khải (KhaiVDD) | `POST /api/v1/classrooms/{classId}/students/batch`<br>`POST /api/v1/classrooms/{classId}/students/import-csv`<br>`GET /api/v1/classrooms/{classId}/students`<br>`GET /api/v1/classrooms/{classId}/available-students`<br>`DELETE /api/v1/classrooms/{classId}/students/{studentId}`<br>`POST /api/v1/classrooms/{fromClassId}/students/transfer` |
| **`US-SP6-04`** | Giáo viên Giao Bài tập Đa nhiệm cho Lớp kèm Deadline | `Teacher` | 🟢 Khải (KhaiVDD) | `POST /api/v1/teacher/assignments` |
| **`US-SP6-05`** | Học sinh Nộp bài & Tự động Đánh giá Tiến độ | `School Student` / System | 🟢 Khải (KhaiVDD) | `POST /api/v1/student/assignments/{id}/submit` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP6-01: Gán & Chuyển Học sinh vào Lớp học (Class Roster Association & Import Integration)
* **Actor:** `School Admin` / `Teacher`
* **Thời gian thực hiện:** 11/10/2026 – 12/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin hoặc Giáo viên, tôi muốn gán hàng loạt học sinh vào lớp học (bằng cách chọn trên UI hoặc tải file CSV), xem danh sách học sinh của lớp, tra cứu học sinh chưa có lớp, chuyển lớp và gỡ học sinh khỏi lớp, để quản lý danh sách học sinh theo từng lớp học chính xác.
* **Acceptance Criteria (AC):**
  1. **API Batch Assign (JSON):** `POST /api/v1/classrooms/{classId}/students/batch` tiếp nhận mảng `studentIds` để thêm vào lớp. Xử lý idempotent:
     - Nếu học sinh chưa từng có trong lớp: Tạo mới bản ghi `class_student` với `status = 'ACTIVE'`.
     - Nếu học sinh từng ở lớp này với trạng thái `INACTIVE` hoặc `TRANSFERRED`: Tái kích hoạt lại `status = 'ACTIVE'`.
  2. **API CSV Import vào Lớp:** `POST /api/v1/classrooms/{classId}/students/import-csv` kế thừa Import Engine Sprint 5:
     - Học sinh đã có tài khoản: Chỉ liên kết vào lớp (`status = 'ACTIVE'`), không trừ token.
     - Học sinh mới: Tạo tài khoản, trừ token từ quỹ trường, và gán ngay vào lớp (`status = 'ACTIVE'`).
  3. **API Xem Roster Lớp:** `GET /api/v1/classrooms/{classId}/students` trả về danh sách học sinh hiện có trong lớp (chỉ lấy học sinh có `status = 'ACTIVE'`, hỗ trợ tìm kiếm theo họ tên, mã HS, phân trang).
  4. **API Danh sách Học sinh khả dụng:** `GET /api/v1/classrooms/{classId}/available-students` trả về học sinh của trường chưa thuộc bất kỳ lớp nào (hoặc đang `INACTIVE` trong lớp này) để hiển thị Modal chọn học sinh trên UI.
  5. **API Gỡ Học sinh khỏi Lớp (Soft Delete):** `DELETE /api/v1/classrooms/{classId}/students/{studentId}`:
     - **Cơ chế Soft Delete:** Tuyệt đối **không xóa cứng (Hard Delete)** bản ghi `class_student`, mà cập nhật trạng thái sang `status = 'INACTIVE'`.
     - **Mục đích:** Bảo toàn 100% lịch sử học tập và các bài nộp cũ (`assignment_submission`) mà học sinh đã làm trong thời gian ở lớp, ngăn chặn việc tạo ra "học sinh ma" khi giáo viên xem lại sổ điểm các bài tập cũ.
  6. **API Chuyển Lớp:** `POST /api/v1/classrooms/{fromClassId}/students/transfer`:
     - Trong cùng 1 transaction: Cập nhật bản ghi ở lớp cũ sang `status = 'TRANSFERRED'`, và tạo/kích hoạt bản ghi ở lớp mới sang `status = 'ACTIVE'`.
     - Toàn bộ kết quả bài tập học sinh từng làm ở lớp cũ vẫn được lưu giữ và gắn liền với bài tập của lớp cũ để phục vụ đối soát học bạ.
  7. **Tenant & Role Isolation:** Tất cả học sinh và lớp học phải cùng thuộc `school_id`. Giáo viên chỉ được thao tác trên lớp do mình phụ trách.

> ⚠️ **Phạm vi nghiệp vụ (Scope Boundary):**
> * US này **CHỈ** phụ trách quản lý mối liên kết học sinh - lớp học và trạng thái liên kết (`ACTIVE`, `INACTIVE`, `TRANSFERRED`), đảm bảo không làm đứt gãy quan hệ dữ liệu của các bài nộp lịch sử.
> * US này **KHÔNG** cover việc tính toán tỷ lệ hoàn thành (Completion Rate), điểm trung bình hay thống kê biểu đồ của Lớp học (các nghiệp vụ này thuộc về phân hệ Analytics & Dashboard trong `US-SP6-06` và `US-SP6-07`).

---

### 🆔 US-SP6-04: Giáo viên Giao Bài tập Đa nhiệm cho Lớp kèm Deadline (Assignment Creation & Content Pool Selection)
* **Actor:** `Teacher`
* **Thời gian thực hiện:** 14/10/2026 – 15/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là Giáo viên, tôi muốn duyệt kho nội dung (Bối cảnh, Nhân vật, Quiz), xem chi tiết nội dung trước khi chọn và giao Bài tập cho Lớp phụ trách kèm Hạn nộp (`deadline`), để học sinh làm bài và hệ thống thu thập kết quả.
* **Acceptance Criteria (AC):**
  1. **API Giao Bài tập:** `POST /api/v1/teacher/assignments` nhận:
     * `class_id` (`UUID`, bắt buộc): Lớp học được giao bài (phải thuộc quyền quản lý của Giáo viên).
     * `title` (`String`, bắt buộc): Tiêu đề bài tập hiển thị cho học sinh.
     * `description` (`String`, tùy chọn): Lời dặn dò, hướng dẫn của giáo viên.
     * `assignment_type` (`AssignmentType`, bắt buộc):
       - `CONTEXT_READING`: Đọc và tìm hiểu Bối cảnh Lịch sử.
       - `AI_CHAT_ROLEPLAY`: Nhập vai trò chuyện tương tác với Nhân vật Lịch sử (AI Persona).
       - `QUIZ_TEST`: Làm bài kiểm tra trắc nghiệm bấm giờ.
     * `target_id` (`UUID`, bắt buộc): ID của nội dung được chọn (tương ứng `context_id`, `character_id`, hoặc `quiz_id`).
     * `deadline` (`LocalDateTime`, bắt buộc): Hạn chót nộp bài (phải sau thời điểm hiện tại).
     * `min_requirement` (`Integer`, tùy chọn): Số lượng tiêu chuẩn yêu cầu để đạt điểm tối đa (thang 10):
       - Với `AI_CHAT_ROLEPLAY`: Số lượt tin nhắn tương tác tối thiểu (mặc định: 5 hoặc do GV cài đặt, ví dụ: 10 câu).
       - Với `CONTEXT_READING`: Thời gian đọc tối thiểu tính bằng giây (mặc định: 180s - 3 phút).
       - Với `QUIZ_TEST`: Tự động lấy tổng số câu hỏi của bộ Quiz làm tiêu chuẩn.
  2. **Tích hợp Kho Nội dung (Content Pool Integration) & Preview:**
     * Hệ thống tận dụng các kho nội dung đã có sẵn:
       - `CONTEXT_READING`: Lấy từ `historical_context` (Global qua `GET /api/v1/historical-contexts`, Local qua `GET /api/v1/teacher/local-contents/contexts`).
       - `AI_CHAT_ROLEPLAY`: Lấy từ `character` (Global qua `GET /api/v1/characters`, Local qua `GET /api/v1/teacher/local-contents/characters`).
       - `QUIZ_TEST`: Lấy từ `quiz` (qua `GET /api/v1/quizzes`).
     * Giáo viên có quyền xem trước chi tiết nội dung (`Preview/View Detail`) thông qua các API GET chi tiết theo ID trước khi xác nhận giao bài.
     * Với nội dung địa phương (Local Content), chỉ được phép giao các bài đã ở trạng thái `ACTIVE` (đã được School Admin phê duyệt).
  3. **Khởi tạo Submission tự động (Eager Initialization):**
     * Ngay khi bài tập được tạo, hệ thống tự động sinh bản ghi `assignment_submission` cho toàn bộ học sinh đang có trạng thái `ACTIVE` trong lớp.
     * Trạng thái khởi tạo ban đầu là **`NOT_STARTED`**.
  4. **Cơ chế xử lý Edge Cases:**
     * **Edge Case 1 (Học sinh mới chuyển vào lớp sau khi bài tập đã được giao):**
       - Hệ thống **không** tự động ép gán làm các bài tập đã giao trước ngày chuyển lớp để tránh làm học sinh bị quá tải hoặc thiếu hụt kiến thức nền.
       - Các bài tập đã hết hạn trước thời điểm chuyển lớp sẽ được đánh dấu trạng thái **`EXEMPTED` (Miễn làm)** trong sổ điểm, không tính điểm 0 và không kéo tụt điểm trung bình của học sinh/lớp.
       - **Chế độ Luyện tập bù (Practice / Catch-up Mode):** Học sinh vẫn có thể xem và làm bài tập cũ để củng cố kiến thức mà không bị phạt nộp trễ (`LATE_SUBMITTED`).
       - Giáo viên có quyền kích hoạt **"Gia hạn riêng" (Custom Student Deadline)** nếu muốn giao bài bù có tính điểm chính thức cho học sinh này.
     * **Edge Case 2 (Học sinh bị gỡ khỏi lớp hoặc chuyển sang lớp khác):**
       - Toàn bộ bài nộp và điểm số cũ của học sinh trong lớp cũ **được bảo toàn nguyên vẹn** (phục vụ đối soát học bạ), chỉ đổi trạng thái liên kết lớp `class_student` sang `TRANSFERRED` hoặc `INACTIVE`.
     * **Edge Case 3 (Giáo viên gia hạn Deadline hoặc Hủy/Xóa bài tập):**
       - *Gia hạn Deadline:* Nếu giáo viên lùi deadline sang tương lai, các bài nộp từng bị đánh dấu `LATE_SUBMITTED` nhưng có `submitted_at <= new_deadline` sẽ tự động chuyển thành `SUBMITTED` đúng hạn.
       - *Hủy/Xóa bài tập:* Thực hiện **Soft Delete** (chuyển trạng thái bài tập sang `CANCELLED`). Tuyệt đối không hard delete để tránh phá vỡ toàn vẹn dữ liệu submission và phiên chat AI đã phát sinh.

---

### 🆔 US-SP6-05: Học sinh Nộp bài & Tự động Đánh giá Tiến độ (Assignment Submission & Auto-Grading)
* **Actor:** `School Student` / System
* **Thời gian thực hiện:** 15/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là Học sinh hoặc Hệ thống, tôi muốn tự động chấm điểm bài Quiz trắc nghiệm, tính điểm tương tác đối thoại với AI, hoặc ghi nhận thời gian đọc bối cảnh quy về thang điểm 10, để tự động cập nhật trạng thái hoàn thành bài tập và ghi nhận điểm số vào sổ học tập.
* **Acceptance Criteria (AC):**
  1. **Đặc tả Enum Trạng thái Submission (`SubmissionStatus`):**
     * `NOT_STARTED`: Khởi tạo mặc định khi bài tập được giao, học sinh chưa bấm vào xem/làm.
     * `IN_PROGRESS`: Học sinh đã mở bài tập (bắt đầu chat AI, bắt đầu làm quiz, hoặc mở tài liệu đọc).
     * `SUBMITTED`: Học sinh đã hoàn thành và nộp bài đúng hạn (`submitted_at <= deadline`).
     * `LATE_SUBMITTED`: Học sinh nộp bài sau hạn (`submitted_at > deadline`).
     * `MISSED`: Quá hạn chót mà học sinh vẫn ở `NOT_STARTED` hoặc `IN_PROGRESS` chưa hoàn thành.
     * `EXEMPTED`: Miễn làm (dành riêng cho học sinh mới chuyển lớp đối với các bài đã giao trước đó).
  2. **API Nộp bài & Công thức Chấm điểm Tương tác (Server-Side Auto Grading trên Thang điểm 10):**
     * `POST /api/v1/student/assignments/{assignmentId}/submit`: Client gửi mã phiên (`quizSessionId` hoặc `chatSessionId`), Backend tự động tính toán khép kín, tuyệt đối không nhận điểm trực tiếp từ client.
     * **`AI_CHAT_ROLEPLAY` (Điểm tương tác Chat AI):**
       - Backend đếm số tin nhắn học sinh gửi trong `ChatSession`: `actual_count = COUNT(message WHERE role = 'USER')`.
       - So khớp với tiêu chuẩn `min_requirement` do giáo viên cài đặt:
         * Nếu `actual_count >= min_requirement`: Cho tối đa **`10.0`** điểm.
         * Nếu `actual_count < min_requirement`: Tính tỷ lệ trên thang điểm 10:  
           $$\text{score} = \left(\frac{\text{actual\_count}}{\text{min\_requirement}}\right) \times 10.0$$
           *(Ví dụ: Tiêu chuẩn là 10 tin nhắn. Học sinh chat 7 tin nhắn ➔ `score = 7.00/10`; chat 12 tin nhắn ➔ `score = 10.00/10`).*
       - Lưu `message_count = actual_count`.
     * **`QUIZ_TEST` (Điểm trắc nghiệm Quiz):**
       - Backend lấy kết quả từ `QuizSession`:  
         $$\text{score} = \left(\frac{\text{correct\_answers}}{\text{total\_questions}}\right) \times 10.0$$
     * **`CONTEXT_READING` (Điểm tương tác Đọc bối cảnh):**
       - Backend tính `reading_duration_seconds = Duration.between(started_at, submitted_at).getSeconds()`.
       - So khớp với `min_requirement` (giây):
         * Nếu `reading_duration_seconds >= min_requirement`: Cho **`10.0`** điểm.
         * Nếu `reading_duration_seconds < min_requirement`:  
           $$\text{score} = \left(\frac{\text{reading\_duration\_seconds}}{\text{min\_requirement}}\right) \times 10.0$$
  3. **Cập nhật Vòng đời & Thời gian nộp:**
     * Lưu chính xác `submitted_at = LocalDateTime.now()`.
     * So sánh với `deadline` của bài tập để set trạng thái `SUBMITTED` hoặc `LATE_SUBMITTED`.
  4. **Bảo mật & Tenant Isolation:**
     * Học sinh chỉ được nộp bài cho các assignment thuộc lớp học mà mình đang theo học (`status = ACTIVE`).
     * Học sinh không thể nộp bài tập của trường khác hoặc lớp khác (`403 Forbidden`).
