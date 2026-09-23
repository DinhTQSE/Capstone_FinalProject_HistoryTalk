# HistoryTalk â€” API Contract

> **Version:** 2.2 - synced with Java backend source on 2026-05-28
> **Base URL:** `{BACKEND_BASE_URL}/api/v1`  
> **Response Wrapper:** `{ success: boolean, message: string, data: T, timestamp: string }`  
> **Auth:** `Authorization: Bearer <accessToken>`  
> **Roles:** `CUSTOMER` | `CONTENT_ADMIN` | `SYSTEM_ADMIN`

---

## Má»¥c lá»¥c

1. [Enums & Common Types](#1-enums--common-types)
2. [Auth](#2-auth)
3. [Characters](#3-characters)
4. [Historical Contexts (Events)](#4-historical-contexts-events)
5. [Chat Sessions & Messages](#5-chat-sessions--messages)
6. [Chat History](#6-chat-history)
7. [Quiz â€” Customer](#7-quiz--customer)
8. [Quiz â€” Staff/Admin](#8-quiz--staffadmin)
9. [Error Format](#9-error-format)
10. [Notes cho Backend](#10-notes-cho-backend)
11. [System Dashboard - System Admin](#11-system-dashboard---system-admin)

---

## 1. Enums & Common Types

### Era (thá»i Ä‘áº¡i)

| FE Value (lowercase) | Backend Value (uppercase) | Label | Khoáº£ng nÄƒm |
|---|---|---|---|
| `ancient` | `ANCIENT` | Cá»• Ä‘áº¡i | â†’ 937 |
| `medieval` | `MEDIEVAL` | Trung Ä‘áº¡i | 938 â†’ 1857 |
| `modern` | `MODERN` | Cáº­n Ä‘áº¡i | 1858 â†’ 1944 |
| `contemporary` | `CONTEMPORARY` | Hiá»‡n Ä‘áº¡i | 1945 â†’ nay |

> **Backend pháº£i dÃ¹ng UPPERCASE.** FE tá»± convert sang lowercase cho UI.  
> `all` chá»‰ dÃ¹ng ná»™i bá»™ FE cho filter â€” **khÃ´ng gá»­i lÃªn API**.

### MessageRole

| Value | Ã nghÄ©a |
|---|---|
| `USER` | Tin nháº¯n ngÆ°á»i dÃ¹ng |
| `ASSISTANT` | Tin nháº¯n nhÃ¢n váº­t AI |

### Pagination Response (dÃ¹ng chung)

```json
{
  "content": [],
  "totalElements": 0,
  "totalPages": 0,
  "currentPage": 0,
  "pageSize": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

> `currentPage` lÃ  **0-indexed**.

---

## 2. Auth

### `POST /auth/register`

ÄÄƒng kÃ½ tÃ i khoáº£n má»›i (Customer).

**Request:**
```json
{
  "userName": "string",
  "email": "string",
  "password": "string",
  "confirmPassword": "string"
}
```

**Response `200`:**
```json
{
  "success": true,
  "message": "ÄÄƒng kÃ½ thÃ nh cÃ´ng",
  "data": { "message": "string" },
  "timestamp": "ISO8601"
}
```

---

### `POST /auth/login`

**Request:**
```json
{
  "email": "string",
  "password": "string"
}
```

**Response `200`:**
```json
{
  "success": true,
  "message": "string",
  "data": {
    "uid": "string",
    "userName": "string",
    "email": "string",
    "role": "CUSTOMER",
    "accessToken": "string",
    "refreshToken": "string",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "timestamp": "ISO8601"
}
```

> `role`: `CUSTOMER` | `CONTENT_ADMIN` | `SYSTEM_ADMIN`

---

### `POST /auth/logout`

YÃªu cáº§u auth. KhÃ´ng cáº§n body. Response `200`.

---

### `POST /auth/refresh-token`

**Request:**
```json
{ "refreshToken": "string" }
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "string",
    "refreshToken": "string"
  }
}
```

---

### `POST /auth/register-content-admin`

YÃªu cáº§u role `SYSTEM_ADMIN`.

**Request:**
```json
{
  "userName": "string",
  "name": "string",
  "email": "string",
  "password": "string",
  "confirmPassword": "string",
  "roleName": "CONTENT_ADMIN"
}
```

> `roleName`: `CONTENT_ADMIN` | `SYSTEM_ADMIN`

**Response `200`:** `{ "success": true, "message": "string" }`

---

## 3. Characters

### Object `Character`

```typescript
{
  characterId: string      // ID nhÃ¢n váº­t (FE map sang id)
  name: string
  title: string            // Chá»©c danh
  background: string       // Tiá»ƒu sá»­ (FE map sang description)
  image: string | null     // URL áº£nh (FE map sang imageUrl + avatarUrl)
  personality?: string
  lifespan?: string        // VD: "898â€“944"
  era?: string             // ANCIENT | MEDIEVAL | MODERN | CONTEMPORARY
  modelUrl?: string | null  // URL model 3D
  isPublished?: boolean     // true = visible when not deleted
  status?: "ACTIVE" | "DRAFT" | "INACTIVE"
  createdAt?: string        // ISO8601 - thá»i gian táº¡o (admin only)
  updatedAt?: string        // ISO8601 - thá»i gian cáº­p nháº­t (admin only)
  context?: { contextId: string }   // nested object
  events?: { id: string; title: string; year: number }[]
}
```

> **Quan trá»ng:** FE dÃ¹ng `raw.characterId ?? raw.id` lÃ m id. Pháº£i tráº£ `characterId`.  
> **Quan trá»ng:** FE dÃ¹ng `raw.image ?? raw.imageUrl`. Æ¯u tiÃªn tráº£ field `image`.  
> **Quan trá»ng:** `contextId` náº±m trong `context.contextId` (nested).  
> **Quan trá»ng:** CUSTOMER/guest list APIs hide soft-deleted records and only expose visible content according to backend role filtering.
> **Quan trá»ng:** ADMIN/STAFF APIs still exclude soft-deleted records from main lists; use trash APIs for deleted records.
> **Quan trá»ng:** `createdAt` vÃ  `updatedAt` chá»‰ tráº£ vá» cho ADMIN/STAFF, CUSTOMER sáº½ khÃ´ng tháº¥y.

---

### `GET /characters`

**Query params:**

| Param | Type | MÃ´ táº£ |
|---|---|---|
| `search` | string | TÃ¬m theo tÃªn, chá»©c danh |
| `page` | number | 0-indexed |
| `limit` | number | Sá»‘ item/trang |
| `era` | string | `ANCIENT` \| `MEDIEVAL` \| `MODERN` \| `CONTEMPORARY` |

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "content": [ { "characterId": "...", "name": "...", "title": "...", "background": "...", "image": "url", "modelUrl": "url", "era": "MEDIEVAL", "isPublished": true, "status": "ACTIVE" } ],
    "totalElements": 24,
    "totalPages": 3,
    "currentPage": 0,
    "pageSize": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

### `GET /characters/:id`

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "characterId": "string",
    "name": "string",
    "title": "string",
    "background": "string",
    "image": "string | null",
    "modelUrl": "string | null",
    "personality": "string",
    "lifespan": "string",
    "era": "MEDIEVAL",
    "isPublished": true,
    "status": "ACTIVE",
    "context": { "contextId": "string" }
  }
}
```

---

### `GET /characters/context/:contextId`

Láº¥y danh sÃ¡ch nhÃ¢n váº­t thuá»™c 1 bá»‘i cáº£nh lá»‹ch sá»­.

> **Quan trá»ng:** BE auto-filter theo role. Main lists exclude soft-deleted records; use trash APIs for deleted records.

**Response `200`:**
```json
{
  "success": true,
  "data": [
    {
      "characterId": "string",
      "name": "string",
      "title": "string",
      "background": "string",
      "image": "string | null",
      "isPublished": true,
      "context": { "contextId": "string" }
    }
  ]
}
```

---

### `POST /characters`

YÃªu cáº§u role `CONTENT_ADMIN` | `SYSTEM_ADMIN`.

**Request:**
```json
{
  "name": "string",
  "title": "string",
  "background": "string",
  "image": "string | null",
  "personality": "string",
  "lifespan": "string",
  "modelUrl": "string | null",
  "isPublished": false     // default false on create
}
```

**Response `200`:** `{ "success": true, "data": Character }`

---

### `PUT /characters/:id`

**Request:** Partial cá»§a POST body trÃªn.

**Response `200`:** `{ "success": true, "data": Character }`

---

### `DELETE /characters/:id`

Permanent delete. Response `200`.

---

### `PATCH /characters/:id/soft-delete`

Move a character to trash by setting `deletedAt`. Response `200`.

---

### `POST /characters/:characterId/contexts/:contextId`

Gáº¯n nhÃ¢n váº­t vÃ o bá»‘i cáº£nh. Response `200`.

---

## 4. Historical Contexts (Events)

> FE gá»i resource nÃ y lÃ  "Events" nhÆ°ng endpoint lÃ  `/historical-contexts`.  
> `contextId` â†” `id` cá»§a event trong FE.

### Object `HistoricalContext`

```typescript
{
  contextId: string        // ID (FE map sang id)
  name: string             // TÃªn sá»± kiá»‡n (FE map sang title)
  description: string      // MÃ´ táº£ (FE map sang summary, endYear ghi trong description)
  year: number             // NÄƒm báº¯t Ä‘áº§u
  yearLabel?: string       // VD: "938 SCN", "258 TCN" â€” backend tá»± format
  era: string              // ANCIENT | MEDIEVAL | MODERN | CONTEMPORARY
  location?: string
  imageUrl?: string | null
  videoUrl?: string | null
  period?: string
  isPublished?: boolean
  status?: "ACTIVE" | "DRAFT" | "INACTIVE"
}
```

---

### `GET /historical-contexts`

**Query params:**

| Param | Type | MÃ´ táº£ |
|---|---|---|
| `search` | string | TÃ¬m theo tÃªn |
| `page` | number | 0-indexed |
| `limit` | number | Sá»‘ item/trang |
| `era` | string | `ANCIENT` \| `MEDIEVAL` \| `MODERN` \| `CONTEMPORARY` |

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "contextId": "string",
        "name": "string",
        "description": "string",
        "year": 938,
        "yearLabel": "938 SCN",
        "era": "MEDIEVAL",
        "location": "SÃ´ng Báº¡ch Äáº±ng",
        "imageUrl": "string | null",
        "videoUrl": "string | null",
        "isPublished": true,
        "status": "ACTIVE"
      }
    ],
    "totalElements": 48,
    "totalPages": 8,
    "currentPage": 0,
    "pageSize": 6,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

### `GET /historical-contexts/:id`

**Response `200`:** `{ "success": true, "data": HistoricalContext }`

---

### `POST /historical-contexts`

YÃªu cáº§u role `CONTENT_ADMIN` | `SYSTEM_ADMIN`.

**Request:**
```json
{
  "name": "string",
  "description": "string",
  "era": "MEDIEVAL",
  "year": 938,
  "location": "string",
  "imageUrl": "string | null",
  "videoUrl": "string | null",
  "isPublished": false
}
```

**Response `200`:** `{ "success": true, "data": HistoricalContext }`

---

### `PUT /historical-contexts/:id`

**Request:** Partial cá»§a POST body. **Response `200`:** `{ "success": true, "data": HistoricalContext }`

---

### `DELETE /historical-contexts/:id`

Permanent delete. Response `200`.

---

### `PATCH /historical-contexts/:id/soft-delete`

Move a historical context to trash by setting `deletedAt`. Response `200`.

---

## 5. Chat Sessions & Messages

### Object `ChatSession`

```typescript
{
  id: string
  characterId: string
  contextId: string      // ID cá»§a historical-context
  title: string          // AI tá»± táº¡o sau tin Ä‘áº§u, Ä‘á»ƒ "" khi má»›i táº¡o
  lastMessage: string
  lastMessageAt: string  // ISO8601
  messageCount: number
}
```

### Object `ChatMessage`

```typescript
{
  id: string
  sessionId: string
  role: "USER" | "ASSISTANT"
  content: string
  createdAt: string  // ISO8601
}
```

---

### `GET /chat/sessions`

Láº¥y sessions cá»§a user theo context + character.

**Query params:**

| Param | Type | Required | MÃ´ táº£ |
|---|---|---|---|
| `contextId` | string | âœ“ | ID bá»‘i cáº£nh lá»‹ch sá»­ |
| `characterId` | string | âœ“ | ID nhÃ¢n váº­t |

**Response `200`:**
```json
{
  "success": true,
  "data": [
    {
      "id": "string",
      "characterId": "string",
      "contextId": "string",
      "title": "string",
      "lastMessage": "string",
      "lastMessageAt": "2026-05-22T14:30:00Z",
      "messageCount": 14
    }
  ]
}
```

---

### `POST /chat/sessions`

Táº¡o session má»›i. Backend tá»± táº¡o tin nháº¯n chÃ o (`role: ASSISTANT`) Ä‘áº§u tiÃªn.

**Request:**
```json
{
  "contextId": "string",
  "characterId": "string"
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "string",
    "characterId": "string",
    "contextId": "string",
    "title": "",
    "lastMessage": "",
    "lastMessageAt": "ISO8601",
    "messageCount": 0
  }
}
```

---

### `DELETE /chat/sessions/:sessionId`

XÃ³a session vÃ  toÃ n bá»™ messages. Response `200`.

---

### `GET /chat/sessions/:sessionId/messages`

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "messages": [
      {
        "id": "string",
        "sessionId": "string",
        "role": "ASSISTANT",
        "content": "string",
        "createdAt": "ISO8601"
      }
    ],
    "suggestedQuestions": [
      "CÃ¢u há»i gá»£i Ã½ 1",
      "CÃ¢u há»i gá»£i Ã½ 2",
      "CÃ¢u há»i gá»£i Ã½ 3"
    ]
  }
}
```

> - Messages sort theo `createdAt` ASC.  
> - `suggestedQuestions`: tráº£ `[]` náº¿u chÆ°a cÃ³ message.

---

### `POST /chat/messages`

Gá»­i tin nháº¯n â€” backend gá»i AI vÃ  tráº£ reply trong cÃ¹ng response (synchronous).

**Request:**
```json
{
  "sessionId": "string",
  "content": "string"
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "userMessage": {
      "id": "string",
      "sessionId": "string",
      "role": "USER",
      "content": "string",
      "createdAt": "ISO8601"
    },
    "assistantMessage": {
      "id": "string",
      "sessionId": "string",
      "role": "ASSISTANT",
      "content": "string",
      "createdAt": "ISO8601"
    },
    "suggestedQuestions": ["string", "string", "string"]
  }
}
```

---

## 6. Chat History

### `GET /chat/history`

Láº¥y lá»‹ch sá»­ chat cá»§a user Ä‘ang auth, **Ä‘Ã£ group theo context**.

**Response `200`:**
```json
{
  "success": true,
  "data": [
    {
      "contextId": "string",
      "contextName": "string",
      "sessions": [
        {
          "id": "string",
          "characterId": "string",
          "characterName": "string",
          "characterTitle": "string",
          "characterImage": "string",
          "contextId": "string",
          "contextName": "string",
          "sessionTitle": "string",
          "lastMessage": "string",
          "lastMessageAt": "ISO8601",
          "messageCount": 14
        }
      ]
    }
  ]
}
```

> - Sort groups theo `lastMessageAt` cá»§a session má»›i nháº¥t, DESC.  
> - `characterImage`: URL áº£nh nhÃ¢n váº­t (FE field name: `characterImage`, khÃ´ng pháº£i `characterImageUrl`).  
> - Group dÃ¹ng `contextId` + `contextName` (khÃ´ng pháº£i `eventId`/`eventTitle`).

---

## 7. Quiz â€” Customer

### Object `QuizSet`

```typescript
{
  quizId: string
  title: string
  era: "ALL" | "ANCIENT" | "MEDIEVAL" | "MODERN" | "CONTEMPORARY"
  playCount: number
  rating: number
  contextTitle?: string
}
```

### Object `QuizQuestion`

```typescript
{
  questionId: string
  content: string
  options: string[]      // 4 pháº§n tá»­
  correctAnswer: number  // index 0-3
  explanation?: string
}
```

---

### `GET /quizzes`

> **LÆ°u Ã½:** Backend hiá»‡n tráº£ vá» **array trá»±c tiáº¿p** (khÃ´ng pagination). FE tá»± wrap.  
> Náº¿u backend cÃ³ thá»ƒ tráº£ pagination thÃ¬ cÃ ng tá»‘t.

**Query params:**

| Param | Type | MÃ´ táº£ |
|---|---|---|
| `search` | string | TÃ¬m theo title |

**Response `200`:**
```json
{
  "success": true,
  "data": [
    {
      "quizId": "string",
      "title": "string",
      "era": "CONTEMPORARY",
      "playCount": 3241,
      "rating": 4.8,
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

Báº¯t Ä‘áº§u phiÃªn lÃ m bÃ i. KhÃ´ng cáº§n body.

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

> `correctAnswer` tráº£ vá» ngay vÃ¬ Ä‘Ã¢y lÃ  app há»c táº­p (hiá»‡n Ä‘Ãºng/sai sau má»—i cÃ¢u).  
> FE sort questions theo `orderIndex` ASC.

---

### `POST /quizzes/submit`

**Request:**
```json
{
  "sessionId": "string",
  "answers": [
    { "questionId": "string", "selectedAnswer": 0 }
  ]
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "resultId": "string",
    "score": 8,
    "totalQuestions": 10,
    "percentage": 80,
    "correctAnswers": [0, 1, 2],
    "wrongAnswers": [3, 4]
  }
}
```

> `correctAnswers` / `wrongAnswers`: máº£ng **index** (0-based) trong danh sÃ¡ch questions.

---

### `GET /quizzes/results/me`

**Query params:**

| Param | Type | Default |
|---|---|---|
| `page` | number | 0 |
| `size` | number | 10 |

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "resultId": "string",
        "quizId": "string",
        "quizTitle": "string",
        "score": 8,
        "totalQuestions": 10,
        "percentage": 80,
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

## 8. Quiz â€” Content Admin/System Admin

> Táº¥t cáº£ endpoint dÆ°á»›i yÃªu cáº§u role `CONTENT_ADMIN` hoáº·c `SYSTEM_ADMIN`.

### Object `ContentAdminQuizSet`

```typescript
{
  quizId: string
  title: string
  era: "ANCIENT" | "MEDIEVAL" | "MODERN" | "CONTEMPORARY"
  playCount: number
  rating: number
  contextId: string
  contextTitle: string
  createdBy: string
  createdDate: string      // ISO8601
  updatedDate: string      // ISO8601
  isPublished: boolean
  status: "ACTIVE" | "DRAFT" | "INACTIVE"
  questions: QuizQuestion[]
}
```

---

### `GET /staff/quizzes`

**Query params:**

| Param | Type | MÃ´ táº£ |
|---|---|---|
| `search` | string | TÃ¬m theo title |
| `era` | string | Era enum |
| `page` | number | 0-indexed |
| `size` | number | Sá»‘ item/trang |

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "content": [ "ContentAdminQuizSet..." ],
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

Chi tiáº¿t quiz kÃ¨m toÃ n bá»™ cÃ¢u há»i.

**Response `200`:** `{ "success": true, "data": ContentAdminQuizSet }`

---

### `POST /staff/quizzes`

**Request:**
```json
{
  "title": "string",
  "contextId": "string",
  "era": "CONTEMPORARY",
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

Cáº­p nháº­t metadata (khÃ´ng bao gá»“m questions).

**Request:** Partial â€” táº¥t cáº£ field Ä‘á»u optional:
```json
{
  "title": "string",
  "contextId": "string",
  "era": "CONTEMPORARY"
}
```

**Response `200`:** `{ "success": true, "data": ContentAdminQuizSet }`

---

### `DELETE /staff/quizzes/:quizId`

Permanent delete. Response `200`.

---

### `PATCH /staff/quizzes/:quizId/soft-delete`

Move a quiz to trash by setting `deletedAt`. Response `200`.

---

### `POST /staff/quizzes/:quizId/questions`

ThÃªm cÃ¢u há»i vÃ o quiz.

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

Sá»­a cÃ¢u há»i. **Request:** Partial cá»§a POST body â€” táº¥t cáº£ optional.

**Response `200`:** `{ "success": true }`

---

### `DELETE /staff/quizzes/:quizId/questions/:questionId`

XÃ³a cÃ¢u há»i. Response `200`.

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

| HTTP Status | Khi nÃ o |
|---|---|
| `400` | Request params/body khÃ´ng há»£p lá»‡ |
| `401` | ChÆ°a auth hoáº·c token háº¿t háº¡n |
| `403` | KhÃ´ng Ä‘á»§ role/quyá»n |
| `404` | KhÃ´ng tÃ¬m tháº¥y resource |
| `500` | Lá»—i server |

---

## 10. Notes cho Backend

### âš ï¸ Critical â€” pháº£i Ä‘Ãºng Ä‘á»ƒ FE khÃ´ng lá»—i

1. **Response wrapper báº¯t buá»™c:** Má»i response pháº£i cÃ³ shape `{ success, message, data, timestamp }`. FE Ä‘á»c `res.data.success` vÃ  `res.data.data`.

2. **Character ID field:** Pháº£i tráº£ `characterId` (khÃ´ng pháº£i `id`). FE dÃ¹ng `raw.characterId ?? raw.id`.

3. **Character image field:** Pháº£i tráº£ `image` (khÃ´ng pháº£i `imageUrl`). FE dÃ¹ng `raw.image ?? raw.imageUrl`.

4. **Character contextId:** Pháº£i náº±m trong `context.contextId` (nested object), khÃ´ng pháº£i flat `contextId`.

5. **Chat history fields:** Group dÃ¹ng `contextId` + `contextName`. Session dÃ¹ng `characterImage` (khÃ´ng pháº£i `characterImageUrl`).

6. **Era enum:** Backend nháº­n vÃ  tráº£ **UPPERCASE** (`ANCIENT`, `MEDIEVAL`, `MODERN`, `CONTEMPORARY`). FE tá»± convert lowercase cho UI.

7. **MessageRole:** Pháº£i lÃ  `USER` vÃ  `ASSISTANT` (UPPERCASE) â€” khÃ´ng pháº£i `user`/`assistant`.

8. **Pagination:** `currentPage` lÃ  **0-indexed**. FE gá»­i `page=0` cho trang Ä‘áº§u.

9. **Quiz /quizzes (GET):** Backend tráº£ `data` lÃ  **array trá»±c tiáº¿p** (khÃ´ng pagination object). FE tá»± wrap.

### Datetime

- Táº¥t cáº£ datetime dÃ¹ng **ISO 8601 UTC**: `"2026-05-22T14:30:00Z"`
- `createdDate` / `updatedDate` (Staff Quiz) â€” cÃ¹ng format ISO 8601.

### Content lifecycle pattern

- Content lifecycle for `Character`, `HistoricalContext`, and `Quiz` is derived from `deletedAt` and `isPublished`.
- `deletedAt != null` -> `status = INACTIVE`.
- `deletedAt == null && isPublished == false` -> `status = DRAFT`.
- `deletedAt == null && isPublished == true` -> `status = ACTIVE`.
- Main list APIs exclude soft-deleted records. Use `/system/trash` APIs for deleted records.
- Soft-delete endpoints use `PATCH /:id/soft-delete`; toggle-active endpoints are no longer current.

### Role check

- `/staff/*` endpoints: check `role IN ('CONTENT_ADMIN', 'SYSTEM_ADMIN')` tá»« JWT â†’ `403` náº¿u khÃ´ng Ä‘á»§ quyá»n.
- `POST /auth/register-content-admin`: chá»‰ `SYSTEM_ADMIN` Ä‘Æ°á»£c gá»i.

---

## 11. System Dashboard - System Admin

> Includes Plan A and Plan B Phase 2A. Token usage and AI cost are not included yet because the Python AI service does not return `tokenUsage` yet.

All endpoints require:

```http
Authorization: Bearer <accessToken>
```

Required role:

```text
SYSTEM_ADMIN
```

Base prefix:

```text
/api/v1/system-admin/dashboard
```

Response wrapper:

```json
{
  "success": true,
  "message": "string",
  "data": {},
  "timestamp": "ISO8601"
}
```

### `GET /system-admin/dashboard/overview`

First-screen dashboard cards.

**Response `200`:**

```json
{
  "success": true,
  "message": "Dashboard overview retrieved successfully",
  "data": {
    "users": {
      "total": 0,
      "active": 0,
      "inactive": 0,
      "deleted": 0,
      "newToday": 0,
      "newThisMonth": 0
    },
    "roles": {
      "customers": 0,
      "contentAdmins": 0,
      "systemAdmins": 0
    },
    "content": {
      "historicalContexts": 0,
      "publishedHistoricalContexts": 0,
      "characters": 0,
      "publishedCharacters": 0,
      "documents": 0
    },
    "chat": {
      "sessions": 0,
      "messages": 0,
      "messagesToday": 0
    },
    "systemHealth": {
      "status": "UP",
      "lastCheckedAt": "2026-05-23T13:00:00"
    }
  },
  "timestamp": "2026-05-23T13:00:00"
}
```

### `GET /system-admin/dashboard/users`

User analytics summary and trend.

**Query params:**

| Param | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `from` | date `YYYY-MM-DD` | no | `to - 29 days` | Inclusive |
| `to` | date `YYYY-MM-DD` | no | today | Inclusive |
| `granularity` | `day` \| `week` \| `month` | no | `day` | Max range: 180 days |

**Response `200`:**

```json
{
  "success": true,
  "message": "User analytics retrieved successfully",
  "data": {
    "summary": {
      "total": 0,
      "active": 0,
      "inactive": 0,
      "deleted": 0,
      "recentlyActive": 0
    },
    "byRole": [
      { "role": "CUSTOMER", "count": 0 },
      { "role": "CONTENT_ADMIN", "count": 0 },
      { "role": "SYSTEM_ADMIN", "count": 0 }
    ],
    "trend": [
      { "date": "2026-05-23", "newUsers": 0, "activeUsers": 0 }
    ]
  },
  "timestamp": "2026-05-23T13:00:00"
}
```

Notes:

- `summary.total` counts all user rows.
- `summary.deleted` counts users where `deletedAt` is not null.
- `byRole` counts non-deleted users.
- `trend.activeUsers` is based on `lastActiveDate`.

### `GET /system-admin/dashboard/content`

Content inventory summary.

**Response `200`:**

```json
{
  "success": true,
  "message": "Content summary retrieved successfully",
  "data": {
    "historicalContexts": {
      "total": 0,
      "published": 0,
      "active": 0
    },
    "characters": {
      "total": 0,
      "published": 0,
      "active": 0
    },
    "documents": {
      "total": 0,
      "active": 0
    }
  },
  "timestamp": "2026-05-23T13:00:00"
}
```

Notes:

- `total` counts all rows, including inactive or soft-deleted rows.
- `active` counts rows where `deletedAt` is null and `isActive=true`.
- `published` counts all rows where `isPublished=true`, including inactive or soft-deleted rows.

### `GET /system-admin/dashboard/chat-activity`

Chat session and message analytics.

**Query params:**

| Param | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `from` | date `YYYY-MM-DD` | no | `to - 29 days` | Inclusive |
| `to` | date `YYYY-MM-DD` | no | today | Inclusive |
| `granularity` | `day` \| `week` \| `month` | no | `day` | Max range: 180 days |

**Response `200`:**

```json
{
  "success": true,
  "message": "Chat activity retrieved successfully",
  "data": {
    "summary": {
      "sessions": 0,
      "activeSessions": 0,
      "messages": 0,
      "userMessages": 0,
      "aiMessages": 0,
      "sessionsToday": 0,
      "messagesToday": 0
    },
    "trend": [
      { "date": "2026-05-23", "sessions": 0, "messages": 0 }
    ]
  },
  "timestamp": "2026-05-23T13:00:00"
}
```

Notes:

- `summary.sessions` and `summary.messages` count all rows, including soft-deleted rows.
- `summary.activeSessions` counts sessions where `deletedAt` is null and `isActive=true`.

### `GET /system-admin/dashboard/system-health`

Lightweight backend health for the in-app dashboard. Grafana remains the detailed technical monitoring screen.

**Response `200`:**

```json
{
  "success": true,
  "message": "System health retrieved successfully",
  "data": {
    "status": "UP",
    "uptime": "0d 1h 2m 3s",
    "jvmMemoryUsed": 0,
    "jvmMemoryMax": 0,
    "httpRequestCount": 0,
    "httpErrorCount": 0,
    "lastCheckedAt": "2026-05-23T13:00:00"
  },
  "timestamp": "2026-05-23T13:00:00"
}
```

### `GET /system-admin/dashboard/revenue`

Revenue analytics from paid payment orders.

**Query params:**

| Param | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `from` | date `YYYY-MM-DD` | no | `to - 29 days` | Inclusive |
| `to` | date `YYYY-MM-DD` | no | today | Inclusive |
| `granularity` | `day` \| `week` \| `month` | no | `day` | Max range: 180 days |

**Response `200`:**

```json
{
  "success": true,
  "message": "Revenue analytics retrieved successfully",
  "data": {
    "summary": {
      "totalRevenue": 0,
      "revenueToday": 0,
      "revenueThisMonth": 0,
      "revenueThisYear": 0,
      "paidOrders": 0,
      "averageOrderValue": 0
    },
    "ordersByStatus": [
      { "status": "PENDING", "count": 0 },
      { "status": "PAID", "count": 0 },
      { "status": "CANCELLED", "count": 0 },
      { "status": "EXPIRED", "count": 0 },
      { "status": "FAILED", "count": 0 }
    ],
    "revenueByTier": [
      {
        "tierId": "00000000-0000-0000-0000-000000000002",
        "tierTitle": "plus",
        "revenue": 0,
        "paidOrders": 0
      }
    ],
    "trend": [
      { "date": "2026-05-27", "revenue": 0, "paidOrders": 0 }
    ]
  },
  "timestamp": "2026-05-27T13:00:00"
}
```

Notes:

- Revenue only counts `payment_order.status = PAID`.
- Revenue trend uses `payment_order.paid_at`.
- `summary.revenueThisYear` counts paid revenue from January 1 of the current year to January 1 of the next year.
- Amount values are VND integers.

### `GET /system-admin/dashboard/payments`

Payment order and transaction analytics.

**Query params:**

| Param | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `from` | date `YYYY-MM-DD` | no | `to - 29 days` | Inclusive |
| `to` | date `YYYY-MM-DD` | no | today | Inclusive |
| `granularity` | `day` \| `week` \| `month` | no | `day` | Max range: 180 days |

**Response `200`:**

```json
{
  "success": true,
  "message": "Payment analytics retrieved successfully",
  "data": {
    "summary": {
      "totalOrders": 0,
      "pendingOrders": 0,
      "paidOrders": 0,
      "cancelledOrders": 0,
      "expiredOrders": 0,
      "failedOrders": 0,
      "successfulTransactions": 0,
      "failedTransactions": 0
    },
    "transactionTrend": [
      { "date": "2026-05-27", "success": 0, "failed": 0 }
    ]
  },
  "timestamp": "2026-05-27T13:00:00"
}
```

### `GET /system-admin/dashboard/tiers`

Tier/package analytics.

**Query params:**

| Param | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `from` | date `YYYY-MM-DD` | no | `to - 29 days` | Inclusive, used for purchase stats |
| `to` | date `YYYY-MM-DD` | no | today | Inclusive |
| `granularity` | `day` \| `week` \| `month` | no | `day` | Accepted for consistency |

**Response `200`:**

```json
{
  "success": true,
  "message": "Tier analytics retrieved successfully",
  "data": {
    "summary": {
      "activeTiers": 0,
      "currentPaidUsers": 0,
      "currentFreeUsers": 0,
      "activeSubscriptions": 0,
      "expiringSoonSubscriptions": 0,
      "freeToPaidConversionRate": 0.0
    },
    "usersByTier": [
      { "tierId": "00000000-0000-0000-0000-000000000001", "tierTitle": "free", "users": 0 }
    ],
    "purchasesByTier": [
      { "tierId": "00000000-0000-0000-0000-000000000002", "tierTitle": "plus", "paidOrders": 0, "revenue": 0 }
    ]
  },
  "timestamp": "2026-05-27T13:00:00"
}
```

Notes:

- `freeToPaidConversionRate` is a percentage from `0` to `100`.
- Current tier uses `user.tier_id`.
- Active subscriptions use `user_tier`.

### `GET /system-admin/dashboard/quiz`

Quiz analytics.

**Query params:**

| Param | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `from` | date `YYYY-MM-DD` | no | `to - 29 days` | Inclusive |
| `to` | date `YYYY-MM-DD` | no | today | Inclusive |
| `granularity` | `day` \| `week` \| `month` | no | `day` | Max range: 180 days |

**Response `200`:**

```json
{
  "success": true,
  "message": "Quiz analytics retrieved successfully",
  "data": {
    "summary": {
      "totalQuizzes": 0,
      "publishedQuizzes": 0,
      "draftQuizzes": 0,
      "deletedQuizzes": 0,
      "startedSessions": 0,
      "completedSessions": 0,
      "completionRate": 0.0,
      "averageScorePercentage": 0.0
    },
    "sessionsTrend": [
      { "date": "2026-05-27", "started": 0, "completed": 0 }
    ],
    "topQuizzes": [
      {
        "quizId": "uuid",
        "title": "string",
        "level": "EASY",
        "startedSessions": 0,
        "completedSessions": 0,
        "averageScorePercentage": 0.0
      }
    ],
    "topWrongQuestions": [
      {
        "questionId": "uuid",
        "quizId": "uuid",
        "quizTitle": "string",
        "wrongAnswers": 0,
        "totalAnswers": 0,
        "wrongRate": 0.0
      }
    ]
  },
  "timestamp": "2026-05-27T13:00:00"
}
```

Notes:

- `completionRate`, `averageScorePercentage`, and `wrongRate` are percentages from `0` to `100`.
- Completed sessions are sessions where `end_time` is not null.

### `GET /system-admin/dashboard/tokens`

Token usage and token balance dashboard.

**Query params:**

| Param | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `from` | date `YYYY-MM-DD` | no | `to - 29 days` | Inclusive, used for message token usage |
| `to` | date `YYYY-MM-DD` | no | today | Inclusive |
| `granularity` | `day` \| `week` \| `month` | no | `day` | Max range: 180 days |

**Response `200`:**

```json
{
  "success": true,
  "message": "Token usage analytics retrieved successfully",
  "data": {
    "summary": {
      "promptTokens": 0,
      "completionTokens": 0,
      "totalTokens": 0,
      "remainingTokens": 0,
      "averageRemainingTokens": 0.0,
      "usersOutOfTokens": 0,
      "estimatedCost": 0
    },
    "trend": [
      {
        "date": "2026-05-27",
        "promptTokens": 0,
        "completionTokens": 0,
        "totalTokens": 0
      }
    ],
    "tokenBalanceByTier": [
      {
        "tierId": "00000000-0000-0000-0000-000000000001",
        "tierTitle": "free",
        "users": 0,
        "remainingTokens": 0,
        "averageRemainingTokens": 0.0,
        "usersOutOfTokens": 0
      }
    ],
    "topUsersByTokenUsage": [
      {
        "uid": "00000000-0000-0000-0000-000000000001",
        "userName": "customer1",
        "email": "customer1@example.com",
        "tierId": "00000000-0000-0000-0000-000000000001",
        "tierTitle": "free",
        "promptTokens": 0,
        "completionTokens": 0,
        "totalTokens": 0,
        "remainingTokens": 0
      }
    ]
  },
  "timestamp": "2026-05-27T13:00:00"
}
```

Notes:

- Usage fields read from `message.token`.
- `message.is_from_ai=false` means prompt token usage.
- `message.is_from_ai=true` means completion token usage.
- Balance fields read from `user.token`.
- `estimatedCost` is `0` for the current Ollama/self-hosted AI setup.

### Errors

`401`: missing/invalid token.

`403`: authenticated user is not `SYSTEM_ADMIN`.

`400`: invalid date range or invalid `granularity`.
