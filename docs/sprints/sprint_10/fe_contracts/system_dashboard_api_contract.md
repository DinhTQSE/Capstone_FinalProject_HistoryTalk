# System Admin Dashboard — FE API Contract (Sprint 10)

Base path: `/api/v1/system-admin/dashboard`  
Auth: `Bearer <JWT>` với role `SYSTEM_ADMIN` (`@PreAuthorize("hasRole('SYSTEM_ADMIN')")`)

---

## 1. Quy ước Query Parameters chung

| Tham số | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `from` | `YYYY-MM-DD` (ISO Date) | `to - 29 ngày` | Ngày bắt đầu khoảng thống kê |
| `to` | `YYYY-MM-DD` (ISO Date) | Hôm nay | Ngày kết thúc khoảng thống kê (tối đa 180 ngày) |
| `granularity` | `day` \| `week` \| `month` | `day` | Độ phân giải chuỗi thời gian |
| `scope` | `ALL` \| `B2C` \| `B2B` \| `GLOBAL` \| `LOCAL` | `ALL` | Lọc dữ liệu theo phân khúc (`GLOBAL` map về `B2C`, `LOCAL` map về `B2B`). Giá trị không hợp lệ trả `400 Bad Request`. |

> **Lưu ý chung**: Tất cả số liệu thống kê hiện hành đều loại trừ các bản ghi đã xóa mềm (`deleted_at IS NOT NULL`), trừ trường `deleted` / `deletedQuizzes` chuyên biệt.

---

## 2. Endpoints

### 2.1. `GET /api/v1/system-admin/dashboard/overview`
- **Query**: `scope` (optional, default `ALL`)
- **Response (`data`)**:
```json
{
  "users": {
    "total": 163,
    "active": 163,
    "inactive": 0,
    "deleted": 2,
    "newToday": 3,
    "newThisMonth": 25
  },
  "roles": {
    "customers": 50,
    "contentAdmins": 2,
    "systemAdmins": 1,
    "schoolAdmins": 3,
    "teachers": 10,
    "schoolStudents": 100
  },
  "content": {
    "historicalContexts": 4,
    "publishedHistoricalContexts": 4,
    "characters": 4,
    "publishedCharacters": 4,
    "documents": 8
  },
  "chat": {
    "sessions": 40,
    "messages": 98,
    "messagesToday": 12
  },
  "topCharacters": [
    {
      "characterId": "uuid",
      "name": "Lê Lợi",
      "title": "Lê Thái Tổ",
      "imageUrl": "https://...",
      "totalMessages": 28,
      "userMessages": 14,
      "aiMessages": 14
    }
  ],
  "topGlobalCharacters": [ "..." ],
  "topLocalCharacters": [ "..." ],
  "systemHealth": {
    "status": "UP",
    "lastCheckedAt": "2026-11-17T18:30:00"
  }
}
```
- **Thay đổi quan trọng**:
  - `roles` bổ sung `schoolAdmins`, `teachers`, `schoolStudents`.
  - Tách riêng `topGlobalCharacters` (`school_id IS NULL`) và `topLocalCharacters` (`school_id IS NOT NULL`).
  - `topCharacters` trả `topLocalCharacters` khi `scope=B2B`/`LOCAL`, còn lại trả `topGlobalCharacters` (không trộn nhân vật trùng tên giữa Global và Local).

---

### 2.2. `GET /api/v1/system-admin/dashboard/users`
- **Query**: `from`, `to`, `granularity`, `scope`
- **Response (`data`)**:
  - `summary`: `{ total, active, inactive, deleted, recentlyActive }` (lọc theo role B2C/B2B khi có `scope`).
  - `byRole`: danh sách `{ role, count }`:
    - `B2C`: `[CUSTOMER]`
    - `B2B`: `[SCHOOL_ADMIN, TEACHER, SCHOOL_STUDENT]`
    - `ALL`: `[CUSTOMER, SCHOOL_ADMIN, TEACHER, SCHOOL_STUDENT, CONTENT_ADMIN, SYSTEM_ADMIN]`
  - `trend`: `[{ date, newUsers, activeUsers }]` (loại trừ user đã xóa mềm).

---

### 2.3. `GET /api/v1/system-admin/dashboard/content`
- **Query**: `scope`
- **Response (`data`)**:
```json
{
  "historicalContexts": { "total": 4, "published": 4, "active": 4 },
  "characters": { "total": 4, "published": 4, "active": 4 },
  "documents": { "total": 8, "active": 8 },
  "localHistoricalContexts": { "total": 2, "published": 1, "active": 1 },
  "localCharacters": { "total": 3, "published": 2, "active": 2 }
}
```
- **Thay đổi quan trọng**:
  - `total` và `published` đã loại bỏ bản ghi xóa mềm (`deleted_at IS NULL`).
  - Bổ sung `localHistoricalContexts` và `localCharacters`. Khi `scope=B2B`/`LOCAL`, `historicalContexts` và `characters` trả số liệu Local.

---

### 2.4. `GET /api/v1/system-admin/dashboard/chat-activity`
- **Query**: `from`, `to`, `granularity`, `scope`
- **Response (`data`)**:
  - `summary`: `{ sessions, activeSessions, messages, userMessages, aiMessages, sessionsToday, messagesToday }`
  - `trend`: `[{ date, sessions, messages }]`
- **Thay đổi quan trọng**:
  - `activeSessions` chỉ đếm các phiên chưa xóa có `last_message_at >= NOW() - 2 minutes`.
  - Hỗ trợ `scope=B2C` (`CUSTOMER`) và `scope=B2B` (`SCHOOL_ADMIN`, `TEACHER`, `SCHOOL_STUDENT`).
  - Loại trừ `chat_session` và `message` đã xóa mềm.

---

### 2.5. `GET /api/v1/system-admin/dashboard/revenue`
- **Query**: `from`, `to`, `granularity`, `scope`
- **Response (`data`)**:
  - `summary`: `{ totalRevenue, revenueToday, revenueThisMonth, revenueThisYear, paidOrders, averageOrderValue }`
  - `ordersByStatus`: `[{ status, count }]`
  - `revenueByTier`: `[{ tierId, tierTitle, revenue, paidOrders }]`
  - `trend`: `[{ date, revenue, paidOrders }]`
- **Thay đổi quan trọng**:
  - Khi `scope=B2C`: chỉ tính đơn `TIER_SUBSCRIPTION` (doanh thu gói dịch vụ B2C không còn bị cộng lẫn đơn nạp token B2B).
  - Khi `scope=B2B`: chỉ tính đơn `SCHOOL_TOKEN_TOPUP`; `revenueByTier` trả `[]`.
  - `revenueByTier` gộp theo `LOWER(t.title)` (`free`, `plus`, `pro`), không còn lặp dòng giữa gói tháng và gói năm.

---

### 2.6. `GET /api/v1/system-admin/dashboard/payments`
- **Query**: `from`, `to`, `granularity`, `scope`
- **Response (`data`)**:
  - `summary`: `{ totalOrders, pendingOrders, paidOrders, cancelledOrders, expiredOrders, failedOrders, successfulTransactions, failedTransactions }`
  - `transactionTrend`: `[{ date, success, failed }]`
- **Thay đổi quan trọng**:
  - Hỗ trợ `scope=B2C` (`TIER_SUBSCRIPTION`) và `scope=B2B` (`SCHOOL_TOKEN_TOPUP`).

---

### 2.7. `GET /api/v1/system-admin/dashboard/tiers`
- **Query**: `from`, `to`, `granularity` (chỉ áp dụng cho B2C `CUSTOMER`)
- **Response (`data`)**:
  - `summary`: `{ activeTiers, currentPaidUsers, currentFreeUsers, activeSubscriptions, expiringSoonSubscriptions, freeToPaidConversionRate }`
  - `usersByTier`: `[{ tierId, tierTitle, users }]`
  - `purchasesByTier`: `[{ tierId, tierTitle, paidOrders, revenue }]`
- **Thay đổi quan trọng**:
  - `usersByTier` và `purchasesByTier` gộp theo `LOWER(t.title)` để không bị đúp `plus`, `pro`.

---

### 2.8. `GET /api/v1/system-admin/dashboard/quiz`
- **Query**: `from`, `to`, `granularity`, `scope`
- **Response (`data`)**:
  - `summary`: `{ totalQuizzes, publishedQuizzes, draftQuizzes, deletedQuizzes, startedSessions, completedSessions, completionRate, averageScorePercentage }`
  - `sessionsTrend`: `[{ date, started, completed }]`
  - `topQuizzes`: `[{ quizId, title, level, startedSessions, completedSessions, averageScorePercentage }]`
  - `topWrongQuestions`: `[{ questionId, questionContent, quizId, quizTitle, wrongAnswers, totalAnswers, wrongRate }]`
- **Thay đổi quan trọng**:
  - `totalQuizzes` và các thống kê session chỉ tính quiz chưa xóa mềm (`quiz.deleted_at IS NULL`).
  - Hỗ trợ `scope=B2C`/`GLOBAL` (`hc.school_id IS NULL`) và `scope=B2B`/`LOCAL` (`hc.school_id IS NOT NULL`).
  - `topWrongQuestions` bổ sung `questionContent`.

---

### 2.9. `GET /api/v1/system-admin/dashboard/tokens`
- **Query**: `from`, `to`, `granularity`, `scope`
- **Response (`data`)**:
  - `summary`: `{ promptTokens, completionTokens, totalTokens, remainingTokens, averageRemainingTokens, usersOutOfTokens, estimatedCost }`
  - `trend`: `[{ date, promptTokens, completionTokens, totalTokens }]`
  - `tokenBalanceByTier`: `[{ tierId, tierTitle, users, remainingTokens, averageRemainingTokens, usersOutOfTokens }]`
  - `topUsersByTokenUsage`: `[{ uid, userName, email, tierId, tierTitle, promptTokens, completionTokens, totalTokens, remainingTokens }]`
- **Thay đổi quan trọng**:
  - Đồng bộ phạm vi `scope=B2C` (`CUSTOMER`) và `scope=B2B` (`SCHOOL_ADMIN`, `TEACHER`, `SCHOOL_STUDENT`) giữa tổng token tiêu thụ, token còn lại và top user.
  - `tokenBalanceByTier` gộp theo `LOWER(t.title)` (trả `[]` khi `scope=B2B`).
