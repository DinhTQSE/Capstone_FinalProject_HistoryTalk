# School Admin Dashboard — FE API Contract (Sprint 10)

Base path: `/api/v1/school-admin/dashboard`  
Auth: `Bearer <JWT>` với role `SCHOOL_ADMIN` (`@PreAuthorize("hasRole('SCHOOL_ADMIN')")`)  
Phạm vi dữ liệu: Tự động lọc theo **`school_id` của School Admin đang đăng nhập** (tách biệt hoàn toàn theo từng trường B2B).

---

## 1. So sánh trực tiếp System Admin Dashboard vs School Admin Dashboard

School Admin Dashboard sử dụng **cùng bộ cấu trúc DTO và bộ sub-endpoints** như System Admin Dashboard (`/overview`, `/users`, `/content`, `/chat-activity`, `/quiz`, `/tokens`) để FE có thể tái sử dụng 100% layout và components, với 3 điểm khác biệt nghiệp vụ cốt lõi:

| Hạng mục | System Admin (`/api/v1/system-admin/dashboard/*`) | School Admin (`/api/v1/school-admin/dashboard/*`) |
|---|---|---|
| **Phạm vi dữ liệu** | Toàn hệ thống (lọc theo `?scope=ALL \| B2C \| B2B`) | Chỉ trong phạm vi **trường của School Admin (`school_id`)** |
| **Nhân vật & Kho nội dung** | Thống kê **cả Global và Local** (`topGlobalCharacters` + `topLocalCharacters`, nội dung Global + Local) | **Chỉ thống kê Local của trường** (`c.school_id = :schoolId`, `hc.school_id = :schoolId`; `topGlobalCharacters = []`) |
| **Tài chính vs Quản lý Token** | Có thống kê **Doanh thu (`/revenue`, `/payments`, `/tiers`)** | Không có doanh thu B2C; thay bằng **Thống kê Quản lý Quỹ Token của Trường (`/tokens` & `schoolTokenOverview`)** |

---

## 2. Quy ước Query Parameters chung

| Tham số | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `from` | `YYYY-MM-DD` (ISO Date) | `to - 29 ngày` | Ngày bắt đầu khoảng thống kê |
| `to` | `YYYY-MM-DD` (ISO Date) | Hôm nay | Ngày kết thúc khoảng thống kê (tối đa 180 ngày) |
| `granularity` | `day` \| `week` \| `month` | `day` | Độ phân giải chuỗi thời gian |

> **Lưu ý**: Mọi số liệu thống kê hiện hành đều loại trừ bản ghi đã xóa mềm (`deleted_at IS NULL`), trừ trường `deleted` / `deletedQuizzes`.

---

## 3. Danh sách Endpoints

### 3.1. `GET /api/v1/school-admin/dashboard/overview`
Trả về `DashboardOverviewResponse` (cùng DTO với `/api/v1/system-admin/dashboard/overview`), bổ sung thêm `schoolTokenOverview`:

```json
{
  "users": {
    "total": 476,
    "active": 475,
    "inactive": 1,
    "deleted": 2,
    "newToday": 5,
    "newThisMonth": 30
  },
  "roles": {
    "customers": 0,
    "contentAdmins": 0,
    "systemAdmins": 0,
    "schoolAdmins": 1,
    "teachers": 25,
    "schoolStudents": 450
  },
  "content": {
    "historicalContexts": 3,
    "publishedHistoricalContexts": 2,
    "characters": 4,
    "publishedCharacters": 3,
    "documents": 6
  },
  "chat": {
    "sessions": 40,
    "messages": 120,
    "messagesToday": 15
  },
  "topCharacters": [
    {
      "characterId": "uuid",
      "name": "Chu Văn An (Địa phương)",
      "title": "Vạn thế sư biểu",
      "imageUrl": "https://...",
      "totalMessages": 32,
      "userMessages": 16,
      "aiMessages": 16
    }
  ],
  "topGlobalCharacters": [],
  "topLocalCharacters": [
    {
      "characterId": "uuid",
      "name": "Chu Văn An (Địa phương)",
      "title": "Vạn thế sư biểu",
      "imageUrl": "https://...",
      "totalMessages": 32,
      "userMessages": 16,
      "aiMessages": 16
    }
  ],
  "systemHealth": {
    "status": "UP",
    "lastCheckedAt": "2026-11-19T14:30:00"
  },
  "schoolTokenOverview": {
    "schoolId": "uuid",
    "schoolName": "THPT Chu Văn An",
    "schoolCode": "CVA",
    "packageType": "ENTERPRISE_SMALL",
    "status": "ACTIVE",
    "subscriptionStartDate": "2025-09-01",
    "subscriptionEndDate": "2026-05-31",
    "renewedForNextYear": true,
    "nextPackageType": "ENTERPRISE_MEDIUM",
    "totalClassrooms": 12,
    "totalTeachers": 25,
    "totalStudents": 450,
    "totalSchoolTokenQuota": 435750000,
    "unallocatedTokenQuota": 375750000,
    "allocatedTokens": 60000000,
    "quotaUsagePercentage": 13.8,
    "tokensUsedByStudents": 45000000,
    "tokensUsedByTeachers": 15000000,
    "tokensUsedTotal": 60000000,
    "studentTokenUsagePercentage": 75.0,
    "teacherTokenUsagePercentage": 25.0,
    "defaultDailyStudentQuota": 5000,
    "defaultDailyTeacherQuota": 5000,
    "pendingTokenBoostRequests": 3,
    "topupPaidOrders": 2,
    "topupTokensPurchased": 500000,
    "topupAmountPaid": 75000,
    "assignmentStats": {
      "totalSubmissions": 120,
      "completedRate": 75.0,
      "lateSubmittedRate": 15.0,
      "notStartedRate": 10.0
    }
  }
}
```

---

### 3.2. `GET /api/v1/school-admin/dashboard/users`
- **Query**: `from`, `to`, `granularity`
- **Response (`data` — `DashboardUserAnalyticsResponse`)**:
  - `summary`: `{ total, active, inactive, deleted, recentlyActive }` (chỉ tính user thuộc trường `school_id`).
  - `byRole`:
    ```json
    [
      { "role": "SCHOOL_ADMIN", "count": 1 },
      { "role": "TEACHER", "count": 25 },
      { "role": "SCHOOL_STUDENT", "count": 450 }
    ]
    ```
  - `trend`: `[{ date, newUsers, activeUsers }]` của trường.

---

### 3.3. `GET /api/v1/school-admin/dashboard/content`
- **Mô tả**: Chỉ thống kê **Kho nội dung Local của trường (`school_id`)**.
- **Response (`data` — `DashboardContentSummaryResponse`)**:
```json
{
  "historicalContexts": { "total": 3, "published": 2, "active": 2 },
  "characters": { "total": 4, "published": 3, "active": 3 },
  "documents": { "total": 6, "active": 6 },
  "localHistoricalContexts": { "total": 3, "published": 2, "active": 2 },
  "localCharacters": { "total": 4, "published": 3, "active": 3 }
}
```

---

### 3.4. `GET /api/v1/school-admin/dashboard/chat-activity`
- **Query**: `from`, `to`, `granularity`
- **Response (`data` — `DashboardChatActivityResponse`)**:
  - `summary`: `{ sessions, activeSessions, messages, userMessages, aiMessages, sessionsToday, messagesToday }` (chỉ tính hội thoại của giáo viên/học sinh trong trường; `activeSessions` tính trong cửa sổ 2 phút gần nhất).
  - `trend`: `[{ date, sessions, messages }]`.

---

### 3.5. `GET /api/v1/school-admin/dashboard/quiz`
- **Query**: `from`, `to`, `granularity`
- **Response (`data` — `DashboardQuizAnalyticsResponse`)**:
  - `summary`: `{ totalQuizzes, publishedQuizzes, draftQuizzes, deletedQuizzes, startedSessions, completedSessions, completionRate, averageScorePercentage }` (chỉ tính Quiz Local của trường `hc.school_id = :schoolId`).
  - `sessionsTrend`: `[{ date, started, completed }]`
  - `topQuizzes`: `[{ quizId, title, level, startedSessions, completedSessions, averageScorePercentage }]`
  - `topWrongQuestions`: `[{ questionId, questionContent, quizId, quizTitle, wrongAnswers, totalAnswers, wrongRate }]` (có đầy đủ `questionContent`).

---

### 3.6. `GET /api/v1/school-admin/dashboard/tokens` (Thay thế phần Doanh thu của System Admin)
- **Query**: `from`, `to`, `granularity`
- **Response (`data` — `DashboardTokenUsageResponse`)**:
```json
{
  "summary": {
    "promptTokens": 20000,
    "completionTokens": 40000,
    "totalTokens": 60000,
    "remainingTokens": 375750000,
    "averageRemainingTokens": 4200.0,
    "usersOutOfTokens": 2,
    "estimatedCost": 9000
  },
  "trend": [
    {
      "date": "2026-11-19",
      "promptTokens": 2000,
      "completionTokens": 4000,
      "totalTokens": 6000
    }
  ],
  "tokenBalanceByTier": [],
  "topUsersByTokenUsage": [
    {
      "uid": "uuid",
      "userName": "cva_hs_01",
      "email": "hs01@cva.edu.vn",
      "tierId": null,
      "tierTitle": "SCHOOL_STUDENT",
      "promptTokens": 1200,
      "completionTokens": 2400,
      "totalTokens": 3600,
      "remainingTokens": 1400
    }
  ],
  "schoolTokenManagement": {
    "schoolId": "uuid",
    "schoolName": "THPT Chu Văn An",
    "schoolCode": "CVA",
    "packageType": "ENTERPRISE_SMALL",
    "status": "ACTIVE",
    "subscriptionStartDate": "2025-09-01",
    "subscriptionEndDate": "2026-05-31",
    "renewedForNextYear": true,
    "nextPackageType": "ENTERPRISE_MEDIUM",
    "totalSchoolTokenQuota": 435750000,
    "unallocatedTokenQuota": 375750000,
    "allocatedTokens": 60000000,
    "quotaUsagePercentage": 13.8,
    "tokensUsedByStudents": 45000000,
    "tokensUsedByTeachers": 15000000,
    "tokensUsedTotal": 60000000,
    "studentTokenUsagePercentage": 75.0,
    "teacherTokenUsagePercentage": 25.0,
    "defaultDailyStudentQuota": 5000,
    "defaultDailyTeacherQuota": 5000,
    "pendingTokenBoostRequests": 3,
    "topupPaidOrders": 2,
    "topupTokensPurchased": 500000,
    "topupAmountPaid": 75000
  },
  "tokenUsageByRole": [
    {
      "role": "SCHOOL_STUDENT",
      "users": 450,
      "promptTokens": 15000,
      "completionTokens": 30000,
      "totalTokens": 45000,
      "usagePercentage": 75.0,
      "defaultDailyQuota": 5000
    },
    {
      "role": "TEACHER",
      "users": 25,
      "promptTokens": 5000,
      "completionTokens": 10000,
      "totalTokens": 15000,
      "usagePercentage": 25.0,
      "defaultDailyQuota": 5000
    }
  ],
  "topClassroomsByTokenUsage": [
    {
      "classroomId": "uuid",
      "className": "10A1",
      "classCode": "CVA-10A1-2026",
      "gradeLevel": 10,
      "academicYear": 2026,
      "status": "ACTIVE",
      "teacherName": "Nguyễn Thị Mai",
      "teacherUid": "uuid",
      "studentCount": 35,
      "tokensUsedTotal": 12000,
      "tokensUsedToday": 800,
      "percentageOfSchoolUsage": 20.0
    }
  ]
}
```

---

### 3.7. `GET /api/v1/school-admin/dashboard` (Root Endpoint tổng hợp — Giữ tương thích ngược & Mở rộng)
- Trả về `SchoolAdminDashboardResponse` giữ nguyên toàn bộ các trường cũ (`schoolId`, `schoolName`, `totalClassrooms`, `totalTeachers`, `totalStudents`, `totalSchoolTokenQuota`, `unallocatedTokenQuota`, `allocatedTokens`, `topClassroomsByTokenUsage`, `assignmentStats`...) **và** bổ sung sẵn:
  - `defaultDailyTeacherQuota`, `quotaUsagePercentage`, `topupPaidOrders`, `topupTokensPurchased`, `topupAmountPaid`
  - `users` (`UserOverview`)
  - `roles` (`RoleOverview`)
  - `content` (`DashboardContentSummaryResponse`)
  - `chat` (`ChatOverview`)
  - `topLocalCharacters` (`TopCharacterOverview[]`)
  - `quiz` (`QuizSummary`)
