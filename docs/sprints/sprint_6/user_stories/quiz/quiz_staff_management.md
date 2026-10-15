# 📋 USER STORIES: QUIZ — QUẢN LÝ NỘI DUNG (STAFF)
> **Sprint:** Sprint 7 (16/10/2026 – 30/10/2026)  
> **Phân hệ:** Quiz Module — Staff Management

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Người phụ trách | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP7-08`** | Quản lý Quiz (CRUD + Import CSV) | `CONTENT_ADMIN` / `SYSTEM_ADMIN` | 🟢 Bao (Bao-2005) | `GET/POST/PUT/DELETE /api/v1/staff/quizzes` |
| **`US-SP7-09`** | Quản lý câu hỏi trong Quiz | `CONTENT_ADMIN` / `SYSTEM_ADMIN` | 🟢 Bao (Bao-2005) | `POST/PUT/DELETE /api/v1/staff/quizzes/{quizId}/questions` |
| **`US-SP7-10`** | Xem lịch sử phiên làm bài (Admin) | `SYSTEM_ADMIN` | 🟢 Bao (Bao-2005) | `GET /api/v1/staff/quizzes/sessions` |
| **`US-SP7-11`** | Xử lý báo cáo câu hỏi | `CONTENT_ADMIN` / `SYSTEM_ADMIN` | 🟢 Bao (Bao-2005) | `GET/PATCH /api/v1/staff/quizzes/reports` |

---

### 🆔 US-SP7-08: Quản lý Quiz — CRUD và Import CSV
* **Actor:** `CONTENT_ADMIN`, `SYSTEM_ADMIN`
* **Thời gian thực hiện:** 16/10/2026 – 25/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")`
  - **Phạm vi bảo vệ dữ liệu:** Toàn hệ thống
* **User Story:**  
  Là staff quản lý nội dung, tôi muốn tạo, chỉnh sửa, xoá mềm/cứng và import hàng loạt quiz từ file CSV, để duy trì kho nội dung quiz cho hệ thống.

* **File Template Mẫu:**
  - [`quiz_import_template.csv`](quiz_import_template.csv)
  - Bảng quy chuẩn các cột dữ liệu:

    | Tên Cột (Header) | Kiểu Dữ Liệu | Bắt buộc | Mô tả & Ràng buộc Nghiệp vụ |
    | :--- | :--- | :---: | :--- |
    | `title` | String | **Có** | Tiêu đề quiz. Các hàng có cùng title → gom thành 1 quiz |
    | `contextId` | UUID | **Có** | ID của HistoricalContext (phải tồn tại trong DB) |
    | `level` | String | **Có** | `EASY` / `MEDIUM` / `HARD` |
    | `questionContent` | String | **Có** | Nội dung câu hỏi |
    | `option1` | String | **Có** | Lựa chọn A |
    | `option2` | String | **Có** | Lựa chọn B |
    | `option3` | String | **Có** | Lựa chọn C |
    | `option4` | String | **Có** | Lựa chọn D |
    | `correctAnswer` | Integer (0-3) | **Có** | Index 0-based của đáp án đúng (0=A, 1=B, 2=C, 3=D) |
    | `explanation` | String | Không | Giải thích đáp án (để trống được) |

* **Acceptance Criteria (AC):**
  1. **Lấy danh sách:** `GET /api/v1/staff/quizzes?search=&era=&page=0&size=10` → `PaginatedResponse<QuizStaffResponse>`. Hỗ trợ filter theo `era` (ANCIENT/MEDIEVAL/MODERN/CONTEMPORARY).
  2. **Lấy chi tiết:** `GET /api/v1/staff/quizzes/{quizId}` → `QuizStaffResponse` bao gồm `questions[]` với `questionData` **FULL** (không sanitize).
  3. **Tạo mới:** `POST /api/v1/staff/quizzes` với `{ title, contextId, level, isPublished, questions[] }`. Kiểm tra title không trùng (case-insensitive). `HTTP 409` nếu trùng.
  4. **Cập nhật:** `PUT /api/v1/staff/quizzes/{quizId}` với `{ title?, contextId?, level?, isPublished? }`. Tất cả field tuỳ chọn (partial update).
  5. **Xoá cứng:** `DELETE /api/v1/staff/quizzes/{quizId}` — xoá hoàn toàn.
  6. **Xoá mềm:** `PATCH /api/v1/staff/quizzes/{quizId}/soft-delete` — set `deletedAt = NOW()`.
  7. **Import CSV:**
     - `POST /api/v1/staff/quizzes/import` (multipart/form-data, key = `file`).
     - File phải đủ 10 cột header bắt buộc, định dạng UTF-8.
     - Mỗi title duy nhất tạo 1 quiz. Quiz import được tạo ở trạng thái **draft** (`isPublished = false`).
     - Quiz có title trùng hoặc lỗi validation → bị skip, ghi vào `errors[]`.
     - `HTTP 200`: `{ totalQuizzesAttempted, successCount, skippedCount, errors[], imported[] }`.

---

### 🆔 US-SP7-09: Quản lý câu hỏi trong Quiz
* **Actor:** `CONTENT_ADMIN`, `SYSTEM_ADMIN`
* **Thời gian thực hiện:** 16/10/2026 – 25/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")`
  - **Phạm vi bảo vệ dữ liệu:** Câu hỏi phải thuộc quiz được chỉ định
* **User Story:**  
  Là staff, tôi muốn thêm, sửa, xoá câu hỏi trực tiếp trong một quiz cụ thể, để kiểm soát nội dung từng quiz.

* **Acceptance Criteria (AC):**
  1. **Thêm câu hỏi:** `POST /api/v1/staff/quizzes/{quizId}/questions` với `{ content, questionType, questionData, explanation }`. Trả về `QuestionResponse`.
  2. `questionData` phải có đủ dữ liệu theo từng loại. Thiếu `questionData` → `HTTP 400`.
  3. **Cập nhật:** `PUT /api/v1/staff/quizzes/{quizId}/questions/{questionId}` — partial update cho `content`, `explanation`, `questionType`, `questionData`.
  4. Câu hỏi phải thuộc quiz được truyền vào. Sai quiz → `HTTP 400`.
  5. **Xoá:** `DELETE /api/v1/staff/quizzes/{quizId}/questions/{questionId}` — hard delete.

---

### 🆔 US-SP7-10: Xem lịch sử phiên làm bài (Admin)
* **Actor:** `SYSTEM_ADMIN`
* **Thời gian thực hiện:** 16/10/2026 – 22/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasRole('SYSTEM_ADMIN')")`
  - **Phạm vi bảo vệ dữ liệu:** Toàn hệ thống (không giới hạn theo user)
* **User Story:**  
  Là system admin, tôi muốn xem lịch sử làm bài của tất cả người dùng hoặc lọc theo một user cụ thể, để giám sát hoạt động học tập trên hệ thống.

* **Acceptance Criteria (AC):**
  1. `GET /api/v1/staff/quizzes/sessions?page=0&size=10` → tất cả sessions đã hoàn thành.
  2. `GET /api/v1/staff/quizzes/sessions?userId={uuid}&page=0&size=10` → sessions của một user cụ thể.
  3. `GET /api/v1/staff/quizzes/sessions/{sessionId}` → chi tiết đầy đủ 1 session (không kiểm tra ownership).
  4. `HTTP 200`: `PaginatedResponse<QuizHistoryResponse>` hoặc `QuizSessionDetailResponse`.

---

### 🆔 US-SP7-11: Xử lý báo cáo câu hỏi
* **Actor:** `CONTENT_ADMIN`, `SYSTEM_ADMIN`
* **Thời gian thực hiện:** 16/10/2026 – 22/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")`
  - **Phạm vi bảo vệ dữ liệu:** Toàn hệ thống
* **User Story:**  
  Là staff, tôi muốn xem danh sách các báo cáo câu hỏi lỗi từ learner và đánh dấu đã xử lý, để đảm bảo chất lượng nội dung quiz.

* **Acceptance Criteria (AC):**
  1. `GET /api/v1/staff/quizzes/reports?status=OPEN&page=0&size=10` → danh sách báo cáo phân trang. `status` có thể là `OPEN` hoặc `RESOLVED` (tuỳ chọn, lấy tất cả nếu bỏ trống).
  2. Response mỗi item: `{ reportId, questionId, questionContent, quizId, quizTitle, reportedBy, reason, status, createdAt }`. `quizId`/`quizTitle` có thể `null` nếu câu hỏi thuộc bank (không gắn quiz).
  3. `PATCH /api/v1/staff/quizzes/reports/{reportId}/resolve` → set `status = "RESOLVED"`.
  4. `HTTP 404` nếu `reportId` không tồn tại.
