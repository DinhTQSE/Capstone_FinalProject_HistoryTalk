# 📑 FRONTEND API CONTRACT: SCHOOL SUBSCRIPTION, PAY-AS-YOU-GO TOKEN POOL, TEACHER TOKEN TRANSFER, TICKET WORKFLOW & PDF UPLOAD
> **Document Version:** 2.0.0  
> **Sprint:** Sprint 7  
> **Module:** B2B School Subscription (`01/09` – `31/05`), Pure Shared Token Pool (`1.000/day` Student, `10.000/day` Teacher), Teacher Direct Token Grant & Admin Ticket Workflow, Teacher Local Content PDF Upload (`US-SP7-14` to `US-SP7-20`)  
> **Author:** Trần Quốc Dinh (`DinhTQ`) - Backend Developer & DevOps  
> **Base URL:** `/Historical-tell/api/v1`  
> **Authentication:** Bearer Token (`Authorization: Bearer <jwt_access_token>`)

---

## 1. Overview & Business Rules for Frontend

### 1.1. Pure Shared Pool (Pay-As-You-Go) & Daily Token Quotas (`US-SP7-15`)
* **Không trừ Quỹ trường khi Import học sinh:** Khi School Admin import danh sách học sinh, `school.unallocatedTokenQuota` giữ nguyên 100%.
* **Hạn mức ngày mặc định:**
  * **Học sinh (`ROLE_SCHOOL_STUDENT`):** Mặc định **`1.000 token/ngày`** (`classroom.dailyTokenQuota` hoặc `school.defaultDailyStudentQuota`).
  * **Giáo viên (`ROLE_TEACHER`):** Mặc định **`10.000 token/ngày`** (`school.defaultDailyTeacherQuota`) — lớn hơn học sinh để Giáo viên vừa chat AI vừa có thể trích token trong ngày của mình cấp trực tiếp cho học sinh.
* **Khấu trừ thực tế:** Học sinh và Giáo viên chat AI bao nhiêu thì mới trừ bấy nhiêu vào `school.unallocatedTokenQuota`.

### 1.2. Luồng Cấp thêm Token trong ngày & Ticket (`US-SP7-17`)
1. **Khi Học sinh hết token & Giáo viên còn hạn mức ngày (Cấp ngay lập tức — Không chờ School Admin):**
   * **Cách A (Học sinh tạo đơn $\rightarrow$ Giáo viên duyệt):** Học sinh gọi `POST /api/v1/student/token-requests` (`status = PENDING`). Giáo viên bấm Duyệt (`PUT /api/v1/teacher/token-requests/{id}/review` với `action = "APPROVE"`). Hệ thống trích ngay từ hạn mức ngày của Giáo viên sang Học sinh và chuyển trạng thái thành **`APPROVED`**.
   * **Cách B (Giáo viên chủ động cấp trực tiếp cho học sinh):** Giáo viên gọi `POST /api/v1/teacher/students/{studentId}/grant-tokens`. Token được trích từ hạn mức ngày của Giáo viên sang Học sinh ngay lập tức (`status = APPROVED`).
2. **Khi Giáo viên hết hạn mức token trong ngày (Tạo / Chuyển tiếp Ticket lên School Admin):**
   * **Cách A (Chuyển tiếp đơn của học sinh lên Admin):** Giáo viên gọi `PUT /api/v1/teacher/token-requests/{id}/review` với `action = "ESCALATE"` $\rightarrow$ Trạng thái chuyển sang **`PENDING_ADMIN`**.
   * **Cách B (Giáo viên tự tạo Ticket gửi School Admin):** Giáo viên gọi `POST /api/v1/teacher/token-requests` (có thể truyền `studentUid` để xin cấp cho học sinh hoặc để trống `studentUid` để xin thêm hạn mức cho chính Giáo viên) $\rightarrow$ Trạng thái **`PENDING_ADMIN`**.
   * **School Admin duyệt Ticket:** School Admin gọi `PUT /api/v1/school-admin/token-requests/{id}/review` với `action = "APPROVE"` $\rightarrow$ Cấp thêm từ Quỹ chung của trường (`status = APPROVED`).
3. **Cả 3 Role (`SCHOOL_STUDENT`, `TEACHER`, `SCHOOL_ADMIN`) đều xem được Ticket:**
   * Dùng chung `GET /api/v1/token-requests` và `GET /api/v1/token-requests/{requestId}` (hoặc các đường dẫn theo role).

---

## 2. API Endpoints Specification

### 2.1. Cấu hình Hạn mức Token Ngày của Trường (Học sinh & Giáo viên) (`US-SP7-16`)
* **Endpoint:** `PUT /api/v1/school-admin/token-policy`
* **Role:** `ROLE_SCHOOL_ADMIN`

#### Request Body
```json
{
  "defaultDailyStudentQuota": 1000,
  "defaultDailyTeacherQuota": 10000,
  "gradeLevel": 12,
  "classroomDailyQuota": 2000
}
```

---

### 2.2. Học sinh Tạo Yêu cầu Xin thêm Token trong ngày (`US-SP7-17`)
* **Endpoint:** `POST /api/v1/student/token-requests`
* **Role:** `ROLE_SCHOOL_STUDENT`

#### Request Body
```json
{
  "requestedTokens": 1000,
  "reason": "Em đang làm bài tập thảo luận sâu với nhân vật Quang Trung"
}
```

#### Response (`201 Created`)
```json
{
  "code": 201,
  "message": "Đã gửi yêu cầu xin thêm token trong ngày tới Giáo viên",
  "data": {
    "id": "b7777777-8888-9999-aaaa-bbbbbbbbbbbb",
    "schoolId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "classroomId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "className": "10A1",
    "requesterUid": "u1111111-2222-3333-4444-555555555555",
    "requesterName": "Trần Quốc Dinh",
    "requesterRole": "STUDENT",
    "studentUid": "u1111111-2222-3333-4444-555555555555",
    "studentName": "Trần Quốc Dinh",
    "studentCode": "HS1001",
    "requestedTokens": 1000,
    "approvedTokens": null,
    "reason": "Em đang làm bài tập thảo luận sâu với nhân vật Quang Trung",
    "status": "PENDING",
    "requestDate": "2026-10-20"
  }
}
```

---

### 2.3. Giáo viên Chủ động Cấp Trực tiếp Token của mình cho Học sinh (`US-SP7-17`)
* **Endpoint:** `POST /api/v1/teacher/students/{studentId}/grant-tokens`
* **Role:** `ROLE_TEACHER`
* **Mô tả:** Giáo viên trích trực tiếp từ hạn mức token ngày của Giáo viên để cấp ngay cho một học sinh trong lớp mình dạy mà không cần đợi học sinh tạo ticket.

#### Request Body
```json
{
  "tokens": 1500,
  "reason": "Cấp thêm token cho học sinh làm bài thuyết trình nhóm"
}
```

---

### 2.4. Giáo viên Duyệt đơn của Học sinh (Trích từ Token của GV) hoặc Chuyển tiếp lên Admin (`US-SP7-17`)
* **Endpoint:** `PUT /api/v1/teacher/token-requests/{requestId}/review`
* **Role:** `ROLE_TEACHER`, `ROLE_SCHOOL_ADMIN`
* **Giá trị `action`:**
  * `"APPROVE"`: Nếu người gọi là `TEACHER`, hệ thống trích từ hạn mức ngày còn lại của Giáo viên cấp ngay cho Học sinh (`status = APPROVED`).
  * `"ESCALATE"`: Khi Giáo viên đã hết hạn mức ngày, chuyển tiếp ticket lên cho School Admin duyệt (`status = PENDING_ADMIN`).
  * `"REJECT"`: Từ chối yêu cầu (`status = REJECTED`).

#### Request Body (Giáo viên duyệt cấp ngay từ hạn mức của mình)
```json
{
  "action": "APPROVE",
  "approvedTokens": 1000,
  "reviewNote": "Cô trích 1.000 token từ hạn mức của cô cho em hoàn thành bài"
}
```

#### Request Body (Giáo viên hết token -> Chuyển tiếp lên School Admin)
```json
{
  "action": "ESCALATE",
  "reviewNote": "Giáo viên đã hết hạn mức token hôm nay, nhờ School Admin duyệt cấp thêm cho học sinh"
}
```

---

### 2.5. Giáo viên Tạo Ticket Xin School Admin Cấp thêm Token (Khi Giáo viên hết Token) (`US-SP7-17`)
* **Endpoint:** `POST /api/v1/teacher/token-requests`
* **Role:** `ROLE_TEACHER`
* **Mô tả:** Khi Giáo viên hết hạn mức token trong ngày, Giáo viên tạo Ticket gửi thẳng lên School Admin (`status = PENDING_ADMIN`). Có thể truyền `studentUid` để xin cấp cho học sinh cụ thể, hoặc bỏ trống `studentUid` để xin thêm hạn mức cho chính Giáo viên.

#### Request Body
```json
{
  "studentUid": "u1111111-2222-3333-4444-555555555555",
  "classroomId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "requestedTokens": 2000,
  "reason": "Giáo viên đã hết hạn mức ngày, xin Admin cấp thêm 2.000 token cho học sinh ôn thi"
}
```

---

### 2.6. School Admin Duyệt / Từ chối Ticket (`US-SP7-17`)
* **Endpoint:** `PUT /api/v1/school-admin/token-requests/{requestId}/review`
* **Role:** `ROLE_SCHOOL_ADMIN`

#### Request Body
```json
{
  "action": "APPROVE",
  "approvedTokens": 2000,
  "reviewNote": "Admin đã duyệt cấp thêm 2.000 token từ Quỹ trường"
}
```

---

### 2.7. Xem Danh sách & Chi tiết Ticket cho Cả 3 Role (`SCHOOL_STUDENT`, `TEACHER`, `SCHOOL_ADMIN`) (`US-SP7-17`)
* **Endpoints xem danh sách:**
  * Dùng chung cho cả 3 role: `GET /api/v1/token-requests?status=PENDING&page=1&limit=10`
  * Theo từng role:
    * Học sinh: `GET /api/v1/student/token-requests?status=PENDING&page=1&limit=10`
    * Giáo viên: `GET /api/v1/teacher/token-requests?status=PENDING&page=1&limit=10`
    * School Admin: `GET /api/v1/school-admin/token-requests?status=PENDING_ADMIN&page=1&limit=10`
* **Endpoint xem chi tiết 1 Ticket (Cả 3 role):**
  * `GET /api/v1/token-requests/{requestId}`

---

### 2.8. Giáo viên Upload Tài liệu PDF cho Local Context & Local Character (`US-SP7-19`)
* **Upload PDF cho Bối cảnh Lịch sử Địa phương (Local Context):**
  * **Endpoint:** `POST /api/v1/teacher/local-contents/contexts/{contextId}/documents/upload-pdf`
  * **Role:** `ROLE_TEACHER`
  * **Content-Type:** `multipart/form-data`
  * **Form Parts:**
    * `file` (`MultipartFile`, bắt buộc): File PDF.
    * `title` (`String`, tùy chọn): Tiêu đề tài liệu (mặc định lấy tên file PDF nếu bỏ trống).
* **Upload PDF cho Nhân vật Lịch sử Địa phương (Local Character):**
  * **Endpoint:** `POST /api/v1/teacher/local-contents/characters/{characterId}/documents/upload-pdf`
  * **Role:** `ROLE_TEACHER`
  * **Content-Type:** `multipart/form-data`
  * **Form Parts:**
    * `file` (`MultipartFile`, bắt buộc): File PDF.
    * `title` (`String`, tùy chọn): Tiêu đề tài liệu.

---

### 2.9. Giáo viên Upload Media / Ảnh cho Bối cảnh & Nhân vật Lịch sử Địa phương (`US-SP7-19`)
* **Upload Media cho Bối cảnh Địa phương:**
  * **Endpoint:** `POST /api/v1/teacher/local-contents/contexts/{contextId}/media/upload-direct` (hoặc `POST /api/v1/contexts/{contextId}/media/upload-direct`)
  * **Role:** `ROLE_TEACHER`, `ROLE_SCHOOL_ADMIN`, `ROLE_CONTENT_ADMIN`, `ROLE_SYSTEM_ADMIN`
  * **Content-Type:** `multipart/form-data`
  * **Form Parts:**
    * `file` (`MultipartFile`, bắt buộc): File ảnh (PNG/JPG, tối đa 10MB) hoặc Model 3D (tối đa 100MB).
    * `mediaType` (`String`, bắt buộc): `"IMAGE_2D"` hoặc `"MODEL_3D"`.
    * `width`, `height` (`Integer`, tùy chọn).
* **Upload Media cho Nhân vật Địa phương:**
  * **Endpoint:** `POST /api/v1/teacher/local-contents/characters/{characterId}/media/upload-direct` (hoặc `POST /api/v1/characters/{characterId}/media/upload-direct`)
  * **Role:** `ROLE_TEACHER`, `ROLE_SCHOOL_ADMIN`, `ROLE_CONTENT_ADMIN`, `ROLE_SYSTEM_ADMIN`
  * **Content-Type:** `multipart/form-data`
* **Upload Ảnh Nền Lược đồ Bản đồ (Battle Map Image):**
  * **Endpoint:** `POST /api/v1/media/map-image`
  * **Role:** `ROLE_TEACHER`, `ROLE_SCHOOL_ADMIN`, `ROLE_CONTENT_ADMIN`, `ROLE_SYSTEM_ADMIN`
  * **Content-Type:** `multipart/form-data` (file ảnh PNG, JPG, WebP $\le$ 2MB)
  * **Response:** `{ "data": { "url": "https://..." } }` trả về URL vĩnh viễn để gán vào `battleMap.imageUrl` khi lưu bối cảnh.
