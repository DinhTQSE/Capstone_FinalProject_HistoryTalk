# 🔄 BUSINESS FLOW: SCHOOL SUBSCRIPTION, PAY-AS-YOU-GO TOKEN POOL, TEACHER DIRECT TOKEN TRANSFER & TICKET WORKFLOW
> **Sprint:** Sprint 7 (16/10/2026 – 30/10/2026)  
> **Phân hệ:** Quản trị Gói B2B Trường học (01/09 – 31/05), Quỹ Token Chung Pay-As-You-Go, Luồng Giáo viên Cấp Token & Ticket Admin, Upload PDF Local Content & Vòng đời Học sinh  
> **Stories Covered:** `US-SP7-14`, `US-SP7-15`, `US-SP7-16`, `US-SP7-17`, `US-SP7-18`, `US-SP7-19`, `US-SP7-20`  
> **Tác giả:** 🔵 Trần Quốc Dinh (`DinhTQ`)  

---

## 1. TỔNG QUAN KIẾN TRÚC NGHIỆP VỤ

Hệ thống chuyển đổi từ mô hình *"Cấp cứng một lượng token cố định vào từng tài khoản ngay lúc Import học sinh"* sang mô hình **"100% Quỹ Token Chung Pay-As-You-Go + Hạn mức Ngày phân tầng (Học sinh `1.000/ngày`, Giáo viên `10.000/ngày`) + Giáo viên Cấp trực tiếp Token của mình cho Học sinh + Tạo Ticket lên School Admin khi Giáo viên hết Token"**.

### 6 Nguyên tắc Vàng (Core Business Invariants)
1. **Chu kỳ gói trường học (`01/09` – `31/05`):**
   * Gói B2B trường học hoạt động từ **đầu tháng 9 (`01/09`)** đến **cuối tháng 5 (`31/05`)**, gia hạn trong **tháng 5 (`01/05` – `31/05`)** và bảo lưu tối đa **20% token dư** sang năm mới.
2. **Phân tách cơ chế Khóa (Two-Level Locking):**
   * **Hết hạn gói (`EXPIRED`):** Khóa toàn bộ nền tảng của trường (`403 SCHOOL_SUBSCRIPTION_EXPIRED`).
   * **Còn hạn gói nhưng Hết Token (`Out of Tokens`):** Chỉ khóa tính năng Chat AI (`403 AI_QUOTA_EXCEEDED`), toàn bộ tính năng Quiz, Tài liệu, Assignment, Dashboard vẫn mở 100%.
3. **Import Học sinh & Tạo Giáo viên Không Trừ Cứng Quỹ Trường (Pure Shared Pool Pay-As-You-Go):**
   * Lúc Import học sinh hoặc tạo tài khoản Giáo viên, **không trừ bất kỳ token nào từ `school.unallocatedTokenQuota`** (`totalTokensDeducted = 0`).
   * Toàn bộ token của trường nằm cố định tại `school.unallocatedTokenQuota`. Ai xài bao nhiêu khi Chat AI thì mới trừ bấy nhiêu vào tổng token của trường.
4. **Hạn mức Ngày của Giáo viên Lớn hơn Học sinh (`10.000` vs `1.000`):**
   * **Học sinh (`ROLE_SCHOOL_STUDENT`):** Hạn mức mặc định **`1.000 token/ngày`** (`classroom.dailyTokenQuota` hoặc `school.defaultDailyStudentQuota`).
   * **Giáo viên (`ROLE_TEACHER`):** Hạn mức mặc định **`10.000 token/ngày`** (`school.defaultDailyTeacherQuota`) để vừa chat AI phục vụ giảng dạy vừa có quỹ hạn mức ngày để cấp thêm trực tiếp cho học sinh trong lớp.
5. **Luồng Giáo viên Tự Cấp Token cho Học sinh & Escalate Ticket lên School Admin khi Hết Token:**
   * **Khi Học sinh hết token trong ngày & Giáo viên còn hạn mức ngày:** Giáo viên duyệt đơn của học sinh (`APPROVE`) hoặc chủ động bấm cấp token (`grant-tokens`) $\rightarrow$ **Trích trực tiếp từ hạn mức ngày của Giáo viên chuyển sang Học sinh ngay lập tức (`APPROVED`)**, không cần chờ School Admin duyệt.
   * **Khi Giáo viên hết hạn mức ngày:** Giáo viên bấm **Chuyển tiếp (`ESCALATE`)** đơn của học sinh hoặc **Tạo Ticket mới (`POST /api/v1/teacher/token-requests`)** gửi lên **School Admin (`PENDING_ADMIN`)** để School Admin duyệt cấp thêm từ Quỹ chung của trường.
   * **Cả 3 Role (`SCHOOL_STUDENT`, `TEACHER`, `SCHOOL_ADMIN`)** đều xem được danh sách và trạng thái Ticket.
6. **Giáo viên Upload Tài liệu PDF Local Content:**
   * Giáo viên có thể upload trực tiếp file PDF vào Local Context hoặc Local Character để tự động trích xuất văn bản, lưu trữ file PDF và đồng bộ sang AI RAG.

---

## 2. LUỒNG IMPORT HỌC SINH & KHẤU TRỪ PAY-AS-YOU-GO KHI CHAT AI (US-SP7-15 & US-SP7-16)

### 2.1. Sơ đồ So sánh Nghiệp vụ Import Học sinh (Trước và Sau khi Cập nhật)

```mermaid
flowchart LR
    subgraph Before["❌ Nghiệp vụ Cũ (Cấp cứng lúc Import)"]
        B1["Import 500 Học sinh"] --> B2["Cấp cứng 10.000 token/HS"]
        B2 --> B3["Trừ ngay 5.000.000 token khỏi Quỹ trường"]
        B3 --> B4["Học sinh không học cũng làm cạn Quỹ trường (Stranded Tokens)"]
    end

    subgraph After["✅ Nghiệp vụ Mới (Pure Shared Pool Pay-As-You-Go)"]
        A1["Import 500 Học sinh / Tạo Giáo viên"] --> A2["Thiết lập Hạn mức Ngày: HS = 1.000/ngày, GV = 10.000/ngày"]
        A2 --> A3["Trừ Quỹ trường = 0 (Giữ nguyên 100% trong school.unallocatedTokenQuota)"]
        A3 --> A4["Khi HS/GV Chat AI bao nhiêu -> Trừ trực tiếp vào Quỹ trường bấy nhiêu"]
    end
```

### 2.2. Công thức Tính Hạn mức Khả dụng trong Ngày (Student & Teacher)

* **Đối với Học sinh (`ROLE_SCHOOL_STUDENT`):**
  $$\text{dailyBaseLimit}_{\text{student}} = \text{COALESCE}(\text{classroom.dailyTokenQuota}, \text{school.defaultDailyStudentQuota}, 1000)$$
  $$\text{effectiveDailyLimit}_{\text{student}} = \text{dailyBaseLimit}_{\text{student}} + \text{student.dailyBonusTokens}$$
  $$\text{availableToday}_{\text{student}} = \min(\max(0, \text{effectiveDailyLimit}_{\text{student}} - \text{student.dailyTokensUsed}), \text{school.unallocatedTokenQuota})$$

* **Đối với Giáo viên (`ROLE_TEACHER`):**
  $$\text{dailyBaseLimit}_{\text{teacher}} = \text{COALESCE}(\text{school.defaultDailyTeacherQuota}, 10000)$$
  $$\text{effectiveDailyLimit}_{\text{teacher}} = \text{dailyBaseLimit}_{\text{teacher}} + \text{teacher.dailyBonusTokens}$$
  $$\text{availableToday}_{\text{teacher}} = \min(\max(0, \text{effectiveDailyLimit}_{\text{teacher}} - \text{teacher.dailyTokensUsed}), \text{school.unallocatedTokenQuota})$$

---

## 3. LUỒNG GIÁO VIÊN CẤP TOKEN TRỰC TIẾP CHO HỌC SINH & TẠO TICKET LÊN SCHOOL ADMIN KHI GIÁO VIÊN HẾT TOKEN (US-SP7-17)

### 3.1. State Diagram: Vòng đời của Ticket Cấp Token (`TokenBoostRequestStatus`)

```mermaid
stateDiagram-v2
    [*] --> PENDING: Học sinh tạo yêu cầu xin thêm token (POST /api/v1/student/token-requests)
    [*] --> APPROVED: Giáo viên chủ động cấp thẳng token của mình cho HS (POST /api/v1/teacher/students/{id}/grant-tokens)
    [*] --> PENDING_ADMIN: Giáo viên hết token -> Tạo Ticket gửi School Admin (POST /api/v1/teacher/token-requests)

    PENDING --> APPROVED: Giáo viên còn token -> Duyệt cấp ngay từ hạn mức của GV (action = APPROVE)
    PENDING --> PENDING_ADMIN: Giáo viên hết token -> Chuyển tiếp lên School Admin (action = ESCALATE)
    PENDING --> REJECTED: Giáo viên từ chối (action = REJECT)

    PENDING_ADMIN --> APPROVED: School Admin duyệt cấp từ Quỹ chung của trường (action = APPROVE)
    PENDING_ADMIN --> REJECTED: School Admin từ chối (action = REJECT)
```

### 3.2. Sequence Diagram: Toàn bộ Luồng Học sinh — Giáo viên — School Admin

```mermaid
sequenceDiagram
    actor Student as Học sinh (1.000/ngày)
    actor Teacher as Giáo viên (10.000/ngày)
    actor Admin as School Admin (Quỹ Trường)
    participant API as SchoolLifecycleAndTokenController
    participant Svc as TokenBoostRequestServiceImpl
    participant DB as PostgreSQL

    Note over Student,DB: TRƯỜNG HỢP 1: Học sinh hết token, Giáo viên còn hạn mức ngày -> Cấp ngay lập tức (Không cần chờ Admin)
    Student->>API: POST /api/v1/student/token-requests { requestedTokens: 1000, reason: "Cần hỏi thêm nhân vật Trần Hưng Đạo" }
    API->>Svc: createBoostRequest(studentPrincipal, dto)
    Svc->>DB: Lưu Ticket (status = PENDING, requesterRole = STUDENT)
    Svc-->>Student: 201 Created (status = PENDING)

    Teacher->>API: PUT /api/v1/teacher/token-requests/{id}/review { action: "APPROVE", approvedTokens: 1000 }
    API->>Svc: reviewBoostRequest(teacherPrincipal, id, reviewDto)
    Svc->>DB: Kiểm tra teacherRemainingToday >= 1000
    Svc->>DB: Trừ hạn mức ngày của GV (teacher.dailyTokensUsed += 1000, teacher.token -= 1000)
    Svc->>DB: Cộng hạn mức thưởng cho HS (student.dailyBonusTokens += 1000, student.token += 1000)
    Svc->>DB: Cập nhật Ticket status = APPROVED, teacherReviewedByUid = teacher.uid
    Svc-->>Teacher: 200 OK (status = APPROVED - Học sinh dùng được ngay lập tức!)

    Note over Student,DB: TRƯỜNG HỢP 2: Giáo viên cũng hết hạn mức token trong ngày -> Tạo / Chuyển tiếp Ticket lên School Admin duyệt
    alt Cách 2A: Giáo viên Escalate đơn đang PENDING của Học sinh lên Admin
        Teacher->>API: PUT /api/v1/teacher/token-requests/{id}/review { action: "ESCALATE", reviewNote: "GV đã hết hạn mức ngày, nhờ Admin duyệt" }
        API->>Svc: reviewBoostRequest(teacherPrincipal, id, reviewDto)
        Svc->>DB: Cập nhật Ticket status = PENDING_ADMIN, teacherReviewNote
        Svc-->>Teacher: 200 OK (status = PENDING_ADMIN)
    else Cách 2B: Giáo viên tạo Ticket mới gửi thẳng lên School Admin
        Teacher->>API: POST /api/v1/teacher/token-requests { studentUid: "...", requestedTokens: 2000, reason: "Xin cấp thêm cho HS ôn thi" }
        API->>Svc: createTeacherBoostRequest(teacherPrincipal, dto)
        Svc->>DB: Lưu Ticket (status = PENDING_ADMIN, requesterRole = TEACHER)
        Svc-->>Teacher: 201 Created (status = PENDING_ADMIN)
    end

    Admin->>API: PUT /api/v1/school-admin/token-requests/{id}/review { action: "APPROVE", approvedTokens: 2000 }
    API->>Svc: reviewBoostRequest(adminPrincipal, id, reviewDto)
    Svc->>DB: Kiểm tra school.unallocatedTokenQuota >= 2000
    Svc->>DB: Cộng student.dailyBonusTokens += 2000 (hoặc teacher.dailyBonusTokens nếu xin cho GV)
    Svc->>DB: Cập nhật Ticket status = APPROVED, adminReviewedByUid = admin.uid
    Svc-->>Admin: 200 OK (status = APPROVED)

    Note over Student,Admin: CẢ 3 ROLE ĐỀU XEM ĐƯỢC TICKET
    Student->>API: GET /api/v1/token-requests (hoặc /api/v1/student/token-requests)
    Teacher->>API: GET /api/v1/token-requests (hoặc /api/v1/teacher/token-requests)
    Admin->>API: GET /api/v1/token-requests (hoặc /api/v1/school-admin/token-requests)
```

---

## 4. LUỒNG GIÁO VIÊN UPLOAD TÀI LIỆU PDF LOCAL CONTENT (US-SP7-19)

```mermaid
sequenceDiagram
    actor Teacher as Giáo viên (ROLE_TEACHER)
    participant Ctrl as TeacherLocalContentController
    participant Svc as LocalContentServiceImpl
    participant Pdf as PdfExtractionService
    participant Supa as SupabaseDocumentStorageService
    participant AI as AiServiceClient (Async RAG)

    Teacher->>Ctrl: POST /api/v1/teacher/local-contents/contexts/{contextId}/documents/upload-pdf (multipart/form-data: file, title)
    Ctrl->>Svc: uploadLocalContextPdfDocument(contextId, file, title, principal)
    Svc->>Svc: Kiểm tra Context thuộc cùng trường (schoolId) của Giáo viên
    Svc->>Pdf: extractText(file)
    Pdf-->>Svc: PdfExtractionResponse (rawText, pageCount)
    Svc->>Supa: uploadPdf(CONTEXT, contextId, docId, file)
    Supa-->>Svc: objectPath trên Supabase Storage
    Svc->>AI: processDocumentAsync(docId, contextId, extractedText)
    Svc-->>Teacher: 201 Created (HistoricalContextDocumentResponse)
```
