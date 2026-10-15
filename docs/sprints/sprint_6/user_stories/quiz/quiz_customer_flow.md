# 📋 USER STORIES: QUIZ — LUỒNG LEARNER
> **Sprint:** Sprint 7 (16/10/2026 – 30/10/2026)  
> **Phân hệ:** Quiz Module — Customer Flow

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Người phụ trách | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP7-01`** | Duyệt danh sách Quiz | `CUSTOMER` (hoặc guest) | 🟢 Bao (Bao-2005) | `GET /api/v1/quizzes` |
| **`US-SP7-02`** | Bắt đầu phiên làm bài | `CUSTOMER` | 🟢 Bao (Bao-2005) | `POST /api/v1/quizzes/{quizId}/start` |
| **`US-SP7-03`** | Nộp bài và nhận kết quả | `CUSTOMER` | 🟢 Bao (Bao-2005) | `POST /api/v1/quizzes/submit` |
| **`US-SP7-04`** | Xem lịch sử làm bài | `CUSTOMER` | 🟢 Bao (Bao-2005) | `GET /api/v1/quizzes/results/me` |
| **`US-SP7-05`** | Xem chi tiết kết quả phiên | `CUSTOMER` | 🟢 Bao (Bao-2005) | `GET /api/v1/quizzes/results/me/{sessionId}` |
| **`US-SP7-06`** | Đánh giá sao cho Quiz | `CUSTOMER` | 🟢 Bao (Bao-2005) | `POST /api/v1/quizzes/{quizId}/rating` |
| **`US-SP7-07`** | Báo cáo câu hỏi lỗi | `CUSTOMER` | 🟢 Bao (Bao-2005) | `POST /api/v1/quizzes/questions/{questionId}/report` |

---

### 🆔 US-SP7-01: Duyệt danh sách Quiz (Browse Quizzes)
* **Actor:** `CUSTOMER` hoặc guest (không cần đăng nhập)
* **Thời gian thực hiện:** 16/10/2026 – 19/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** Public (không cần token)
  - **Phạm vi bảo vệ dữ liệu:** Toàn hệ thống — chỉ trả về quiz có `isPublished = true` và `deletedAt IS NULL`
* **User Story:**  
  Là learner, tôi muốn xem danh sách các quiz đang hoạt động (có thể lọc theo từ khóa và bối cảnh lịch sử), để tìm quiz phù hợp với chủ đề tôi muốn ôn tập.

* **Acceptance Criteria (AC):**
  1. `GET /api/v1/quizzes` trả về mảng (không phân trang).
  2. Hỗ trợ query params tuỳ chọn: `?search=` (tìm theo tiêu đề) và `?contextId=` (lọc theo bối cảnh lịch sử).
  3. Nếu user đã đăng nhập, `playCount` = số lần user đó đã hoàn thành quiz này. Nếu chưa đăng nhập, `playCount = 0`.
  4. Kết quả trả về: `HTTP 200` với mảng `QuizCustomerResponse`.

---

### 🆔 US-SP7-02: Bắt đầu phiên làm bài (Start Quiz Session)
* **Actor:** `CUSTOMER`
* **Thời gian thực hiện:** 16/10/2026 – 19/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasRole('CUSTOMER')")`
  - **Phạm vi bảo vệ dữ liệu:** Cá nhân (user_id)
* **User Story:**  
  Là learner, tôi muốn bắt đầu một phiên làm bài quiz, để nhận được danh sách câu hỏi (đã ẩn đáp án) và một sessionId để nộp bài sau này.

* **Acceptance Criteria (AC):**
  1. `POST /api/v1/quizzes/{quizId}/start?limitedTime=N` tạo một `QuizSession` mới với `endTime = null`.
  2. `limitedTime` (giây, tuỳ chọn) được lưu server-side trong session, KHÔNG trả về trong response.
  3. Câu hỏi trả về đã **sanitized** — đáp án đúng bị ẩn theo từng loại:
     - `MULTIPLE_CHOICE`: `questionData` chỉ có `options`, không có `correctOptionIds`
     - `FILL_IN_BLANK`: `questionData = null`
     - `MATCHING`: `questionData` chỉ có `leftItems` + `rightItems`, không có `correctPairs`
  4. `HTTP 200`: `{ sessionId, quizId, title, questions[] }`
  5. `HTTP 404`: quiz không tồn tại hoặc đã bị xóa.

---

### 🆔 US-SP7-03: Nộp bài và nhận kết quả (Submit Quiz)
* **Actor:** `CUSTOMER`
* **Thời gian thực hiện:** 16/10/2026 – 22/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasRole('CUSTOMER')")`
  - **Phạm vi bảo vệ dữ liệu:** Cá nhân — chỉ nộp được session của chính mình
* **User Story:**  
  Là learner, tôi muốn nộp bài quiz với tất cả câu trả lời của mình, để nhận điểm và biết câu nào đúng/sai.

* **Acceptance Criteria (AC):**
  1. `POST /api/v1/quizzes/submit` nhận `{ sessionId, answers: [{ questionId, answerData }] }`.
  2. `answerData` có cấu trúc khác nhau theo `questionType` (xem FE contracts).
  3. Hệ thống chấm điểm: mỗi câu đúng = 1 điểm. Logic chấm theo loại câu hỏi.
  4. Nếu user không nộp đáp án cho một câu → câu đó tính sai.
  5. Session chỉ được nộp một lần — nếu `endTime IS NOT NULL` → `HTTP 400`.
  6. Chỉ chủ sở hữu session mới được nộp → nếu sai user → `HTTP 403`.
  7. `HTTP 200`: `{ resultId, score, totalQuestions, percentage, correctAnswers[], wrongAnswers[], startTime, endTime }`.
  8. `correctAnswers`/`wrongAnswers` là mảng chứa **index 0-based** của câu hỏi trong danh sách.

---

### 🆔 US-SP7-04: Xem lịch sử làm bài (Quiz History)
* **Actor:** `CUSTOMER`
* **Thời gian thực hiện:** 16/10/2026 – 19/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasRole('CUSTOMER')")`
  - **Phạm vi bảo vệ dữ liệu:** Cá nhân (user_id)
* **User Story:**  
  Là learner, tôi muốn xem danh sách các phiên làm bài đã hoàn thành của mình, để theo dõi tiến trình học tập.

* **Acceptance Criteria (AC):**
  1. `GET /api/v1/quizzes/results/me?page=0&size=10` trả về danh sách phân trang.
  2. Chỉ trả về các session có `endTime IS NOT NULL` (đã hoàn thành).
  3. `HTTP 200`: `PaginatedResponse<QuizHistoryResponse>` với `{ sessionId, quizId, quizTitle, score, totalQuestions, percentage, completedAt }`.

---

### 🆔 US-SP7-05: Xem chi tiết kết quả phiên (Session Detail)
* **Actor:** `CUSTOMER`
* **Thời gian thực hiện:** 16/10/2026 – 22/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasRole('CUSTOMER')")`
  - **Phạm vi bảo vệ dữ liệu:** Cá nhân — chỉ xem session của chính mình
* **User Story:**  
  Là learner, sau khi nộp bài, tôi muốn xem chi tiết từng câu hỏi kèm đáp án đúng và đáp án tôi đã chọn, để hiểu chỗ mình sai và học từ đó.

* **Acceptance Criteria (AC):**
  1. `GET /api/v1/quizzes/results/me/{sessionId}` chỉ trả về session của chính user.
  2. Session phải đã hoàn thành (`endTime IS NOT NULL`) → nếu chưa xong → `HTTP 400`.
  3. Mỗi câu hỏi trong response có:
     - `questionData` **FULL** (kể cả `correctOptionIds`/`correctPairs`/`acceptedAnswers`)
     - `submittedAnswer`: đáp án learner đã nộp
     - `isCorrect`: kết quả chấm
     - `explanation`: giải thích (nếu có)
  4. `HTTP 403` nếu session không thuộc về user đang đăng nhập.

---

### 🆔 US-SP7-06: Đánh giá sao cho Quiz (Rate Quiz)
* **Actor:** `CUSTOMER`
* **Thời gian thực hiện:** 16/10/2026 – 19/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasRole('CUSTOMER')")`
  - **Phạm vi bảo vệ dữ liệu:** Cá nhân (1 đánh giá / user / quiz)
* **User Story:**  
  Là learner, tôi muốn đánh giá quiz từ 1–5 sao, để chia sẻ trải nghiệm với cộng đồng.

* **Acceptance Criteria (AC):**
  1. `POST /api/v1/quizzes/{quizId}/rating` nhận `{ "value": 1-5 }`.
  2. Nếu user đã đánh giá → cập nhật (upsert), không tạo mới.
  3. `HTTP 400` nếu `value < 1` hoặc `value > 5`.
  4. `HTTP 200`: `{ rating (trung bình, làm tròn 1 chữ số), ratingCount, myRating }`.
  5. `GET /api/v1/quizzes/{quizId}/rating/me` → `{ myRating }` (null nếu chưa đánh giá).

---

### 🆔 US-SP7-07: Báo cáo câu hỏi lỗi (Report Question)
* **Actor:** `CUSTOMER`
* **Thời gian thực hiện:** 16/10/2026 – 19/10/2026
* **Phụ trách:** 🟢 Bao (Bao-2005)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasRole('CUSTOMER')")`
  - **Phạm vi bảo vệ dữ liệu:** Cá nhân
* **User Story:**  
  Là learner, tôi muốn báo cáo câu hỏi có lỗi sai nội dung, để staff có thể kiểm tra và sửa.

* **Acceptance Criteria (AC):**
  1. `POST /api/v1/quizzes/questions/{questionId}/report` nhận `{ "reason": "..." }` (tuỳ chọn).
  2. Tạo một `QuestionReport` với `status = "OPEN"`.
  3. `HTTP 404` nếu questionId không tồn tại.
  4. `HTTP 200`: void (thành công).
