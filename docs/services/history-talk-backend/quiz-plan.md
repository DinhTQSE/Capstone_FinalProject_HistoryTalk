# Quiz Module Implementation Plan – API v2.0

**Status:** ⏳ Pending Implementation  
**Created:** March 2026  
**Target:** Nâng cấp cấu trúc Entity hiện tại để đáp ứng API Contract v2.0 (bao gồm Customer flow và Staff/Admin CRUD).

---

## 1. Phân Tích Hiện Trạng & Cập Nhật Entity

Các entity hiện tại đang thiếu khá nhiều trường so với thiết kế trong `API_CONTRACT.md`. Cần bổ sung các trường sau và áp dụng cơ chế **Soft Delete**.

### 1.1 `Quiz` (Cần bổ sung)
File hiện tại: `Quiz.java`
* **Thêm các fields:**
    * `description` (TEXT)
    * `grade` (Integer - 10, 11, 12)
    * `chapter_number` (Integer)
    * `chapter_title` (String)
    * `era` (Enum `EventEra` - tái sử dụng enum đã có)
    * `duration_seconds` (Integer)
    * `play_count` (Integer - default 0)
    * `rating` (Double - default 0.0)
    * `deleted_at` (LocalDateTime - nullable, phục vụ Soft Delete)
* **Logic:** Thêm các annotation:
    ```java
    @SQLDelete(sql = "UPDATE quiz SET deleted_at = NOW() WHERE quiz_id=?")
    @Where(clause = "deleted_at IS NULL") // Hoặc @SQLRestriction("deleted_at IS NULL") với Hibernate 6+
    ```

### 1.2 `Question` (Cần cập nhật)
File hiện tại: `Question.java`
* **Thay đổi kiểu dữ liệu:**
    * `correct_answer`: Chuyển từ `String` sang `Integer` (để lưu index 0-3 như API yêu cầu).
    * `options`: Đang là `@Lob String`. Có thể giữ nguyên (lưu dạng JSON string `["A","B","C","D"]`) nhưng cần có hàm parse khi map sang DTO (hoặc dùng `@Type(JsonType.class)` nếu có thư viện, hoặc custom converter).
* **Thêm các fields:**
    * `order_index` (Integer)
    * `explanation` (TEXT, nullable)
    * `deleted_at` (LocalDateTime - nullable) cho Soft Delete.
* **Logic:** Thêm annotation Soft Delete tương tự `Quiz`.

### 1.3 `QuizResult` & `QuizAnswerDetail` (Cập nhật nhỏ)
File hiện tại: `QuizResult.java`, `QuizAnswerDetail.java`
* `QuizResult` cần thêm field `duration_seconds` (thời gian user đã làm bài) để map với DTO khi trả về lịch sử.
* Cột `takenDate` đổi tên biến thành `completedAt` trong DTO.

### 1.4 Thêm mới Entity: `QuizSession`
API Contract yêu cầu `/quizzes/:quizId/start` sinh ra một `sessionId`. Cần tạo mới `QuizSession.java` để track tiến trình làm bài:
* `session_id` (UUID, PK)
* `quiz_id` (FK tới Quiz)
* `uid` (FK tới User)
* `started_at` (LocalDateTime)
* `expires_at` (LocalDateTime)
* `is_submitted` (Boolean, default `false`)

---

## 2. Cấu Trúc DTOs Cần Tạo

Tạo package `dto/quiz/`.

### 2.1 Cho Staff/Admin:
* `QuizStaffResponse`: Chứa đầy đủ thông tin (kể cả audit `createdBy`, `updatedBy`).
* `CreateQuizRequest`: Bắt buộc truyền `title`, `description`, `questions[]`,...
* `UpdateQuizRequest`: Các field optional, không bao gồm `questions`.
* `QuestionRequest` / `QuestionResponse`: Định dạng DTO cho việc thêm/sửa câu hỏi (`options` là `List<String>`, `correctAnswer` là số).

### 2.2 Cho Customer:
* `QuizCustomerResponse`: Tương tự StaffResponse nhưng ẩn các field Audit.
* `QuizStartResponse`: Chứa `sessionId`, list câu hỏi (bao gồm cả `correctAnswer` theo spec).
* `QuizSubmitRequest`: Chứa `sessionId`, list `answers [{ questionId, selectedAnswer }]`, `durationSeconds`.
* `QuizSubmitResponse`: Trả về điểm, `correctAnswers[]` (mảng index), `wrongAnswers[]`.

---

## 3. Phân Chia API & Controllers

Tách làm 2 Controllers để dễ dàng setup Spring Security `@PreAuthorize` và phân rõ luồng.

### 3.1 `StaffQuizController` (`/api/v1/staff/quizzes`)
> Yêu cầu: `@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")`
* `GET /` - Danh sách quiz (phân trang, filter theo `grade`, search).
* `GET /{quizId}` - Chi tiết quiz kèm list questions.
* `POST /` - Tạo mới Quiz.
* `PUT /{quizId}` - Cập nhật thông tin chung Quiz.
* `DELETE /{quizId}` - Xóa Quiz (Soft Delete).
* `POST /{quizId}/questions` - Thêm 1 câu hỏi.
* `PUT /{quizId}/questions/{questionId}` - Sửa 1 câu hỏi.
* `DELETE /{quizId}/questions/{questionId}` - Xóa câu hỏi (Soft Delete).
* `PUT /{quizId}/questions/reorder` - Sắp xếp lại list câu hỏi (Cập nhật lại `order_index`).

### 3.2 `QuizController` (`/api/v1/quizzes`)
> Yêu cầu: Quyền `CUSTOMER` (hoặc public một phần đối với list).
* `GET /` - Danh sách quiz hiển thị cho user.
* `GET /{quizId}` - Chi tiết trước khi làm bài.
* `POST /{quizId}/start` - Sinh `QuizSession`, **Tăng `play_count` của Quiz lên 1**. Trả về session và list câu hỏi.
* `POST /submit` - Xử lý nộp bài:
    * Check `sessionId` hợp lệ, chưa hết hạn và chưa `is_submitted`.
    * Tính điểm dựa trên request và logic chấm của backend.
    * Lưu data vào `QuizResult` và `QuizAnswerDetail`.
    * Đánh dấu session là `is_submitted = true`.
* `GET /results/me` - Truy vấn DB bảng `QuizResult` theo User hiện tại (lấy lịch sử).

---

## 4. DB Migration / Setup

* Vì Spring Boot đang dùng Hibernate (`ddl-auto=update`), các cột mới (`deleted_at`, `duration_seconds`...) sẽ tự động được thêm vào DB mà không break data cũ (do đều cho phép `nullable`).
* **Lưu ý đặc biệt:** Cột `correct_answer` trong bảng `Question` đang là `VARCHAR(255)`. Nếu đổi type trong Entity sang `Integer` (để lưu index), Hibernate update có thể sẽ báo lỗi cast type trên database cũ. Cần tự tay viết script `ALTER TABLE` hoặc xóa data/bảng `Question` cũ nếu đang ở môi trường dev.

---

## 5. Checklist Triển Khai (Dành cho Dev)

- [ ] **Bước 1: Sửa Entities**
    - Cập nhật `Quiz.java` (thêm fields, soft delete).
    - Cập nhật `Question.java` (đổi type `correct_answer`, thêm fields, soft delete).
    - Cập nhật `QuizResult.java` (thêm `duration_seconds`).
    - Tạo mới `QuizSession.java`.
- [ ] **Bước 2: Cập nhật / Tạo Repositories**
    - Thêm custom queries vào `QuizRepository` (`findByTitleContainingIgnoreCase`, `findByGradeAndEra`...).
    - Tạo `QuizSessionRepository`.
- [ ] **Bước 3: Code DTOs**
    - Tạo toàn bộ Request/Response DTO cho Staff và Customer theo spec.
- [ ] **Bước 4: Viết `QuizService`**
    - Xây dựng hàm tạo/sửa Quiz (Staff).
    - Hàm `startQuiz`: Sinh session, lưu DB, tăng playCount.
    - Hàm `submitQuiz`: Logic chấm điểm (so sánh mảng `answers` request với `correct_answer` DB), lưu DB.
- [ ] **Bước 5: Viết Controllers**
    - Hoàn thiện `StaffQuizController` (gắn Security).
    - Hoàn thiện `QuizController`.
- [ ] **Bước 6: Testing**
    - Test flow tạo quiz (Staff) -> Xem list (Customer) -> Start -> Submit -> Xem lịch sử.