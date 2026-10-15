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

## 2. LUỒNG GIAO BÀI TẬP, THU BÀI VÀ CẬP NHẬT DASHBOARD GIÁO VIÊN

```mermaid
flowchart TD
    A["Giáo viên giao Bài tập cho Lớp:\nChọn bài, đặt Deadline"] --> B["Hệ thống tạo bản ghi\nAssignment_Submission (NOT_STARTED)\ncho toàn bộ HS trong lớp"]
    B --> C["Học sinh vào làm bài tập:\nChat AI hoặc Làm Quiz bấm giờ"]
    C --> D{Thời điểm nộp bài <= Deadline?}
    D -- "Đúng hạn" --> E["COMPLETED\nLưu Điểm/Message Count"]
    D -- "Nộp trễ" --> F["LATE_SUBMITTED\nLưu Điểm/Message Count"]
    E --> G["Cập nhật Bảng điểm\n& Analytics Teacher Dashboard"]
    F --> G
```

---

## 3. DB SCHEMA — Local Content Columns (Flyway V32)

| Bảng | Cột mới | Kiểu | Ý nghĩa |
|------|---------|------|---------|
| `historical_schema.historical_context` | `school_id` | `UUID NULL` | NULL=global, NOT NULL=local |
| `historical_schema.historical_context` | `content_scope` | `VARCHAR(20) NULL` | `CLASS_ONLY` \| `SCHOOL_WIDE` |
| `historical_schema.historical_context` | `approved_by` | `UUID NULL` | UID School Admin đã duyệt |
| `historical_schema."character"` | `school_id` | `UUID NULL` | Tương tự |
| `historical_schema."character"` | `content_scope` | `VARCHAR(20) NULL` | Tương tự |
| `historical_schema."character"` | `approved_by` | `UUID NULL` | Tương tự |

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
