# 📋 USER STORIES: QUESTION BANK
> **Sprint:** Sprint 7 (16/10/2026 – 30/10/2026)  
> **Phân hệ:** Quiz Module — Question Bank (Ngân hàng câu hỏi)

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Người phụ trách | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP7-12`** | Quản lý Question Bank độc lập | `CONTENT_ADMIN` / `SYSTEM_ADMIN` | 🟢 Bao (Bao-2005) | `GET/POST/PUT/PATCH /api/v1/staff/question-bank` |
| **`US-SP7-13`** | Gắn / Gỡ câu hỏi từ Bank vào Quiz | `CONTENT_ADMIN` / `SYSTEM_ADMIN` | 🟢 Bao (Bao-2005) | `POST/DELETE /api/v1/staff/question-bank/{questionId}/attach/{quizId}` |

---

### 🆔 US-SP7-12: Quản lý Question Bank độc lập
* **Actor:** `CONTENT_ADMIN`, `SYSTEM_ADMIN`
* **Thời gian thực hiện:** 16/10/2026 – 25/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")`
  - **Phạm vi bảo vệ dữ liệu:** Toàn hệ thống
* **User Story:**  
  Là staff, tôi muốn tạo và quản lý câu hỏi trong ngân hàng câu hỏi độc lập (không gắn với quiz cụ thể nào), để có thể tái sử dụng câu hỏi cho nhiều quiz khác nhau.

* **Acceptance Criteria (AC):**
  1. **Tạo câu hỏi bank:** `POST /api/v1/staff/question-bank` với `{ content, questionType, questionData, explanation }`.
     - Câu hỏi được tạo với `quiz_id = null`.
     - `questionData` bắt buộc (không thể null).
     - `HTTP 201`: `QuestionBankResponse`.
  2. **Lấy danh sách:** `GET /api/v1/staff/question-bank?questionType=&search=&page=0&size=20`.
     - Lọc theo `questionType` (`MULTIPLE_CHOICE`, `FILL_IN_BLANK`, `MATCHING`) — tuỳ chọn.
     - `questionData` trong response là **FULL** (bao gồm đáp án đúng, dành cho staff).
     - `assignedToQuiz = true` nếu câu hỏi đã được gắn vào ít nhất 1 quiz.
  3. **Cập nhật:** `PUT /api/v1/staff/question-bank/{questionId}` — partial update cho `content`, `explanation`, `questionType`, `questionData`.
  4. **Xoá mềm:** `PATCH /api/v1/staff/question-bank/{questionId}/soft-delete` — set `deletedAt = NOW()`. Câu hỏi đã xoá mềm không xuất hiện khi learner làm bài.
  5. `HTTP 404` nếu `questionId` không tồn tại hoặc đã bị xoá mềm (khi update).
  6. `HTTP 400` nếu `questionType` truyền vào không hợp lệ.

---

### 🆔 US-SP7-13: Gắn / Gỡ câu hỏi từ Bank vào Quiz
* **Actor:** `CONTENT_ADMIN`, `SYSTEM_ADMIN`
* **Thời gian thực hiện:** 16/10/2026 – 25/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")`
  - **Phạm vi bảo vệ dữ liệu:** Toàn hệ thống
* **User Story:**  
  Là staff, tôi muốn gắn câu hỏi từ ngân hàng vào một quiz, và gỡ ra khi không cần nữa, để linh hoạt tái sử dụng câu hỏi mà không xoá nó.

* **Acceptance Criteria (AC):**
  1. **Gắn câu hỏi vào quiz:** `POST /api/v1/staff/question-bank/{questionId}/attach/{quizId}`.
     - Tạo 1 row trong bảng `quiz_question` (link table).
     - `orderIndex` tự động = max hiện tại + 1.
     - Câu hỏi đã bị xoá mềm → `HTTP 400`.
     - Câu hỏi đã gắn vào quiz đó rồi → `HTTP 409`.
     - `HTTP 200`: void.
  2. **Gỡ câu hỏi khỏi quiz:** `DELETE /api/v1/staff/question-bank/{questionId}/detach/{quizId}`.
     - Xoá row trong `quiz_question`, câu hỏi vẫn còn trong bank.
     - `HTTP 404` nếu link không tồn tại.
     - `HTTP 200`: void.
  3. Sau khi gắn, câu hỏi từ bank **xuất hiện trong quiz khi learner làm bài** (truy vấn union cả `question.quiz_id` và bảng `quiz_question`).
  4. Câu hỏi được gắn có thể từ **bất kỳ loại nào** (MC, FILL, MATCHING).
