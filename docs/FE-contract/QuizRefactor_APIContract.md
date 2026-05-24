## 7. Quiz — Customer

### Object `QuizSet`

```typescript
{
  quizId: string
  title: string
  level: "HARD" | "MEDIUM" | "EASY"
  era: "ALL" | "ANCIENT" | "MEDIEVAL" | "MODERN" | "CONTEMPORARY"
  playCount: number //playCount ở đây là số lần customer đã làm hoàn thành quiz cho cái này, khi kết thúc 1 session rồi nộp quiz,
                         //thì trong bảng quiz_session sẽ có 1 cột end_time lưu giá trị thời gian kết thúc dựa vào đó để xác định được số lần customer đã hoàn thành bài quiz này
  contextTitle?: string
}
```

### Object `QuizQuestion`

```typescript
{
  questionId: string
  content: string
  options: string[]      // chỉ chứa 4 phần tử
  correctAnswer: number  // index 0-3
  explanation?: string
}
```

---

### `GET /quizzes`

> **Lưu ý:** Backend hiện trả về **array trực tiếp** (không pagination). FE tự wrap.  
> Nếu backend có thể trả pagination thì càng tốt.

**Query params:**

| Param    | Type   | Mô tả          |
| -------- | ------ | -------------- |
| `search` | string | Tìm theo title |

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "quizId": "string",
      "title": "string",
      "era": "CONTEMPORARY",
      "playCount": 3241, //playCount ở đây là số lần customer đã làm hoàn thành quiz cho cái này, khi kết thúc 1 session rồi nộp quiz,
      //thì trong bảng quiz_session sẽ có 1 cột end_time lưu giá trị thời gian kết thúc dựa vào đó để xác định được số lần customer đã hoàn thành bài quiz này
      "contextTitle": "string"
    }
  ]
}
```

---

### `GET /quizzes/:quizId`

**Response `200`:** `{ "success": true, "data": QuizSet }`

---

### `POST /quizzes/:quizId/start`

Bắt đầu phiên làm bài. Không cần body.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "sessionId": "string",
    "quizId": "string",
    "title": "string",
    "questions": [
      {
        "questionId": "string",
        "content": "string",
        "options": ["A", "B", "C", "D"],
        "correctAnswer": 0,
        "explanation": "string"
      }
    ]
  }
}
```

> `correctAnswer` trả về ngay vì đây là app học tập (hiện đúng/sai sau mỗi câu).  
> FE sort questions theo `orderIndex` ASC.

---

### `POST /quizzes/submit`

**Request:**

```json
{
  "sessionId": "string",
  "answers": [{ "questionId": "string", "selectedAnswer": 0 }]
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "resultId": "string",
    "score": 8, //score được tính dựa trên số lượng câu trả lời đúng mỗi câu trả lời đúng là 1 điểm
    "totalQuestions": 10,
    "percentage": 80, // score/total question * 100
    "correctAnswers": [0, 1, 2],
    "wrongAnswers": [3, 4]
  }
}
```

> `correctAnswers` / `wrongAnswers`: mảng **index** (0-based) trong danh sách questions.

---

### `GET /quizzes/results/me`

**Query params:**

| Param  | Type   | Default |
| ------ | ------ | ------- |
| `page` | number | 0       |
| `size` | number | 10      |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "sessionId": "string",
        "quizId": "string",
        "quizTitle": "string",
        "score": 8, //score được tính dựa trên số lượng câu trả lời đúng mỗi câu trả lời đúng là 1 điểm
        "totalQuestions": 10,
        "percentage": 80, // score/total question * 100
        "completedAt": "ISO8601"
      }
    ],
    "totalElements": 20,
    "totalPages": 2,
    "currentPage": 0,
    "pageSize": 10,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

## 8. Quiz — Content Admin/System Admin

> Tất cả endpoint dưới yêu cầu role `CONTENT_ADMIN` hoặc `SYSTEM_ADMIN`.

### Object `ContentAdminQuizSet`

```typescript
{
  quizId: string
  title: string
  era: "ANCIENT" | "MEDIEVAL" | "MODERN" | "CONTEMPORARY"
  level: "HARD" | "MEDIUM" | "EASY"
  playCount: number //playCount ở admin thì là tổng số lần tất cả customer hoàn thành quiz này trong hệ thống
  contextId: string
  contextTitle: string
  createdBy: string
  createdDate: string      // ISO8601
  updatedDate: string      // ISO8601
  isActive: boolean
  deletedAt?: string | null // ISO8601 - thời gian xóa tạm thời (null nếu chưa xóa)
  questions: QuizQuestion[]
}
```

---

### `GET /staff/quizzes`

**Query params:**

| Param    | Type   | Mô tả          |
| -------- | ------ | -------------- |
| `search` | string | Tìm theo title |
| `era`    | string | Era enum       |
| `page`   | number | 0-indexed      |
| `size`   | number | Số item/trang  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": ["ContentAdminQuizSet..."],
    "totalElements": 0,
    "totalPages": 0,
    "currentPage": 0,
    "pageSize": 0,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

---

### `GET /staff/quizzes/:quizId`

Chi tiết quiz kèm toàn bộ câu hỏi.

**Response `200`:** `{ "success": true, "data": ContentAdminQuizSet }`

---

### `POST /staff/quizzes`

**Request:**

```json
{
  "title": "string",
  "contextId": "string",
  "questions": [
    {
      "content": "string",
      "options": ["A", "B", "C", "D"],
      "correctAnswer": 0,
      "explanation": "string"
    }
  ]
}
```

**Response `200`:** `{ "success": true, "data": ContentAdminQuizSet }`

---

### `PUT /staff/quizzes/:quizId`

Cập nhật metadata (không bao gồm questions).

**Request:** Partial — tất cả field đều optional:

```json
{
  "title": "string",
  "contextId": "string"
}
```

**Response `200`:** `{ "success": true, "data": ContentAdminQuizSet }`

---

### `DELETE /staff/quizzes/:quizId`

Permanent delete. Response `200`.

---

### `PATCH /staff/quizzes/:quizId/soft-delete`

Chuyển bộ Quiz vào thùng rác (set `deletedAt` thành thời gian hiện tại). Response `200`.

---

### `PATCH /staff/quizzes/:quizId/toggle-active`

Bật/Tắt hoạt động (đảo `isActive`). Response `200`.

---

### `POST /staff/quizzes/:quizId/questions`

Thêm câu hỏi vào quiz.

**Request:**

```json
{
  "content": "string",
  "options": ["A", "B", "C", "D"],
  "correctAnswer": 0,
  "explanation": "string"
}
```

**Response `200`:** `{ "success": true, "data": QuizQuestion }`

---

### `PUT /staff/quizzes/:quizId/questions/:questionId`

Sửa câu hỏi. **Request:** Partial của POST body — tất cả optional.

**Response `200`:** `{ "success": true }`

---

### `DELETE /staff/quizzes/:quizId/questions/:questionId`

Xóa câu hỏi. Response `200`.

---

## 9. Error Format

```json
{
  "success": false,
  "message": "Character not found",
  "data": null,
  "timestamp": "ISO8601"
}
```

| HTTP Status | Khi nào                          |
| ----------- | -------------------------------- |
| `400`       | Request params/body không hợp lệ |
| `401`       | Chưa auth hoặc token hết hạn     |
| `403`       | Không đủ role/quyền              |
| `404`       | Không tìm thấy resource          |
| `500`       | Lỗi server                       |

---

## 10. Notes cho Backend

### ⚠️ Critical — phải đúng để FE không lỗi

1. **Response wrapper bắt buộc:** Mọi response phải có shape `{ success, message, data, timestamp }`. FE đọc `res.data.success` và `res.data.data`.

2. **Character ID field:** Phải trả `characterId` (không phải `id`). FE dùng `raw.characterId ?? raw.id`.

3. **Character image field:** Phải trả `image` (không phải `imageUrl`). FE dùng `raw.image ?? raw.imageUrl`.

4. **Character contextId:** Phải nằm trong `context.contextId` (nested object), không phải flat `contextId`.

5. **Chat history fields:** Group dùng `contextId` + `contextName`. Session dùng `characterImage` (không phải `characterImageUrl`).

6. **Era enum:** Backend nhận và trả **UPPERCASE** (`ANCIENT`, `MEDIEVAL`, `MODERN`, `CONTEMPORARY`). FE tự convert lowercase cho UI.

7. **MessageRole:** Phải là `USER` và `ASSISTANT` (UPPERCASE) — không phải `user`/`assistant`.

8. **Pagination:** `currentPage` là **0-indexed**. FE gửi `page=0` cho trang đầu.

9. **Quiz /quizzes (GET):** Backend trả `data` là **array trực tiếp** (không pagination object). FE tự wrap.

### Datetime

- Tất cả datetime dùng **ISO 8601 UTC**: `"2026-05-22T14:30:00Z"`
- `createdDate` / `updatedDate` (Staff Quiz) — cùng format ISO 8601.

### Active State & Soft Delete pattern

- Thêm `isActive: boolean (default true)` vào `Character`, `HistoricalContext`, `Quiz`.
- Mọi GET query filter `WHERE isActive = true AND deletedAt IS NULL` mặc định cho phía Customer.
- PATCH `/:id/toggle-active` thực hiện bật/tắt (đảo trạng thái `isActive`).
- Thêm `deletedAt: string | null (default null)` vào `Character`, `HistoricalContext`, `Quiz` (ISO8601 UTC).
- PATCH `/:id/soft-delete` thực hiện xóa tạm thời bằng cách cập nhật `deletedAt` thành thời gian hiện tại.
- Mọi GET query filter `WHERE deletedAt IS NULL` cho phía Admin/Staff theo mặc định (trừ khi có param lọc thùng rác hoặc yêu cầu cụ thể).

### Role check

- `/staff/*` endpoints: check `role IN ('CONTENT_ADMIN', 'SYSTEM_ADMIN')` từ JWT → `403` nếu không đủ quyền.
- `POST /auth/register-content-admin`: chỉ `SYSTEM_ADMIN` được gọi.
