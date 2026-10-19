# 📄 API CONTRACT: LOCAL HISTORY CONTENT (Lịch sử Địa phương)
> **Sprint:** Sprint 6 — US-SP6-02, US-SP6-03, UC-14  
> **Base URL:** `http://localhost:8080/Historical-tell`  
> **Auth:** Bearer JWT required for all endpoints  
> **Viết cho:** Frontend (Thành) & Mobile (nếu cần)  
> **Last updated:** 19/10/2026 — v3 (GV tự do upload & tự publish lên School's History Bank, không cần School Admin duyệt)

---

## 📌 Tổng quan

Local Content là nội dung lịch sử **do Giáo viên tự soạn**, gắn với trường học, **không phải nội dung toàn cầu** của platform.

**Cơ chế mới (v3):**
- Giáo viên có quyền tự do tải lên và **tự xuất bản (self-publish)** nội dung vào ngân hàng lịch sử của trường (School's History Bank) tương tự quyền của Content Admin đối với nội dung toàn cầu.
- Mặc định khi tạo mới, nội dung sẽ được xuất bản ngay (`isPublished = true`, `status = ACTIVE`, `approvedBy = { teacher }`).
- Giáo viên có thể tùy chọn lưu bản nháp (`isPublished: false`) và xuất bản sau bằng API `/submit` hoặc API `/status`.
- Thay đổi phạm vi chia sẻ (`contentScope`: `CLASS_ONLY` ↔ `SCHOOL_WIDE`) **KHÔNG** làm reset trạng thái về `DRAFT`.

| Role | Quyền |
|------|-------|
| `TEACHER` | Tạo / Sửa / Xóa / Tự xuất bản (Publish) nội dung của mình. Quản lý trạng thái (`ACTIVE`, `DRAFT`, `INACTIVE`). Xem nội dung `SCHOOL_WIDE` của GV khác cùng trường. |
| `SCHOOL_ADMIN` | Xem tất cả nội dung trong trường. Có quyền giám sát, thay đổi trạng thái (Ẩn `INACTIVE` / Kích hoạt lại `ACTIVE` / Đưa về `DRAFT`) nếu cần can thiệp. |
| `SCHOOL_STUDENT` | Xem nội dung đã được PUBLISHED (`ACTIVE` & `isPublished = true`) trong trường của mình. |
| `CUSTOMER`, `CONTENT_ADMIN`, `SYSTEM_ADMIN` | Không có quyền truy cập |

---

## 🔑 Enum Reference

### `contentScope`
| Giá trị | Ý nghĩa |
|---------|---------|
| `CLASS_ONLY` | *(Default)* Chỉ Teacher tác giả thấy và dùng trong bài tập lớp mình |
| `SCHOOL_WIDE` | Lưu vào kho dữ liệu chung toàn trường (School's History Bank) — tất cả GV cùng trường đều thấy và dùng |

> 💡 *Lưu ý:* Việc chuyển đổi giữa `CLASS_ONLY` và `SCHOOL_WIDE` không làm mất trạng thái phê duyệt / không reset về DRAFT.

### `status`
| Giá trị | Ý nghĩa |
|---------|---------|
| `ACTIVE` | Đã xuất bản — học sinh và giáo viên trong trường có thể tương tác |
| `DRAFT` | Bản nháp — chỉ tác giả (và School Admin) thấy, học sinh chưa thấy |
| `INACTIVE` | Ẩn tạm thời |

---

## 🧑‍🏫 TEACHER APIs

### 1. Tạo bối cảnh địa phương
```
POST /api/v1/teacher/local-contents/contexts
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "Khởi nghĩa Hai Bà Trưng tại Mê Linh",
  "description": "Mô tả chi tiết sự kiện lịch sử địa phương...",
  "era": "ANCIENT",
  "category": "WAR",
  "year": 40,
  "startYear": 40,
  "endYear": 43,
  "isBC": false,
  "location": "Mê Linh, Vĩnh Phúc",
  "imageUrl": "https://...",
  "videoUrl": "https://...",
  "contentScope": "SCHOOL_WIDE",
  "isPublished": true
}
```

> `contentScope`: `CLASS_ONLY` (default) hoặc `SCHOOL_WIDE`  
> `isPublished`: `true` (default - tự publish ngay) hoặc `false` (lưu nháp DRAFT)  
> `era`: `ANCIENT` | `MEDIEVAL` | `MODERN` | `CONTEMPORARY`  
> `category`: `WAR` | `POLITICS` | `CULTURE` | `REVOLUTION`

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Local context created successfully",
  "data": {
    "id": "uuid",
    "type": "CONTEXT",
    "name": "Khởi nghĩa Hai Bà Trưng tại Mê Linh",
    "description": "...",
    "imageUrl": "...",
    "status": "ACTIVE",
    "isPublished": true,
    "contentScope": "SCHOOL_WIDE",
    "era": "ANCIENT",
    "category": "WAR",
    "year": 40,
    "startYear": 40,
    "endYear": 43,
    "isBC": false,
    "location": "Mê Linh, Vĩnh Phúc",
    "videoUrl": "...",
    "createdBy": {
      "uid": "uuid",
      "userName": "teacher01",
      "fullName": "Nguyễn Văn A"
    },
    "approvedBy": {
      "uid": "uuid",
      "userName": "teacher01",
      "fullName": "Nguyễn Văn A"
    },
    "schoolId": "uuid",
    "schoolName": "THPT Nguyễn Du",
    "createdAt": "2026-10-14T08:00:00",
    "updatedAt": "2026-10-14T08:00:00"
  }
}
```

---

### 2. Danh sách bối cảnh địa phương (Teacher view)
```
GET /api/v1/teacher/local-contents/contexts?search=&page=1&size=10
Authorization: Bearer {TEACHER_TOKEN}
```

> Trả về: context của chính mình (mọi scope) + context SCHOOL_WIDE của GV khác cùng trường.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Local contexts retrieved successfully",
  "data": {
    "content": [ { /* LocalContentResponse */ } ],
    "totalElements": 5,
    "totalPages": 1,
    "currentPage": 1,
    "pageSize": 10,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

---

### 3. Chi tiết bối cảnh địa phương
```
GET /api/v1/teacher/local-contents/contexts/{contextId}
Authorization: Bearer {TEACHER_TOKEN}
```

> ⚠️ CLASS_ONLY: chỉ tác giả mới GET được. GV khác → `403 Unauthorized`.

---

### 4. Cập nhật bối cảnh địa phương
```
PUT /api/v1/teacher/local-contents/contexts/{contextId}
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

> ⚠️ Chỉ **teacher tác giả** mới sửa được.  
> 💡 Thay đổi `contentScope` (`CLASS_ONLY` ↔ `SCHOOL_WIDE`) **KHÔNG** làm reset trạng thái về `DRAFT`.

**Request Body** *(tất cả optional — partial update)*:
```json
{
  "name": "Tên mới (optional)",
  "description": "Mô tả mới (optional)",
  "era": "MEDIEVAL",
  "category": "CULTURE",
  "year": null,
  "startYear": 100,
  "endYear": 200,
  "isBC": false,
  "location": "...",
  "imageUrl": "...",
  "videoUrl": "...",
  "contentScope": "SCHOOL_WIDE",
  "isPublished": true
}
```

---

### 5. Xóa mềm bối cảnh địa phương
```
DELETE /api/v1/teacher/local-contents/contexts/{contextId}
Authorization: Bearer {TEACHER_TOKEN}
```

> Soft-delete (set `deleted_at`). Chỉ tác giả mới xóa được.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Local context deleted successfully",
  "data": null
}
```

---

### 6. Xuất bản bối cảnh lên kho tư liệu trường (Quick Publish / Submit)
```
PUT /api/v1/teacher/local-contents/contexts/{contextId}/submit
Authorization: Bearer {TEACHER_TOKEN}
```

> Dùng khi GV tạo bản nháp (`isPublished: false`, `DRAFT`) và sau đó muốn xuất bản nhanh sang `ACTIVE` (`isPublished: true`).  
> Tự động gán `approvedBy = teacher`.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Context published successfully",
  "data": {
    "id": "uuid",
    "type": "CONTEXT",
    "name": "...",
    "status": "ACTIVE",
    "isPublished": true,
    "approvedBy": {
      "uid": "uuid",
      "userName": "teacher01",
      "fullName": "Nguyễn Văn A"
    }
  }
}
```

---

### 7. Giáo viên thay đổi trạng thái bối cảnh (Status management)
```
PUT /api/v1/teacher/local-contents/contexts/{contextId}/status
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "status": "ACTIVE",
  "note": "Xuất bản lại sau khi chỉnh sửa"
}
```

> **`status` hợp lệ:** `ACTIVE` (publish) | `DRAFT` (nháp) | `INACTIVE` (ẩn)  
> Nếu `status == ACTIVE`, hệ thống tự động set `isPublished = true` và `approvedBy = teacher`.  
> Nếu `status != ACTIVE`, `isPublished = false`.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Context status updated to ACTIVE",
  "data": {
    "id": "uuid",
    "status": "ACTIVE",
    "isPublished": true
  }
}
```

---

### 8. Tạo nhân vật địa phương
```
POST /api/v1/teacher/local-contents/characters
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "Bà Trưng Trắc",
  "title": "Trưng Nữ Vương",
  "background": "Tiểu sử và câu chuyện lịch sử...",
  "personality": "Hướng dẫn AI roleplay: dũng cảm, quyết đoán, yêu nước...",
  "imageUrl": "https://...",
  "modelUrl": "https://...",
  "bornYear": 14,
  "bornMonth": null,
  "bornDay": null,
  "isBornBc": false,
  "deathYear": 43,
  "deathMonth": null,
  "deathDay": null,
  "isDeathBc": false,
  "contextIds": ["uuid-of-local-context"],
  "contentScope": "SCHOOL_WIDE",
  "isPublished": true
}
```

> `isPublished`: `true` (default - tự publish ngay vào kho trường) hoặc `false` (lưu nháp DRAFT).  
> `contextIds`: optional — link nhân vật vào các bối cảnh (có thể để `[]` hoặc bỏ qua).

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Local character created successfully",
  "data": {
    "id": "uuid",
    "type": "CHARACTER",
    "name": "Bà Trưng Trắc",
    "status": "ACTIVE",
    "isPublished": true,
    "contentScope": "SCHOOL_WIDE",
    "approvedBy": {
      "uid": "uuid",
      "userName": "teacher01",
      "fullName": "Nguyễn Văn A"
    }
  }
}
```

---

### 9. Danh sách nhân vật địa phương (Teacher view)
```
GET /api/v1/teacher/local-contents/characters?search=&page=1&size=10
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 10. Chi tiết nhân vật địa phương
```
GET /api/v1/teacher/local-contents/characters/{characterId}
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 11. Cập nhật nhân vật địa phương
```
PUT /api/v1/teacher/local-contents/characters/{characterId}
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body** *(tất cả optional)*:
```json
{
  "name": "...",
  "title": "...",
  "background": "...",
  "personality": "...",
  "imageUrl": "...",
  "modelUrl": "...",
  "bornYear": 14,
  "bornMonth": null,
  "bornDay": null,
  "isBornBc": false,
  "deathYear": 43,
  "deathMonth": null,
  "deathDay": null,
  "isDeathBc": false,
  "contentScope": "SCHOOL_WIDE",
  "isPublished": true
}
```

---

### 12. Xóa mềm nhân vật địa phương
```
DELETE /api/v1/teacher/local-contents/characters/{characterId}
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 13. Xuất bản nhân vật lên kho tư liệu trường (Quick Publish / Submit)
```
PUT /api/v1/teacher/local-contents/characters/{characterId}/submit
Authorization: Bearer {TEACHER_TOKEN}
```

> Giáo viên tự do xuất bản nhân vật (chuyển sang `ACTIVE`, `isPublished: true`).

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Character published successfully",
  "data": {
    "id": "uuid",
    "type": "CHARACTER",
    "status": "ACTIVE",
    "isPublished": true,
    "approvedBy": {
      "uid": "uuid",
      "userName": "teacher01",
      "fullName": "Nguyễn Văn A"
    }
  }
}
```

---

### 14. Giáo viên thay đổi trạng thái nhân vật (Status management)
```
PUT /api/v1/teacher/local-contents/characters/{characterId}/status
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "status": "ACTIVE",
  "note": "..."
}
```

> **`status` hợp lệ:** `ACTIVE` (publish) | `DRAFT` (nháp) | `INACTIVE` (ẩn)

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Character status updated to ACTIVE",
  "data": {
    "id": "uuid",
    "status": "ACTIVE",
    "isPublished": true
  }
}
```

---

## 👨‍💼 SCHOOL ADMIN APIs (Giám sát & Quản trị trường)

> 💡 **Lưu ý:** Giáo viên có thể tự xuất bản nội dung của mình lên School's History Bank. School Admin đóng vai trò giám sát, quản trị toàn trường, có quyền xem tất cả và can thiệp điều chỉnh trạng thái khi cần.

### 15. Xem tất cả bối cảnh địa phương trong trường
```
GET /api/v1/school-admin/local-contents/contexts?search=&scope=&page=1&size=10
Authorization: Bearer {SCHOOL_ADMIN_TOKEN}
```

> **Query params:**  
> - `search`: tìm theo tên (optional)  
> - `scope`: `CLASS_ONLY` | `SCHOOL_WIDE` | *(bỏ trống = tất cả)*

---

### 16. Giám sát / Thay đổi trạng thái bối cảnh địa phương
```
PUT /api/v1/school-admin/local-contents/contexts/{contextId}/status
Authorization: Bearer {SCHOOL_ADMIN_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "status": "ACTIVE",
  "note": "Nội dung phù hợp, đã duyệt."
}
```

> **`status` hợp lệ:** `ACTIVE` | `DRAFT` | `INACTIVE`  
> `note` là optional.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Local context published successfully. School assumes full content responsibility.",
  "data": {
    "id": "uuid",
    "type": "CONTEXT",
    "name": "...",
    "status": "ACTIVE",
    "isPublished": true,
    "approvedBy": {
      "uid": "uuid",
      "userName": "school_admin",
      "fullName": "Nguyễn Admin"
    },
    "...": "..."
  }
}
```

> ⚠️ **Policy Disclaimer:** Trường học chịu trách nhiệm pháp lý về tính xác thực nội dung địa phương do giáo viên đăng tải. HistoryTalk không chịu trách nhiệm.

---

### 17. Xem tất cả nhân vật địa phương trong trường
```
GET /api/v1/school-admin/local-contents/characters?search=&scope=&page=1&size=10
Authorization: Bearer {SCHOOL_ADMIN_TOKEN}
```

---

### 18. Giám sát / Thay đổi trạng thái nhân vật địa phương
```
PUT /api/v1/school-admin/local-contents/characters/{characterId}/status
Authorization: Bearer {SCHOOL_ADMIN_TOKEN}
Content-Type: application/json
```

**Request Body:** *(giống như context)*
```json
{
  "status": "ACTIVE",
  "note": "..."
}
```

---

## 🎓 STUDENT APIs

### 19. Xem bối cảnh địa phương đã duyệt
```
GET /api/v1/student/local-contents/contexts
Authorization: Bearer {SCHOOL_STUDENT_TOKEN}
```

> Trả về tất cả context `is_published = true` thuộc trường của học sinh.  
> Filtering theo lớp cụ thể xử lý ở tầng Assignment.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Published local contexts retrieved successfully — 3 found",
  "data": [
    {
      "id": "uuid",
      "type": "CONTEXT",
      "name": "...",
      "isPublished": true,
      "contentScope": "SCHOOL_WIDE",
      "era": "ANCIENT",
      "schoolId": "uuid",
      "schoolName": "THPT Nguyễn Du",
      "createdAt": "..."
    }
  ]
}
```

---

### 20. Xem nhân vật địa phương đã duyệt
```
GET /api/v1/student/local-contents/characters
Authorization: Bearer {SCHOOL_STUDENT_TOKEN}
```

---

## 📎 TEACHER — Document APIs

> Tài liệu gắn vào local context/character để bổ sung kiến thức cho AI (RAG).
> Sau khi upload, nội dung được gửi async lên AI backend để embed vào vector store.

### 21. Danh sách tài liệu của local context
```
GET /api/v1/teacher/local-contents/contexts/{contextId}/documents
Authorization: Bearer {TEACHER_TOKEN}
```

> School isolation: chỉ Teacher thuộc cùng trường với context mới xem được.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Documents retrieved — 2 found",
  "data": [
    {
      "docId": "uuid",
      "contextId": "uuid-of-context",
      "uid": "uuid-of-teacher",
      "userName": "teacher01",
      "title": "Tài liệu tham khảo khởi nghĩa Hai Bà Trưng",
      "content": "Nội dung text đầy đủ...",
      "fileUrl": null,
      "type": "TEXT",
      "uploadDate": "2026-10-14T08:00:00",
      "updatedDate": null,
      "deletedAt": null
    }
  ]
}
```

---

### 22. Thêm tài liệu vào local context
```
POST /api/v1/teacher/local-contents/contexts/{contextId}/documents
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Tài liệu tham khảo lịch sử địa phương",
  "content": "Nội dung chi tiết về sự kiện, nhân vật...",
  "fileUrl": "https://drive.google.com/...",
  "type": "TEXT"
}
```

> `type`: `TEXT` | `PDF` | `URL` — mặc định `TEXT` nếu không truyền.

**Response `201 Created`:** *(giống schema ở trên)*

---

### 23. Xóa mềm tài liệu khỏi local context
```
DELETE /api/v1/teacher/local-contents/contexts/{contextId}/documents/{docId}
Authorization: Bearer {TEACHER_TOKEN}
```

> Chỉ người đã upload tài liệu mới xóa được. Soft-delete (set `deletedAt`).

---

### 24. Danh sách tài liệu của local character
```
GET /api/v1/teacher/local-contents/characters/{characterId}/documents
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 25. Thêm tài liệu vào local character
```
POST /api/v1/teacher/local-contents/characters/{characterId}/documents
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:** *(giống context — title, content, fileUrl, type)*

---

### 26. Xóa mềm tài liệu khỏi local character
```
DELETE /api/v1/teacher/local-contents/characters/{characterId}/documents/{docId}
Authorization: Bearer {TEACHER_TOKEN}
```

---

## ❌ Error Responses

| HTTP | Code | Trường hợp |
|------|------|-----------|
| `400` | `INVALID_REQUEST` | Thiếu field bắt buộc, scope không hợp lệ |
| `401` | `UNAUTHORIZED` | Chưa đăng nhập hoặc token hết hạn |
| `403` | `UNAUTHORIZED` | Sai role, hoặc Teacher cố sửa nội dung của GV khác, hoặc chưa liên kết trường |
| `404` | `NOT_FOUND` | Content không tồn tại hoặc không thuộc trường của bạn |

**Error Response format:**
```json
{
  "success": false,
  "message": "You can only modify content you created",
  "data": null
}
```

---

## 📦 LocalContentResponse Schema (đầy đủ)

```typescript
interface LocalContentResponse {
  id: string;             // contextId hoặc characterId
  type: "CONTEXT" | "CHARACTER";
  name: string;
  description: string;   // context.description hoặc character.background
  imageUrl?: string;
  status: "DRAFT" | "ACTIVE" | "INACTIVE";
  isPublished: boolean;
  contentScope: "CLASS_ONLY" | "SCHOOL_WIDE";

  // Context-specific (null cho CHARACTER)
  era?: "ANCIENT" | "MEDIEVAL" | "MODERN" | "CONTEMPORARY";
  category?: "WAR" | "POLITICS" | "CULTURE" | "REVOLUTION";
  year?: number;
  startYear?: number;
  endYear?: number;
  isBC?: boolean;
  location?: string;
  videoUrl?: string;

  // Character-specific (null cho CONTEXT)
  title?: string;
  personality?: string;
  modelUrl?: string;

  // Audit
  createdBy?: { uid: string; userName: string; fullName: string };
  approvedBy?: { uid: string; userName: string; fullName: string } | null;
  schoolId: string;
  schoolName: string;
  createdAt: string;  // ISO datetime
  updatedAt: string;
}
```
