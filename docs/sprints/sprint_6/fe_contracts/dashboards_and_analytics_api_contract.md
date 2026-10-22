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
- **Description:** Returns aggregate KPI overview for the administrator's school, including academic year subscription status (`01/09` – `31/05`), May renewal status, teacher/student/class counts, shared token pool (`unallocatedTokenQuota`), daily student quota policy, and pending token boost requests.

#### TypeScript Interface
```typescript
export interface AssignmentStats {
  totalSubmissions: number;
  completedRate: number;
  lateSubmittedRate: number;
  notStartedRate: number;
}

export interface ClassroomTokenUsageSummaryDto {
  classroomId: string;
  className: string;
  classCode: string;
  gradeLevel: number;
  academicYear: number;
  status: 'ACTIVE' | 'ARCHIVED' | 'DELETED';
  teacherName: string;
  teacherUid: string;
  studentCount: number;
  tokensUsedTotal: number;
  tokensUsedToday: number;
  percentageOfSchoolUsage: number;
}

export interface SchoolAdminDashboardResponse {
  schoolId: string;
  schoolName: string;
  schoolCode: string;
  packageType: 'ENTERPRISE_SMALL' | 'ENTERPRISE_MEDIUM' | 'ENTERPRISE_LARGE';
  recommendedStudents: number;
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'EXPIRED';
  subscriptionStartDate: string; // YYYY-MM-DD (01/09)
  subscriptionEndDate: string;   // YYYY-MM-DD (31/05)
  renewedForNextYear: boolean;
  nextPackageType: string | null;
  totalClassrooms: number;
  totalTeachers: number;
  totalStudents: number;
  totalSchoolTokenQuota: number;
  unallocatedTokenQuota: number; // Remaining School Shared Pool
  allocatedTokens: number;       // Total tokens deducted from Shared Pool
  tokensUsedByStudents: number; // Token tiêu thụ bởi khối Học sinh
  tokensUsedByTeachers: number; // Token tiêu thụ bởi khối Giáo viên
  tokensUsedTotal: number;      // Tổng token toàn trường đã sử dụng
  studentTokenUsagePercentage: number;
  teacherTokenUsagePercentage: number;
  defaultDailyStudentQuota: number;
  pendingTokenBoostRequests: number;
  topClassroomsByTokenUsage: ClassroomTokenUsageSummaryDto[];
  assignmentStats: AssignmentStats;
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "School Admin dashboard retrieved successfully",
  "data": {
    "schoolId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "schoolName": "THPT Chuyên Lê Hồng Phong",
    "schoolCode": "LHP-HCM",
    "packageType": "ENTERPRISE_SMALL",
    "recommendedStudents": 350,
    "status": "ACTIVE",
    "subscriptionStartDate": "2025-09-01",
    "subscriptionEndDate": "2026-05-31",
    "renewedForNextYear": true,
    "nextPackageType": "ENTERPRISE_MEDIUM",
    "totalClassrooms": 20,
    "totalTeachers": 24,
    "totalStudents": 380,
    "totalSchoolTokenQuota": 5000000,
    "unallocatedTokenQuota": 3175500,
    "allocatedTokens": 1824500,
    "tokensUsedByStudents": 1500000,
    "tokensUsedByTeachers": 324500,
    "tokensUsedTotal": 1824500,
    "studentTokenUsagePercentage": 82.2,
    "teacherTokenUsagePercentage": 17.8,
    "defaultDailyStudentQuota": 1000,
    "pendingTokenBoostRequests": 4,
    "topClassroomsByTokenUsage": [
      {
        "classroomId": "b1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "className": "10A1",
        "classCode": "CVA-10A1-2026",
        "gradeLevel": 10,
        "academicYear": 2026,
        "status": "ACTIVE",
        "teacherName": "Thầy Nguyễn Văn A",
        "teacherUid": "t1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "studentCount": 35,
        "tokensUsedTotal": 450000,
        "tokensUsedToday": 15200,
        "percentageOfSchoolUsage": 24.7
      }
    ],
    "assignmentStats": {
      "totalSubmissions": 0,
      "completedRate": 0.0,
      "lateSubmittedRate": 0.0,
      "notStartedRate": 0.0
    }
  }
}
```

---

### 2.2. Teacher Classroom Analytics Dashboard (US-SP6-07)
- **Endpoint:** `GET /api/v1/teacher/dashboard/classrooms/{classId}`
- **Authorized Role:** `ROLE_TEACHER`
- **Description:** Returns pedagogical analytics for a specific classroom managed by the authenticated teacher, including the classroom's daily token limit, pending student token boost requests, and each student's daily token usage (`dailyTokensUsed`, `dailyBonusTokens`, `currentTokens` remaining today).

#### TypeScript Interface
```typescript
export interface AssignmentRates {
  onTimeRate: number;
  lateRate: number;
  pendingRate: number;
}

export interface StudentAnalyticsItem {
  studentId: string;
  studentCode: string;
  fullName: string;
  dailyTokenLimit: number;
  dailyBonusTokens: number;
  dailyTokensUsed: number;
  currentTokens: number; // Remaining daily allowance today
  quizzesAttempted: number;
  averageScorePercentage: number;
  aiMessagesCount: number;
}

export interface TeacherClassroomDashboardResponse {
  classId: string;
  className: string;
  classCode: string;
  gradeLevel: number;
  academicYear: number;
  status: 'ACTIVE' | 'ARCHIVED';
  dailyTokenLimit: number;
  pendingTokenBoostRequests: number;
  studentCount: number;
  totalAiMessages: number;
  avgAiMessagesPerStudent: number;
  assignmentRates: AssignmentRates;
  students: StudentAnalyticsItem[];
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "Teacher classroom dashboard retrieved successfully",
  "data": {
    "classId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "className": "10A1",
    "classCode": "LHP-10A1-2026",
    "gradeLevel": 10,
    "academicYear": 2026,
    "status": "ACTIVE",
    "dailyTokenLimit": 1000,
    "pendingTokenBoostRequests": 2,
    "studentCount": 42,
    "totalAiMessages": 1340,
    "avgAiMessagesPerStudent": 31.9,
    "assignmentRates": {
      "onTimeRate": 0.0,
      "lateRate": 0.0,
      "pendingRate": 0.0
    },
    "students": [
      {
        "studentId": "u1111111-2222-3333-4444-555555555555",
        "studentCode": "HS00123",
        "fullName": "Phạm Quốc Huy",
        "dailyTokenLimit": 1000,
        "dailyBonusTokens": 500,
        "dailyTokensUsed": 450,
        "currentTokens": 1050,
        "quizzesAttempted": 8,
        "averageScorePercentage": 96.5,
        "aiMessagesCount": 65
      }
    ]
  }
}
```

---

### 2.3. Student Personal Dashboard (US-SP6-08)
- **Endpoint:** `GET /api/v1/student/dashboard`
- **Authorized Role:** `ROLE_SCHOOL_STUDENT`
- **Description:** Returns the authenticated student's daily token spending limit (`dailyTokenLimit`), approved bonus tokens today (`dailyBonusTokens`), tokens used today (`tokensUsedToday`), remaining daily allowance (`currentTokens`), quiz performance, and enrolled classrooms across academic years (`ACTIVE` & `ARCHIVED`).

#### TypeScript Interface
```typescript
export interface RecentQuizItem {
  sessionId: string;
  quizTitle: string;
  percentage: number;
  completedAt: string; // ISO 8601
}

export interface EnrolledClassroomItem {
  classId: string;
  className: string;
  classCode: string;
  academicYear: number;
  status: 'ACTIVE' | 'ARCHIVED';
  teacherName: string;
}

export interface UpcomingAssignmentItem {
  assignmentId: string;
  title: string;
  assignmentType: string;
  deadline: string;
}

export interface StudentPersonalDashboardResponse {
  studentId: string;
  studentName: string;
  studentCode: string;
  schoolName: string;
  dailyTokenLimit: number;
  dailyBonusTokens: number;
  currentTokens: number;    // Remaining daily allowance today
  tokensUsedToday: number;  // Tokens spent today
  totalQuizzesAttempted: number;
  averageScorePercentage: number;
  recentQuizzes: RecentQuizItem[];
  enrolledClassrooms: EnrolledClassroomItem[];
  upcomingAssignments: UpcomingAssignmentItem[];
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "Student dashboard retrieved successfully",
  "data": {
    "studentId": "u1111111-2222-3333-4444-555555555555",
    "studentName": "Trần Quốc Dinh",
    "studentCode": "HS1001",
    "schoolName": "THPT Chuyên Lê Hồng Phong",
    "dailyTokenLimit": 1000,
    "dailyBonusTokens": 500,
    "currentTokens": 1200,
    "tokensUsedToday": 300,
    "totalQuizzesAttempted": 14,
    "averageScorePercentage": 89.4,
    "recentQuizzes": [
      {
        "sessionId": "77777777-8888-9999-aaaa-bbbbbbbbbbbb",
        "quizTitle": "Khởi nghĩa Lam Sơn & Triều đại Lê Sơ",
        "percentage": 90.0,
        "completedAt": "2026-10-14T09:30:00"
      }
    ],
    "enrolledClassrooms": [
      {
        "classId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        "className": "11A1",
        "classCode": "LHP-11A1-2026",
        "academicYear": 2026,
        "status": "ACTIVE",
        "teacherName": "Nguyễn Thị Mai"
      },
      {
        "classId": "e11ac10b-58cc-4372-a567-0e02b2c3d111",
        "className": "10A1",
        "classCode": "LHP-10A1-2025",
        "academicYear": 2025,
        "status": "ARCHIVED",
        "teacherName": "Trần Văn Hùng"
      }
    ],
    "upcomingAssignments": []
  }
}
```

---

### 2.4. Customer B2C Personal Dashboard (US-SP6-09)
- **Endpoint:** `GET /api/v1/customer/dashboard`
- **Authorized Role:** `ROLE_CUSTOMER`
- **Description:** Returns the individual retail customer's subscription tier, token balance, quiz records, top historical characters, and PayOS payment history.

#### TypeScript Interface
```typescript
export interface CustomerPersonalDashboardResponse {
  uid: string;
  userName: string;
  email: string;
  currentTier: string;
  currentTokens: number;
  tierExpiresAt: string | null;
  totalQuizzesAttempted: number;
  averageScorePercentage: number;
  eraDistribution: Record<string, number>;
  recentQuizzes: RecentQuizItem[];
  totalAiMessages: number;
  topCharacters: {
    characterId: string;
    characterName: string;
    messageCount: number;
  }[];
  paymentHistory: {
    orderCode: number;
    amount: number;
    tierName: string;
    status: string;
    createdAt: string;
  }[];
}
```

---

### 2.5. System Admin Comprehensive Dashboard & JVM Health (US-SP6-10)
- **Endpoint:** `GET /api/v1/admin/dashboard`
- **Authorized Role:** `ROLE_SYSTEM_ADMIN`
- **Description:** High-level platform health, B2B Shared Token Pool metrics vs B2C token consumption, active vs expired schools count, total commercial revenue (VND), and real-time JVM telemetry.

#### TypeScript Interface
```typescript
export interface SystemAdminComprehensiveDashboardResponse {
  jvmHealth: {
    cpuUsagePercentage: number;
    usedMemoryMb: number;
    totalMemoryMb: number;
    freeMemoryMb: number;
    maxMemoryMb: number;
    memoryUsagePercentage: number;
    activeThreadCount: number;
    uptimeSeconds: number;
  };
  tokenSummary: {
    totalTokensB2bAllocated: number;
    totalTokensB2bRemainingInPools: number;
    totalTokensB2bConsumed: number;
    totalTokensB2cConsumed: number;
    totalTokensSystemConsumed: number;
  };
  topWrongQuestions: {
    questionId: string;
    quizTitle: string;
    wrongAnswers: number;
    totalAnswers: number;
    wrongRate: number;
  }[];
  platformOverview: {
    totalSchools: number;
    activeSchools: number;
    expiredSchools: number;
    totalUsers: number;
    totalQuizzes: number;
    totalRevenue: number;
  };
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "System Admin dashboard retrieved successfully",
  "data": {
    "jvmHealth": {
      "cpuUsagePercentage": 4.1,
      "usedMemoryMb": 271,
      "totalMemoryMb": 512,
      "freeMemoryMb": 241,
      "maxMemoryMb": 1024,
      "memoryUsagePercentage": 52.9,
      "activeThreadCount": 28,
      "uptimeSeconds": 142580
    },
    "tokenSummary": {
      "totalTokensB2bAllocated": 25000000,
      "totalTokensB2bRemainingInPools": 16549800,
      "totalTokensB2bConsumed": 8450200,
      "totalTokensB2cConsumed": 1120400,
      "totalTokensSystemConsumed": 9570600
    },
    "topWrongQuestions": [
      {
        "questionId": "q-001",
        "quizTitle": "Lịch sử Việt Nam thế kỷ 10",
        "wrongAnswers": 45,
        "totalAnswers": 50,
        "wrongRate": 90.0
      }
    ],
    "platformOverview": {
      "totalSchools": 8,
      "activeSchools": 7,
      "expiredSchools": 1,
      "totalUsers": 1250,
      "totalQuizzes": 80,
      "totalRevenue": 45850000
    }
  }
}
```

---


---

### 2.6. School Admin: Classroom Token Analytics (Phân tích Tiêu thụ theo Lớp)
- **Endpoint:** `GET /api/v1/school-admin/analytics/classrooms`
- **Authorized Role:** `ROLE_SCHOOL_ADMIN`, `ROLE_ADMIN`, `ROLE_SYSTEM_ADMIN`
- **Query Parameters:**
  - `academicYear` (number, optional): Năm học (VD: `2026`)
  - `gradeLevel` (number, optional): Khối lớp (`10`, `11`, `12`)
  - `status` (string, optional): Trạng thái lớp (`ACTIVE`, `ARCHIVED`)
  - `search` (string, optional): Tìm theo tên lớp, mã lớp hoặc tên giáo viên phụ trách
  - `page` (number, default: 0), `size` (number, default: 10), `sort` (string, default: `tokensUsedTotal,desc`)

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "Classroom token analytics retrieved successfully",
  "data": [
    {
      "classroomId": "b1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "className": "10A1",
      "classCode": "CVA-10A1-2026",
      "gradeLevel": 10,
      "academicYear": 2026,
      "status": "ACTIVE",
      "teacherName": "Thầy Nguyễn Văn A",
      "teacherUid": "t1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "studentCount": 35,
      "tokensUsedTotal": 450000,
      "tokensUsedToday": 15200,
      "percentageOfSchoolUsage": 24.7
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 20,
    "totalPages": 2,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

### 2.7. Classroom Students Token Usage Drill-Down (Chi tiết Tiêu thụ từng Học sinh trong Lớp)
- **Endpoint:** `GET /api/v1/school-admin/analytics/classrooms/{classroomId}/students`
- **Authorized Role:** `ROLE_SCHOOL_ADMIN`, `ROLE_TEACHER` (phụ trách lớp), `ROLE_ADMIN`, `ROLE_SYSTEM_ADMIN`
- **Query Parameters:**
  - `search` (string, optional): Tìm theo họ tên, mã HS hoặc username
  - `page` (number, default: 0), `size` (number, default: 20), `sort` (string, default: `tokensUsedTotal,desc`)

#### TypeScript Interface
```typescript
export interface StudentTokenUsageDetailDto {
  studentId: string;
  studentCode: string;
  fullName: string;
  userName: string;
  email: string;
  tokensUsedTotal: number;
  tokensUsedToday: number;
  dailyTokenLimit: number;
  dailyBonusTokens: number;
  effectiveRemainingToday: number;
  messageCount: number;
  lastActiveAt: string | null;
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "Classroom students token usage retrieved successfully",
  "data": [
    {
      "studentId": "s1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "studentCode": "HS001",
      "fullName": "Nguyễn Văn An",
      "userName": "cva_hs_hs001",
      "email": "an.nguyen@cva.edu.vn",
      "tokensUsedTotal": 28500,
      "tokensUsedToday": 850,
      "dailyTokenLimit": 1000,
      "dailyBonusTokens": 200,
      "effectiveRemainingToday": 350,
      "messageCount": 42,
      "lastActiveAt": "2026-10-22T09:45:10"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 35,
    "totalPages": 2,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

### 2.8. Teacher: Classes Overview Analytics (Tổng quan Các lớp GV Phụ trách)
- **Endpoint:** `GET /api/v1/teacher/dashboard/classes-overview`
- **Authorized Role:** `ROLE_TEACHER`
- **Description:** Trả về bảng tổng hợp token của tất cả các lớp mà giáo viên hiện tại đang phụ trách, giúp giáo viên dạy 2, 3 lớp nắm bắt trực quan lớp nào tiêu thụ nhiều token nhất.

#### TypeScript Interface
```typescript
export interface TeacherClassesOverviewResponse {
  teacherUid: string;
  teacherName: string;
  totalClassroomsManaged: number;
  totalStudentsManaged: number;
  totalTokensUsedByAllClasses: number;
  classrooms: ClassroomTokenUsageSummaryDto[];
}
```

#### Sample Response (`200 OK`)
```json
{
  "code": 200,
  "message": "Teacher classes overview retrieved successfully",
  "data": {
    "teacherUid": "t1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "teacherName": "Thầy Nguyễn Văn A",
    "totalClassroomsManaged": 2,
    "totalStudentsManaged": 70,
    "totalTokensUsedByAllClasses": 720000,
    "classrooms": [
      {
        "classroomId": "b1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "className": "10A1",
        "classCode": "CVA-10A1-2026",
        "gradeLevel": 10,
        "academicYear": 2026,
        "status": "ACTIVE",
        "teacherName": "Thầy Nguyễn Văn A",
        "teacherUid": "t1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "studentCount": 35,
        "tokensUsedTotal": 450000,
        "tokensUsedToday": 15200,
        "percentageOfSchoolUsage": 62.5
      },
      {
        "classroomId": "b2b2c3d4-e5f6-7890-abcd-ef1234567891",
        "className": "10A2",
        "classCode": "CVA-10A2-2026",
        "gradeLevel": 10,
        "academicYear": 2026,
        "status": "ACTIVE",
        "teacherName": "Thầy Nguyễn Văn A",
        "teacherUid": "t1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "studentCount": 35,
        "tokensUsedTotal": 270000,
        "tokensUsedToday": 8400,
        "percentageOfSchoolUsage": 37.5
      }
    ]
  }
}
```

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
