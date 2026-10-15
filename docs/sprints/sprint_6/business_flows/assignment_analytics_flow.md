# 🔄 SPRINT 6 — BUSINESS FLOWS & ARCHITECTURE
> **Chủ đề:** Local Content Governance & Disclaimer Policy, Assignment & Deadline Engine, 5 Analytics Dashboards  
> **Áp dụng cho:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Updated:** ✅ 14/10/2026 — Đã reflect đúng implementation thực tế

---

## 1. LUỒNG QUẢN LÝ & DUYỆT NỘI DUNG LỊCH SỬ ĐỊA PHƯƠNG (LOCAL CONTENT WORKFLOW)

### 1.1 Tạo nội dung — Scope CLASS_ONLY (default)

```mermaid
sequenceDiagram
    autonumber
    actor T as Teacher (Trường A)
    actor T2 as Teacher 2 (Trường A)
    actor ScA as School Admin (Trường A)
    participant BE as HistoryTalk Backend

    T->>BE: POST /api/v1/teacher/local-contents/contexts<br/>{name, description, contentScope: "CLASS_ONLY"}
    BE-->>T: 201 Created — status: DRAFT, school_id gắn từ JWT<br/>Chỉ Teacher này thấy

    Note over T,T2: Teacher 2 KHÔNG thấy CLASS_ONLY content của Teacher 1

    T->>BE: PUT /api/v1/teacher/local-contents/contexts/{id}<br/>{contentScope: "SCHOOL_WIDE"}
    BE-->>T: 200 OK — scope đổi, reset về DRAFT, xóa approvedBy

    Note over T,T2: Teacher 2 (cùng Trường A) giờ thấy được content SCHOOL_WIDE
    T2->>BE: GET /api/v1/teacher/local-contents/contexts
    BE-->>T2: Trả về list bao gồm content SCHOOL_WIDE của Teacher 1
```

### 1.2 School Admin duyệt & Student xem

```mermaid
sequenceDiagram
    autonumber
    actor ScA as School Admin (Trường A)
    actor St as Học sinh (Trường A)
    participant BE as HistoryTalk Backend

    ScA->>BE: GET /api/v1/school-admin/local-contents/contexts
    BE-->>ScA: Toàn bộ nội dung trong trường (CLASS_ONLY + SCHOOL_WIDE)

    ScA->>BE: PUT /api/v1/school-admin/local-contents/contexts/{id}/status<br/>{status: "ACTIVE"}
    BE-->>ScA: is_published=true, approved_by=admin_id<br/>POLICY: Nhà trường chịu toàn bộ trách nhiệm nội dung

    St->>BE: GET /api/v1/student/local-contents/contexts
    BE-->>St: Danh sách context is_published=true của Trường A

    ScA->>BE: PUT .../status {status: "DRAFT"}
    BE-->>ScA: is_published=false, approvedBy cleared (revert)
```

### 1.3 Flowchart tổng quan Scope & Status

```mermaid
flowchart TD
    A["Teacher tạo nội dung\nPOST /teacher/local-contents/contexts"] --> B{contentScope?}
    B -- "CLASS_ONLY (default)" --> C["DRAFT — Chỉ Teacher tác giả thấy"]
    B -- "SCHOOL_WIDE" --> D["DRAFT — Tất cả GV cùng trường thấy"]

    C --> E["Submit để School Admin duyệt"]
    D --> E

    E --> H{School Admin review}
    H -- "ACTIVE" --> I["PUBLISHED — is_published=true\napproved_by ghi lại\nPolicy Disclaimer nhà trường"]
    H -- "DRAFT" --> E
    H -- "INACTIVE" --> J["Ẩn tạm thời — is_published=false\napproved_by giữ nguyên audit"]

    I --> K["Học sinh Trường A\nGET /student/local-contents/contexts"]

    I --> L{Teacher đổi scope?}
    L -- "Có" --> M["Reset về DRAFT\nXóa approvedBy\nCần duyệt lại"]
```

---

## 2. LUỒNG GIAO BÀI TẬP, THU BÀI & XỬ LÝ EDGE CASES

### 2.1 Luồng Giáo viên Duyệt Kho nội dung & Giao bài tập (Eager Initialization)

```mermaid
sequenceDiagram
    autonumber
    actor T as Teacher (Trường A)
    participant BE as HistoryTalk Backend
    participant DB as Database (Postgres)

    Note over T,BE: Bước 1: Duyệt Kho Nội dung (Global + Local APPROVED)
    T->>BE: GET /api/v1/historical-contexts hoặc /characters hoặc /quizzes
    BE-->>T: Danh sách nội dung khả dụng (kèm cờ Global/Local)
    
    T->>BE: GET .../{targetId} (Xem chi tiết/Preview nội dung trước khi giao)
    BE-->>T: Chi tiết bối cảnh / tiểu sử nhân vật / bộ câu hỏi quiz

    Note over T,BE: Bước 2: Tạo Bài tập cho Lớp
    T->>BE: POST /api/v1/teacher/assignments<br/>{classId, title, assignmentType, targetId, deadline}
    BE->>DB: 1. Validate Teacher sở hữu Class & Target hợp lệ<br/>2. INSERT INTO assignment (status: 'ACTIVE')
    BE->>DB: 3. Eager Init: SELECT student_id FROM class_student WHERE classroom_id = classId AND status = 'ACTIVE'
    BE->>DB: 4. INSERT INTO assignment_submission (student_id, assignment_id, status: 'NOT_STARTED')
    BE-->>T: 201 Created (Kèm tổng số học sinh đã được khởi tạo submission)
```

### 2.2 Luồng Học sinh Làm bài & Tự động Chấm điểm (Submission State Machine)

```mermaid
flowchart TD
    A["Assignment được giao"] --> B["Bản ghi Submission tạo tự động:\nstatus = NOT_STARTED"]
    
    B --> C["Học sinh bấm vào làm bài:\nstatus = IN_PROGRESS\nstarted_at = NOW()"]
    
    C --> D{Loại bài tập?}
    D -- "QUIZ_TEST" --> E["Học sinh nộp bài trắc nghiệm\nTính score = (đúng / tổng) * 10"]
    D -- "AI_CHAT_ROLEPLAY" --> F["Chat tương tác với Nhân vật AI\nĐếm message_count"]
    D -- "CONTEXT_READING" --> G["Đọc bối cảnh & tài liệu\nĐo reading_duration_seconds"]

    E --> H{Thời điểm nộp <= Deadline?}
    F --> H
    G --> H

    H -- "Đúng hạn" --> I["status = SUBMITTED\nsubmitted_at = NOW()"]
    H -- "Quá hạn" --> J["status = LATE_SUBMITTED\nsubmitted_at = NOW()"]

    B -. "Hết hạn mà HS chưa từng làm/nộp" .-> K["status = MISSED\n(Phục vụ thống kê Dashboard)"]
    
    I --> L["Cập nhật Sổ điểm & Dashboard Giáo viên/Học sinh"]
    J --> L
    K --> L
```

### 2.3 Luồng Xử lý Edge Cases Thực tế

```mermaid
flowchart TD
    subgraph EC1["Edge Case 1: Học sinh mới chuyển vào Lớp"]
        direction TB
        EC1_A["Học sinh mới chuyển vào lớp"] --> EC1_B{Bài tập trong lớp?}
        EC1_B -- "Bài tập đã giao trước đó" --> EC1_C["Đánh dấu EXEMPTED trong sổ điểm\n(Không tính điểm 0, không kéo tụt KPI)"]
        EC1_C --> EC1_D["Chế độ Luyện tập bù (Practice Mode)\nHS vẫn được tự do vào làm củng cố kiến thức"]
        EC1_C --> EC1_E["Giáo viên có thể cấp Custom Deadline\nnếu muốn tính điểm chính thức"]
        EC1_B -- "Bài tập mới giao sau khi vào" --> EC1_F["Tạo NOT_STARTED bình thường"]
    end

    subgraph EC2["Edge Case 2: Học sinh rời khỏi Lớp"]
        direction TB
        EC2_A["Học sinh chuyển lớp hoặc bị gỡ"] --> EC2_B["Chuyển class_student sang TRANSFERRED / INACTIVE"]
        EC2_B --> EC2_C["Bảo toàn 100% dữ liệu submission & điểm số cũ\n(Không xóa bản ghi nào)"]
    end

    subgraph EC3["Edge Case 3: Sửa Deadline hoặc Hủy Bài tập"]
        direction TB
        EC3_A["Giáo viên lùi Deadline (Gia hạn)"] --> EC3_B["Các bài LATE_SUBMITTED nhưng submitted_at <= New Deadline\n-> Tự động chuyển thành SUBMITTED"]
        EC3_C["Giáo viên Hủy bài tập"] --> EC3_D["Soft Delete (assignment status = CANCELLED)\nBảo toàn dữ liệu chat AI & submission cũ"]
    end
```

---

## 3. DB SCHEMA

### 3.1 Local Content Columns (Flyway V32)

| Bảng | Cột mới | Kiểu | Ý nghĩa |
|------|---------|------|---------|
| `historical_schema.historical_context` | `school_id` | `UUID NULL` | NULL=global, NOT NULL=local |
| `historical_schema.historical_context` | `content_scope` | `VARCHAR(20) NULL` | `CLASS_ONLY` \| `SCHOOL_WIDE` |
| `historical_schema.historical_context` | `approved_by` | `UUID NULL` | UID School Admin đã duyệt |
| `historical_schema."character"` | `school_id` | `UUID NULL` | Tương tự |
| `historical_schema."character"` | `content_scope` | `VARCHAR(20) NULL` | Tương tự |
| `historical_schema."character"` | `approved_by` | `UUID NULL` | Tương tự |

### 3.2 Assignment & Submission Tables (Sprint 6 Plan)

```sql
-- historical_schema.assignment
CREATE TABLE historical_schema.assignment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id    UUID NOT NULL REFERENCES historical_schema.classroom(id),
    teacher_id      UUID NOT NULL REFERENCES historical_schema."user"(uid),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    assignment_type VARCHAR(30) NOT NULL, -- 'CONTEXT_READING', 'AI_CHAT_ROLEPLAY', 'QUIZ_TEST'
    target_id       UUID NOT NULL,        -- context_id, character_id, or quiz_id
    deadline        TIMESTAMP WITH TIME ZONE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'CANCELLED', 'ARCHIVED'
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- historical_schema.assignment_submission
CREATE TABLE historical_schema.assignment_submission (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id           UUID NOT NULL REFERENCES historical_schema.assignment(id) ON DELETE CASCADE,
    student_id              UUID NOT NULL REFERENCES historical_schema."user"(uid),
    status                  VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED', -- 'NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED', 'LATE_SUBMITTED', 'MISSED', 'EXEMPTED'
    score                   NUMERIC(4, 2),        -- điểm bài quiz (thang điểm 10)
    message_count           INT DEFAULT 0,         -- số tin nhắn chat AI
    reading_duration_seconds INT DEFAULT 0,        -- thời gian đọc bối cảnh (giây)
    started_at              TIMESTAMP WITH TIME ZONE,
    submitted_at            TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_assignment_student UNIQUE (assignment_id, student_id)
);
```

---

## 4. ISOLATION RULES

| Truy cập | Kết quả |
|---------|---------|
| Teacher Trường A → content Trường B | ❌ 404 Not Found |
| Teacher Trường A → CLASS_ONLY của GV khác Trường A | ❌ 403 Unauthorized |
| Teacher Trường A → SCHOOL_WIDE của GV khác Trường A | ✅ Read-only |
| School Admin Trường A → content Trường B | ❌ 404 Not Found |
| B2C Customer / CONTENT_ADMIN → bất kỳ local content | ❌ 403 Forbidden |
| Student Trường A → DRAFT content | ❌ Không thấy (chỉ PUBLISHED) |
| Student Trường A → PUBLISHED content Trường B | ❌ 404 (filtered by school_id) |
