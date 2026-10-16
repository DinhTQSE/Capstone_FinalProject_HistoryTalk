# 📋 QUIZ MODULE — FE API CONTRACTS
> **Sprint:** Sprint 7 (16/10/2026 – 30/10/2026)  
> **Mục đích:** Đặc tả đầy đủ request / response cho Frontend  
> **Base URL:** `/api/v1`  
> **Auth:** Bearer JWT (Authorization: Bearer `<token>`)

---

## 1. CUSTOMER ENDPOINTS

### GET `/quizzes`
> Public — không cần token. `playCount = 0` nếu chưa đăng nhập.

**Query params:** `search` (string, opt), `contextId` (UUID string, opt)

**Response 200:**
```json
{
  "status": "success",
  "data": [
    {
      "quizId": "uuid",
      "title": "Trận Bạch Đằng 938",
      "level": "EASY",
      "era": "ANCIENT",
      "playCount": 2,
      "contextTitle": "Thời kỳ Bắc thuộc"
    }
  ]
}
```

---

### GET `/quizzes/{quizId}`
> Public.

**Response 200:** Cùng shape `QuizCustomerResponse` như trên.  
**Response 404:** quiz không tồn tại hoặc đã xoá.

---

### POST `/quizzes/{quizId}/start`
> Yêu cầu `CUSTOMER` role + JWT.

**Query params:** `limitedTime` (integer, giây, opt)

**Response 200:**
```json
{
  "status": "success",
  "data": {
    "sessionId": "uuid",
    "quizId": "uuid",
    "title": "Trận Bạch Đằng 938",
    "questions": [
      {
        "questionId": "uuid",
        "content": "Trận Bạch Đằng diễn ra năm nào?",
        "questionType": "MULTIPLE_CHOICE",
        "questionData": {
          "options": [
            { "id": "0", "text": "938" },
            { "id": "1", "text": "939" },
            { "id": "2", "text": "940" },
            { "id": "3", "text": "941" }
          ]
        },
        "explanation": null
      },
      {
        "questionId": "uuid",
        "content": "Ai chỉ huy trận Bạch Đằng?",
        "questionType": "FILL_IN_BLANK",
        "questionData": null,
        "explanation": "Ngô Quyền lãnh đạo quân dân đánh tan quân Nam Hán."
      },
      {
        "questionId": "uuid",
        "content": "Nối đúng nhân vật với sự kiện",
        "questionType": "MATCHING",
        "questionData": {
          "leftItems": [
            { "id": "L1", "text": "Ngô Quyền" },
            { "id": "L2", "text": "Lý Thường Kiệt" }
          ],
          "rightItems": [
            { "id": "R1", "text": "Bạch Đằng 938" },
            { "id": "R2", "text": "Sông Như Nguyệt 1077" }
          ]
        },
        "explanation": null
      }
    ]
  }
}
```

> ⚠️ **Lưu ý sanitization:**
> - `MULTIPLE_CHOICE`: `questionData` chỉ có `options`, KHÔNG có `correctOptionIds`
> - `FILL_IN_BLANK`: `questionData = null`
> - `MATCHING`: `questionData` chỉ có `leftItems` + `rightItems`, KHÔNG có `correctPairs`

---

### POST `/quizzes/submit`
> Yêu cầu `CUSTOMER` role + JWT.

**Request body:**
```json
{
  "sessionId": "uuid",
  "answers": [
    {
      "questionId": "uuid-cau-mc",
      "answerData": { "selectedOptionIds": ["0"] }
    },
    {
      "questionId": "uuid-cau-fill",
      "answerData": { "answers": ["Ngô Quyền"] }
    },
    {
      "questionId": "uuid-cau-matching",
      "answerData": {
        "pairs": [
          { "leftId": "L1", "rightId": "R1" },
          { "leftId": "L2", "rightId": "R2" }
        ]
      }
    }
  ]
}
```

> ⚠️ `answerData` format theo `questionType`:
> | Type | answerData |
> |------|-----------|
> | `MULTIPLE_CHOICE` | `{ "selectedOptionIds": ["0"] }` — mảng id của option được chọn |
> | `FILL_IN_BLANK` | `{ "answers": ["nội dung"] }` — mảng string câu trả lời |
> | `MATCHING` | `{ "pairs": [{ "leftId": "L1", "rightId": "R1" }] }` |

**Response 200:**
```json
{
  "status": "success",
  "data": {
    "resultId": "uuid-session",
    "score": 2,
    "totalQuestions": 3,
    "percentage": 66.67,
    "startTime": "2026-10-16T00:10:00",
    "endTime": "2026-10-16T00:15:23",
    "correctAnswers": [0, 2],
    "wrongAnswers": [1]
  }
}
```
> `correctAnswers` và `wrongAnswers` là **index 0-based** của câu trong mảng `questions` từ lúc start.

**Response 400:** Quiz đã nộp rồi.  
**Response 403:** Session không thuộc về user này.

---

### GET `/quizzes/results/me`
> Yêu cầu `CUSTOMER` + JWT.

**Query params:** `page` (default 0), `size` (default 10)

**Response 200:**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "sessionId": "uuid",
        "quizId": "uuid",
        "quizTitle": "Trận Bạch Đằng 938",
        "score": 8,
        "totalQuestions": 10,
        "percentage": 80.0,
        "completedAt": "2026-10-16T00:15:23"
      }
    ],
    "currentPage": 0,
    "pageSize": 10,
    "totalElements": 5,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

---

### GET `/quizzes/results/me/{sessionId}`
> Yêu cầu `CUSTOMER` + JWT. Session phải đã hoàn thành và thuộc về user.

**Response 200:**
```json
{
  "status": "success",
  "data": {
    "sessionId": "uuid",
    "quizId": "uuid",
    "quizTitle": "Trận Bạch Đằng 938",
    "score": 2,
    "totalQuestions": 3,
    "percentage": 66.67,
    "limitedTime": 300,
    "startedAt": "2026-10-16T00:10:00",
    "completedAt": "2026-10-16T00:15:23",
    "questions": [
      {
        "questionId": "uuid",
        "content": "Trận Bạch Đằng diễn ra năm nào?",
        "questionType": "MULTIPLE_CHOICE",
        "questionData": {
          "options": [{ "id": "0", "text": "938" }, { "id": "1", "text": "939" }],
          "correctOptionIds": ["0"]
        },
        "submittedAnswer": { "selectedOptionIds": ["0"] },
        "isCorrect": true,
        "explanation": null
      },
      {
        "questionId": "uuid",
        "content": "Ai chỉ huy?",
        "questionType": "FILL_IN_BLANK",
        "questionData": {
          "acceptedAnswers": ["Ngô Quyền", "ngo quyen"],
          "caseSensitive": false,
          "trimWhitespace": true
        },
        "submittedAnswer": { "answers": ["Nguyễn Huệ"] },
        "isCorrect": false,
        "explanation": "Ngô Quyền lãnh đạo."
      }
    ]
  }
}
```
> Sau khi nộp bài, `questionData` được trả về **FULL** (bao gồm đáp án đúng).

---

### POST `/quizzes/{quizId}/rating`
> Yêu cầu `CUSTOMER` + JWT.

**Request body:** `{ "value": 4 }` (integer 1–5)

**Response 200:**
```json
{
  "status": "success",
  "data": { "rating": 4.2, "ratingCount": 15, "myRating": 4 }
}
```

---

### GET `/quizzes/{quizId}/rating/me`
> Yêu cầu `CUSTOMER` + JWT.

**Response 200:**
```json
{ "status": "success", "data": { "myRating": 4 } }
```
> `myRating = null` nếu chưa đánh giá.

---

### POST `/quizzes/questions/{questionId}/report`
> Yêu cầu `CUSTOMER` + JWT.

**Request body:** `{ "reason": "Đáp án sai" }` (tuỳ chọn)

**Response 200:** `{ "status": "success", "data": null }`

---

## 2. STAFF QUIZ ENDPOINTS

> **Auth:** Yêu cầu `CONTENT_ADMIN` hoặc `SYSTEM_ADMIN`.  
> Tất cả `questions[]` trong response của staff có `questionData` **FULL** (không sanitize).

---

### GET `/staff/quizzes`
**Query:** `search`, `era` (ANCIENT/MEDIEVAL/MODERN/CONTEMPORARY), `page`, `size`

**Response 200:** `PaginatedResponse<QuizStaffResponse>`
```json
{
  "quizId": "uuid",
  "title": "Trận Bạch Đằng 938",
  "era": "ANCIENT",
  "level": "EASY",
  "playCount": 42,
  "contextId": "uuid",
  "contextTitle": "Thời kỳ Bắc thuộc",
  "createdBy": "staff_username",
  "createdDate": "2026-10-01T08:00:00",
  "updatedDate": "2026-10-15T10:30:00",
  "isPublished": true,
  "status": "ACTIVE",
  "questions": [...]
}
```
> `status`: `ACTIVE` (published) | `DRAFT` (unpublished) | `INACTIVE` (soft-deleted)

---

### POST `/staff/quizzes`
**Request body:**
```json
{
  "title": "Trận Bạch Đằng 938",
  "contextId": "uuid",
  "level": "EASY",
  "isPublished": false,
  "questions": [
    {
      "content": "Năm diễn ra trận Bạch Đằng?",
      "questionType": "MULTIPLE_CHOICE",
      "questionData": {
        "options": [
          { "id": "0", "text": "938" },
          { "id": "1", "text": "939" }
        ],
        "correctOptionIds": ["0"]
      },
      "explanation": "Năm 938"
    }
  ]
}
```
**Response 200:** `QuizStaffResponse`  
**Response 409:** Title đã tồn tại.

---

### PUT `/staff/quizzes/{quizId}`
**Request body** (tất cả tuỳ chọn):
```json
{
  "title": "Tên mới",
  "contextId": "uuid",
  "level": "MEDIUM",
  "isPublished": true
}
```
**Response 200:** `QuizStaffResponse`

---

### POST `/staff/quizzes/{quizId}/questions`
**Request body:**
```json
{
  "content": "Nội dung câu hỏi",
  "questionType": "FILL_IN_BLANK",
  "questionData": {
    "acceptedAnswers": ["938", "Năm 938"],
    "caseSensitive": false,
    "trimWhitespace": true
  },
  "explanation": "Giải thích"
}
```
**questionData theo từng loại:**

| `questionType` | Cấu trúc `questionData` |
|---|---|
| `MULTIPLE_CHOICE` | `{ "options": [{"id":"0","text":"..."}], "correctOptionIds": ["0"] }` |
| `FILL_IN_BLANK` | `{ "acceptedAnswers": ["938"], "caseSensitive": false, "trimWhitespace": true }` |
| `MATCHING` | `{ "leftItems": [{"id":"L1","text":"..."}], "rightItems": [{"id":"R1","text":"..."}], "correctPairs": [{"leftId":"L1","rightId":"R1"}] }` |

**Response 200:** `QuestionResponse` (full questionData)

---

### POST `/staff/quizzes/import`
> `Content-Type: multipart/form-data`, key = `file`

**Response 200:**
```json
{
  "status": "success",
  "data": {
    "totalQuizzesAttempted": 5,
    "successCount": 4,
    "skippedCount": 1,
    "errors": ["Quiz 'Test Quiz' skipped — duplicate title: ..."],
    "imported": [ /* mảng QuizStaffResponse */ ]
  }
}
```

---

### GET `/staff/quizzes/sessions`
> Chỉ `SYSTEM_ADMIN`.

**Query:** `userId` (UUID, opt), `page`, `size`  
**Response:** `PaginatedResponse<QuizHistoryResponse>`

---

### GET `/staff/quizzes/sessions/{sessionId}`
> Chỉ `SYSTEM_ADMIN`.  
**Response:** `QuizSessionDetailResponse` (cùng shape với customer nhưng không kiểm tra ownership)

---

### GET `/staff/quizzes/reports`
**Query:** `status` (OPEN/RESOLVED, opt), `page`, `size`

**Response 200:**
```json
{
  "data": {
    "content": [
      {
        "reportId": "uuid",
        "questionId": "uuid",
        "questionContent": "Nội dung câu hỏi",
        "quizId": "uuid hoặc null",
        "quizTitle": "Tên quiz hoặc null",
        "reportedBy": "username",
        "reason": "Đáp án sai",
        "status": "OPEN",
        "createdAt": "2026-10-16T00:30:00"
      }
    ]
  }
}
```

---

### PATCH `/staff/quizzes/reports/{reportId}/resolve`
**Response 200:** void

---

## 3. QUESTION BANK ENDPOINTS

> **Auth:** `CONTENT_ADMIN` hoặc `SYSTEM_ADMIN`.

---

### GET `/staff/question-bank`
**Query:** `questionType` (opt), `search` (opt), `page` (default 0), `size` (default 20)

**Response 200:** `PaginatedResponse<QuestionBankResponse>`
```json
{
  "questionId": "uuid",
  "content": "Câu hỏi này...",
  "explanation": "...",
  "questionType": "FILL_IN_BLANK",
  "questionData": {
    "acceptedAnswers": ["938"],
    "caseSensitive": false,
    "trimWhitespace": true
  },
  "assignedToQuiz": true
}
```

---

### POST `/staff/question-bank`
**Request body:** cùng shape với `POST /staff/quizzes/{quizId}/questions`  
**Response 201:** `QuestionBankResponse`

---

### PUT `/staff/question-bank/{questionId}`
**Request body** (tất cả tuỳ chọn): `content`, `explanation`, `questionType`, `questionData`  
**Response 200:** `QuestionBankResponse`

---

### PATCH `/staff/question-bank/{questionId}/soft-delete`
**Response 200:** void

---

### POST `/staff/question-bank/{questionId}/attach/{quizId}`
**Response 200:** void  
**Response 409:** Câu hỏi đã được gắn vào quiz này.  
**Response 400:** Câu hỏi đã bị xoá mềm.

---

### DELETE `/staff/question-bank/{questionId}/detach/{quizId}`
**Response 200:** void  
**Response 404:** Link không tồn tại.

---

## 4. ENUMS REFERENCE

| Enum | Giá trị hợp lệ |
|------|---------------|
| `QuestionType` | `MULTIPLE_CHOICE`, `FILL_IN_BLANK`, `MATCHING` |
| `QuizLevel` | `EASY`, `MEDIUM`, `HARD` |
| `EventEra` | `ANCIENT`, `MEDIEVAL`, `MODERN`, `CONTEMPORARY` |
| `ContentStatus` | `ACTIVE`, `DRAFT`, `INACTIVE` |
| `ReportStatus` | `OPEN`, `RESOLVED` |

---

## 5. RESPONSE WRAPPER

Tất cả API đều wrap trong `ApiResponse`:
```json
{
  "status": "success",
  "message": "...",
  "data": { ... }
}
```
Lỗi trả về với HTTP status tương ứng:

| HTTP Code | Ý nghĩa |
|-----------|---------|
| 400 | Dữ liệu đầu vào không hợp lệ |
| 401 | Chưa đăng nhập / token hết hạn |
| 403 | Sai role hoặc truy cập ngoài scope |
| 404 | Không tìm thấy tài nguyên |
| 409 | Xung đột dữ liệu (trùng title, đã gắn link,...) |
