# FRONTEND API CONTRACT: DASHBOARDS & ANALYTICS

**Document Version:** 1.0.0  
**Sprint:** Sprint 6  
**Module:** Analytics & Reporting (US-SP6-06 to US-SP6-10)  
**Author:** Trần Quốc Dinh (DinhTQ) - Backend Developer & DevOps  
**Base URL:** `/Historical-tell/api/v1`  
**Authentication:** Bearer Token (JWT Authorization header: `Bearer <token>`)

---

## 1. Overview & General Specifications

This document specifies the REST API contract for the **5 Role-based Dashboards and Analytics** endpoints implemented in Sprint 6. All endpoints return data wrapped in the standard API envelope:

```json
{
  "code": 200,
  "message": "...",
  "data": { ... }
}
```

### Authentication & Authorization
- **Header:** `Authorization: Bearer <jwt_access_token>`
- If token is missing, expired, or invalid: `401 Unauthorized`.
- If user does not have the required role or lacks ownership/permission: `403 Forbidden`.
- If requested resource is not found or belongs to another tenant: `404 Not Found`.

---

## 2. API Endpoints

### 2.1. School Admin Dashboard (US-SP6-06)
- **Endpoint:** `GET /api/v1/school-admin/dashboard`
- **Authorized Role:** `ROLE_SCHOOL_ADMIN`
- **Description:** Returns aggregate KPI overview for the administrator's school, including teacher/student/class counts, token quota consumption, and classroom summaries.

#### TypeScript Interface
```typescript
export interface ClassroomSummary {
  classId: string;
  className: string;
  classCode: string;
  teacherName: string | null;
  studentCount: number;
}

export interface SchoolAdminDashboardResponse {
  schoolId: string;
  schoolName: string;
  totalTeachers: number;
  totalActiveStudents: number;
  totalClassrooms: number;
  allocatedTokenQuota: number;
  consumedTokens: number;
  remainingTokens: number;
  quotaUsagePercentage: number;
  classrooms: ClassroomSummary[];
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "School admin dashboard fetched successfully",
  "data": {
    "schoolId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "schoolName": "THPT Chuyên Lê Hồng Phong",
    "totalTeachers": 24,
    "totalActiveStudents": 850,
    "totalClassrooms": 20,
    "allocatedTokenQuota": 5000000,
    "consumedTokens": 1824500,
    "remainingTokens": 3175500,
    "quotaUsagePercentage": 36.49,
    "classrooms": [
      {
        "classId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        "className": "Lớp 10A1",
        "classCode": "LHP-10A1-2026",
        "teacherName": "Nguyễn Văn A",
        "studentCount": 42
      },
      {
        "classId": "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
        "className": "Lớp 11B2",
        "classCode": "LHP-11B2-2026",
        "teacherName": "Trần Thị B",
        "studentCount": 40
      }
    ]
  }
}
```

---

### 2.2. Teacher Classroom Analytics Dashboard (US-SP6-07)
- **Endpoint:** `GET /api/v1/teacher/dashboard/classrooms/{classId}`
- **Authorized Role:** `ROLE_TEACHER`
- **Description:** Returns pedagogical analytics for a specific classroom managed by the authenticated teacher. Multi-tenancy check ensures teacher cannot view classrooms outside their assignment.

#### TypeScript Interface
```typescript
export interface StudentPerformanceSummary {
  studentUid: string;
  studentName: string;
  studentCode: string;
  averageScorePercentage: number;
  completedQuizzesCount: number;
  totalMessagesSent: number;
}

export interface WrongQuestionSummary {
  questionId: string;
  questionContent: string;
  wrongCount: number;
}

export interface TeacherClassroomDashboardResponse {
  classroomId: string;
  className: string;
  classCode: string;
  totalStudents: number;
  totalChatMessagesSent: number;
  averageClassQuizScore: number;
  studentRankings: StudentPerformanceSummary[];
  topWrongQuestions: WrongQuestionSummary[];
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "Teacher classroom dashboard fetched successfully",
  "data": {
    "classroomId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "className": "Lớp 10A1",
    "classCode": "LHP-10A1-2026",
    "totalStudents": 42,
    "totalChatMessagesSent": 1340,
    "averageClassQuizScore": 81.25,
    "studentRankings": [
      {
        "studentUid": "u1111111-2222-3333-4444-555555555555",
        "studentName": "Phạm Quốc Huy",
        "studentCode": "HS00123",
        "averageScorePercentage": 96.5,
        "completedQuizzesCount": 8,
        "totalMessagesSent": 65
      },
      {
        "studentUid": "u2222222-3333-4444-5555-666666666666",
        "studentName": "Lê Bảo Ngọc",
        "studentCode": "HS00124",
        "averageScorePercentage": 88.0,
        "completedQuizzesCount": 7,
        "totalMessagesSent": 48
      }
    ],
    "topWrongQuestions": [
      {
        "questionId": "q1001-abc",
        "questionContent": "Chiến thắng Điện Biên Phủ trên không diễn ra vào năm nào?",
        "wrongCount": 15
      },
      {
        "questionId": "q1002-def",
        "questionContent": "Hội nghị Diên Hồng do vua Trần Thánh Tông triệu tập năm bao nhiêu?",
        "wrongCount": 12
      }
    ]
  }
}
```

---

### 2.3. Student Personal Dashboard (US-SP6-08)
- **Endpoint:** `GET /api/v1/student/dashboard`
- **Authorized Role:** `ROLE_STUDENT`
- **Description:** Returns the authenticated student's personalized learning statistics, token consumption over the last 30 days, recent quiz results, and era distribution.

#### TypeScript Interface
```typescript
export interface RecentQuizSummary {
  quizSessionId: string;
  quizTitle: string;
  historicalEra: string;
  scorePercentage: number;
  completedAt: string; // ISO 8601
}

export interface EraDistributionSummary {
  eraName: string;
  quizzesTaken: number;
}

export interface StudentPersonalDashboardResponse {
  studentUid: string;
  studentName: string;
  tokensUsedLast30Days: number;
  totalChatMessagesSent: number;
  totalQuizzesTaken: number;
  averageQuizScorePercentage: number;
  recentQuizzes: RecentQuizSummary[];
  eraDistribution: EraDistributionSummary[];
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "Student personal dashboard fetched successfully",
  "data": {
    "studentUid": "u1111111-2222-3333-4444-555555555555",
    "studentName": "Phạm Quốc Huy",
    "tokensUsedLast30Days": 45200,
    "totalChatMessagesSent": 128,
    "totalQuizzesTaken": 14,
    "averageQuizScorePercentage": 89.4,
    "recentQuizzes": [
      {
        "quizSessionId": "qs-777",
        "quizTitle": "Khởi nghĩa Lam Sơn & Triều đại Lê Sơ",
        "historicalEra": "Hậu Lê",
        "scorePercentage": 90.0,
        "completedAt": "2026-10-14T09:30:00Z"
      },
      {
        "quizSessionId": "qs-888",
        "quizTitle": "Ba lần kháng chiến chống quân Nguyên Mông",
        "historicalEra": "Nhà Trần",
        "scorePercentage": 95.0,
        "completedAt": "2026-10-12T14:15:00Z"
      }
    ],
    "eraDistribution": [
      { "eraName": "Hậu Lê", "quizzesTaken": 5 },
      { "eraName": "Nhà Trần", "quizzesTaken": 4 },
      { "eraName": "Nhà Lý", "quizzesTaken": 3 },
      { "eraName": "Cận đại", "quizzesTaken": 2 }
    ]
  }
}
```

---

### 2.4. Customer B2C Personal Dashboard (US-SP6-09)
- **Endpoint:** `GET /api/v1/customer/dashboard`
- **Authorized Role:** `ROLE_CUSTOMER`
- **Description:** Returns the individual retail customer's personal engagement statistics, chat token usage (30-day window), quiz records, and historical interest breakdown.

#### TypeScript Interface
```typescript
export interface CustomerPersonalDashboardResponse {
  customerUid: string;
  customerName: string;
  tokensUsedLast30Days: number;
  totalChatMessagesSent: number;
  totalQuizzesTaken: number;
  averageQuizScorePercentage: number;
  recentQuizzes: RecentQuizSummary[];
  eraDistribution: EraDistributionSummary[];
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "Customer personal dashboard fetched successfully",
  "data": {
    "customerUid": "c9999999-8888-7777-6666-555555555555",
    "customerName": "Đỗ Minh Triết",
    "tokensUsedLast30Days": 12850,
    "totalChatMessagesSent": 45,
    "totalQuizzesTaken": 6,
    "averageQuizScorePercentage": 78.5,
    "recentQuizzes": [
      {
        "quizSessionId": "qs-999",
        "quizTitle": "Chiến dịch Điện Biên Phủ 1954",
        "historicalEra": "Kháng chiến chống Pháp",
        "scorePercentage": 85.0,
        "completedAt": "2026-10-13T20:00:00Z"
      }
    ],
    "eraDistribution": [
      { "eraName": "Kháng chiến chống Pháp", "quizzesTaken": 3 },
      { "eraName": "Thời kỳ dựng nước", "quizzesTaken": 3 }
    ]
  }
}
```

---

### 2.5. System Admin Comprehensive Dashboard & JVM Health (US-SP6-10)
- **Endpoint:** `GET /api/v1/admin/dashboard`
- **Authorized Role:** `ROLE_ADMIN`
- **Description:** High-level platform health, multi-tenant usage metrics, total commercial revenue (VND), and real-time JVM telemetry (Heap memory, CPU load, uptime).

#### TypeScript Interface
```typescript
export interface SystemAdminComprehensiveDashboardResponse {
  totalActiveUsers: number;
  totalSchools: number;
  totalTeachers: number;
  totalStudents: number;
  totalCustomers: number;
  totalAllocatedSchoolQuota: number;
  totalConsumedSchoolTokens: number;
  totalConsumedCustomerTokens: number;
  totalPlatformTokensUsed: number;
  totalRevenueVnd: number;
  jvmHeapUsedBytes: number;
  jvmHeapMaxBytes: number;
  jvmHeapUsedMb: number;
  jvmHeapMaxMb: number;
  jvmCpuUsagePercent: number;
  systemUptimeSeconds: number;
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "System admin comprehensive dashboard fetched successfully",
  "data": {
    "totalActiveUsers": 1250,
    "totalSchools": 8,
    "totalTeachers": 45,
    "totalStudents": 1050,
    "totalCustomers": 155,
    "totalAllocatedSchoolQuota": 25000000,
    "totalConsumedSchoolTokens": 8450200,
    "totalConsumedCustomerTokens": 1120400,
    "totalPlatformTokensUsed": 9570600,
    "totalRevenueVnd": 45850000.0,
    "jvmHeapUsedBytes": 284562432,
    "jvmHeapMaxBytes": 1073741824,
    "jvmHeapUsedMb": 271.38,
    "jvmHeapMaxMb": 1024.0,
    "jvmCpuUsagePercent": 4.12,
    "systemUptimeSeconds": 142580
  }
}
```

---

## 3. Error Codes & Handling Guide

| HTTP Status | Error Scenario | Action for Frontend |
|:---|:---|:---|
| **401 Unauthorized** | Missing or expired JWT token | Redirect to login page or invoke token refresh flow. |
| **403 Forbidden** | Role mismatch (e.g. Student accessing Admin dashboard) or Teacher accessing another teacher's class | Display permission denied alert (`"Bạn không có quyền truy cập dữ liệu này"`). |
| **404 Not Found** | Classroom ID not found or soft-deleted | Show resource not found banner or redirect to class list. |
| **500 Internal Error** | Database connection or unexpected runtime error | Show generic error toast with retry button. |

---

## 4. UI/UX Rendering Recommendations

1. **School Admin Dashboard:**
   - Use a Radial / Gauge Progress Bar to display `quotaUsagePercentage` (`consumedTokens` / `allocatedTokenQuota`). Highlight red if usage exceeds 85%.
   - Display `classrooms` in a clean data table with sorting by `studentCount`.

2. **Teacher Classroom Dashboard:**
   - Student leaderboard: Display top 5 students with medal badges (Gold, Silver, Bronze) based on `averageScorePercentage`.
   - Top Wrong Questions: Render as a warning widget so teachers immediately see which historical concepts require review in class.

3. **Student / Customer Personal Dashboard:**
   - Display `tokensUsedLast30Days` and `totalQuizzesTaken` in summary metric cards.
   - Use a Pie / Donut Chart for `eraDistribution` to show which historical periods the user explores most.
   - Recent quizzes list with badge status (Green for $\ge 80\%$, Orange for $50-79\%$, Red for $< 50\%$).

4. **System Admin Comprehensive Dashboard:**
   - Metrics cards: Active Users, Total Schools, Total Revenue (formatted as Vietnamese currency `đ`), Total Tokens.
   - Realtime Gauge: JVM Heap Memory (`jvmHeapUsedMb` / `jvmHeapMaxMb`) and CPU Load (`jvmCpuUsagePercent` %).
   - Uptime converted to format: `X days Y hours Z minutes`.
