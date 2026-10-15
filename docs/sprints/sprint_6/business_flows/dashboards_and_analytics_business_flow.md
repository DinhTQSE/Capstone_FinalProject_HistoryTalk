# 📊 SPRINT 6 — BUSINESS FLOW: 5 CHUYÊN HỆ DASHBOARDS & ANALYTICS APIS
> **User Stories:** `US-SP6-06`, `US-SP6-07`, `US-SP6-08`, `US-SP6-09`, `US-SP6-10`  
> **Lập trình viên phụ trách:** Trần Quốc Dinh (`DinhTQ`)  
> **Áp dụng cho:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Mục tiêu:** Cung cấp 5 bộ API Dashboard chuyên biệt theo từng vai trò (School Admin, Teacher, Student, B2C Customer, System Admin), đảm bảo bảo mật Multi-tenancy isolation và hiệu năng tổng hợp truy vấn.

---

## 1. TỔNG QUAN KIẾN TRÚC & PHÂN QUYỀN 5 DASHBOARDS

```mermaid
flowchart TD
    subgraph Clients["Tác nhân (Actors)"]
        ScA["School Admin (B2B)"]
        T["Teacher (B2B)"]
        St["School Student (B2B)"]
        C["Customer (B2C)"]
        SysA["System Admin (Platform)"]
    end

    subgraph Security["Security & Multi-Tenancy Filter"]
        JWT["JWT Claims:\nrole, school_id, uid"]
    end

    subgraph Controllers["Dashboard Controllers"]
        D1["GET /api/v1/school-admin/dashboard\n(US-SP6-06)"]
        D2["GET /api/v1/teacher/dashboard/classrooms/{classId}\n(US-SP6-07)"]
        D3["GET /api/v1/student/dashboard\n(US-SP6-08)"]
        D4["GET /api/v1/customer/dashboard\n(US-SP6-09)"]
        D5["GET /api/v1/admin/dashboard\n(US-SP6-10)"]
    end

    subgraph Services["Analytics & Aggregation Services"]
        DashboardSvc["DashboardService Engine"]
    end

    subgraph Database["PostgreSQL Storage"]
        UserTbl["historical_schema.user"]
        SchoolTbl["historical_schema.school"]
        ClassTbl["historical_schema.classrooms"]
        ClassStudentTbl["historical_schema.class_student"]
        QuizTbl["historical_schema.quiz_session & answer"]
        MsgTbl["historical_schema.message"]
        OrderTbl["historical_schema.payment_order"]
    end

    ScA -->|Token Bearer| D1
    T -->|Token Bearer| D2
    St -->|Token Bearer| D3
    C -->|Token Bearer| D4
    SysA -->|Token Bearer| D5

    D1 & D2 & D3 & D4 & D5 --> JWT --> DashboardSvc
    DashboardSvc --> Database
```

---

## 2. LUỒNG NGHIỆP VỤ CHI TIẾT TỪNG DASHBOARD

### 2.1 US-SP6-06: School Admin Dashboard API (`GET /api/v1/school-admin/dashboard`)
- **Actor:** `SCHOOL_ADMIN` (Multi-tenant: `school_id` lấy từ JWT `UserPrincipal`).
- **Nghiệp vụ:**
  1. Kiểm tra quyền `SCHOOL_ADMIN`, trích xuất `school_id`.
  2. Tổng hợp quy mô trường:
     - Số lớp học: `count(classrooms) WHERE school_id = :schoolId AND deleted_at IS NULL`.
     - Số giáo viên: `count(user) WHERE school_id = :schoolId AND role = 'TEACHER' AND deleted_at IS NULL`.
     - Số học sinh: `count(user) WHERE school_id = :schoolId AND role = 'SCHOOL_STUDENT' AND deleted_at IS NULL`.
  3. Thống kê Quỹ Token:
     - Lấy `total_school_token_quota` và `unallocated_token_quota` từ bảng `school`.
     - Tính `allocated_tokens = total_school_token_quota - unallocated_token_quota`.
     - Tổng hợp `total_tokens_used_by_students`: Tổng số token học sinh trong trường đã dùng trong các tin nhắn AI (`message.token_cost`).
  4. Thống kê Bài tập (Assignment Stats): Tỷ lệ % hoàn thành đúng hạn (`COMPLETED`), nộp trễ (`LATE_SUBMITTED`), chưa làm (`NOT_STARTED`).
- **Sequence Diagram:**

```mermaid
sequenceDiagram
    autonumber
    actor Admin as School Admin
    participant API as DashboardController
    participant Svc as SchoolAdminDashboardService
    participant Repo as DB Repositories

    Admin->>API: GET /api/v1/school-admin/dashboard
    API->>Svc: getSchoolDashboard(principal.getSchoolId())
    Svc->>Repo: Query School info & Token quotas
    Svc->>Repo: Count Classrooms, Teachers, Students in school
    Svc->>Repo: Aggregate student token consumption
    Svc->>Repo: Calculate assignment completion distribution
    Svc-->>API: SchoolAdminDashboardResponse
    API-->>Admin: 200 OK + JSON
```

---

### 2.2 US-SP6-07: Teacher Classroom Analytics Dashboard (`GET /api/v1/teacher/dashboard/classrooms/{classId}`)
- **Actor:** `TEACHER`
- **Nghiệp vụ:**
  1. Kiểm tra quyền `TEACHER`, kiểm tra lớp `{classId}` có thuộc trường `school_id` của giáo viên hay không.
  2. **Ownership Check:** Kiểm tra `classroom.teacher_id == teacher.uid`. Nếu giáo viên khác cố tình xem lớp không phụ trách $\rightarrow$ trả về `403 Forbidden`.
  3. Tổng hợp danh sách học sinh của lớp từ `class_student`:
     - Danh sách học sinh (`uid`, `fullName`, `studentCode`, `tokenBalance`).
     - Điểm Quiz trung bình của từng học sinh: Lấy từ `quiz_session` liên quan đến học sinh đó.
     - Đếm số tin nhắn trò chuyện với nhân vật AI (`ai_chat_message_count`) để đo lường mức độ tương tác (*Engagement Analytics*).
  4. Tỷ lệ hoàn thành bài tập của lớp (On-time vs Late vs Pending).

```mermaid
sequenceDiagram
    autonumber
    actor T as Teacher
    participant API as DashboardController
    participant Svc as TeacherDashboardService
    participant Repo as DB Repositories

    T->>API: GET /api/v1/teacher/dashboard/classrooms/{classId}
    API->>Svc: getClassroomAnalytics(classId, principal)
    Svc->>Repo: findClassroom(classId, schoolId)
    alt Không tìm thấy hoặc khác trường
        Svc-->>API: Throw ResourceNotFoundException (404)
    else Không phải giáo viên phụ trách lớp
        Svc-->>API: Throw AccessDeniedException (403)
    end
    Svc->>Repo: Query students in classroom (class_student)
    Svc->>Repo: Aggregate Quiz scores & AI chat engagement per student
    Svc-->>API: TeacherClassroomDashboardResponse
    API-->>T: 200 OK + JSON
```

---

### 2.3 US-SP6-08: Student Personal Dashboard (`GET /api/v1/student/dashboard`)
- **Actor:** `SCHOOL_STUDENT`
- **Nghiệp vụ:**
  1. Xác thực học sinh từ JWT `uid` và `school_id`.
  2. Lấy danh sách lớp học sinh đang theo học (`class_student` $\rightarrow$ `classroom`).
  3. Lấy số dư Token AI hiện tại (`user.token`), số token đã dùng trong ngày.
  4. Lấy lịch sử Quiz cá nhân:
     - Tổng số bài Quiz đã tham gia (`totalQuizzesAttempted`).
     - Điểm trung bình % (`averageScorePercentage`).
     - Top 5 bài Quiz vừa hoàn thành gần nhất (`recentQuizzes`: `quizTitle`, `percentage`, `completedAt`).
  5. Danh sách bài tập sắp đến hạn nộp (`Upcoming Deadlines`).

---

### 2.4 US-SP6-09: Customer B2C Personal Dashboard (`GET /api/v1/customer/dashboard`)
- **Actor:** `CUSTOMER` / `USER` (Người dùng học tập tự do cá nhân).
- **Nghiệp vụ:**
  1. Xác thực người dùng B2C từ JWT `uid`.
  2. Lấy thông tin Gói cước (`user_tier` $\rightarrow$ `tier`: Free, Plus, Pro, v.v.), số dư Token, hạn dùng.
  3. Lấy phân tích học tập cá nhân:
     - Tổng số Quiz đã làm, điểm trung bình %, phân bố thời kỳ lịch sử đã học (`eraDistribution`).
     - Danh sách 5 bài Quiz gần nhất.
     - Top nhân vật lịch sử trò chuyện nhiều nhất (`topCharacters`: `name`, `messageCount`, `tokenUsed`).
  4. Lấy lịch sử thanh toán PayOS:
     - 5 đơn hàng mua Token / gói gần nhất (`orderCode`, `amount`, `tierName`, `status: PAID`, `createdAt`).

---

### 2.5 US-SP6-10: System Admin Dashboard & JVM Health (`GET /api/v1/admin/dashboard`)
- **Actor:** `SYSTEM_ADMIN`
- **Nghiệp vụ:**
  1. Kiểm tra quyền `SYSTEM_ADMIN`.
  2. **JVM Health Metrics:**
     - Sử dụng `ManagementFactory.getOperatingSystemMXBean()` & `Runtime.getRuntime()`:
       - `cpuUsagePercentage`: % tải CPU.
       - `usedMemoryMb`, `totalMemoryMb`, `maxMemoryMb`, `freeMemoryMb`.
       - `activeThreadCount`: Số lượng luồng đang chạy trong JVM.
       - `uptimeSeconds`: Thời gian ứng dụng đã online liên tục.
  3. **Lưu lượng Token toàn hệ thống:**
     - Tổng token đã cấp cho các Trường học (B2B Quota).
     - Tổng token tiêu thụ của khối B2C và khối B2B.
  4. **Top 10 Câu hỏi Quiz có tỷ lệ làm sai cao nhất hệ thống:**
     - Truy vấn bảng câu trả lời Quiz `quiz_answer`: Lọc các câu hỏi có số lần trả lời sai (`is_correct = false`) nhiều nhất, tính tỷ lệ % trả lời sai.
  5. **Tổng quan Nền tảng:**
     - Tổng doanh thu PayOS lũy kế.
     - Tổng số trường học active, tổng số người dùng active.

---

## 3. MA TRẬN PHÂN QUYỀN VÀ BẢO MẬT (SECURITY MATRIX)

| API Endpoint | Role được phép | Multi-tenancy Isolation |
|---|---|---|
| `GET /api/v1/school-admin/dashboard` | `SCHOOL_ADMIN` | Chỉ xem dữ liệu trong `principal.getSchoolId()` |
| `GET /api/v1/teacher/dashboard/classrooms/{classId}` | `TEACHER` | Chỉ xem lớp học có `teacher_id = teacher.uid` và cùng `school_id` |
| `GET /api/v1/student/dashboard` | `SCHOOL_STUDENT` | Chỉ xem dữ liệu cá nhân của `principal.getUid()` |
| `GET /api/v1/customer/dashboard` | `CUSTOMER` | Chỉ xem dữ liệu cá nhân của `principal.getUid()` |
| `GET /api/v1/admin/dashboard` | `SYSTEM_ADMIN` | Dữ liệu tổng hợp toàn hệ thống |
