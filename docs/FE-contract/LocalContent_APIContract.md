# 📄 API CONTRACT: LOCAL HISTORY CONTENT (Lịch sử Địa phương)
> **Sprint:** Sprint 6 — US-SP6-02, US-SP6-03, UC-14  
> **Base URL:** `http://localhost:8080/Historical-tell`  
> **Auth:** Bearer JWT required for all endpoints  
> **Viết cho:** Frontend (Thành) & Mobile (nếu cần)  
> **Last updated:** 14/10/2026

---

## 📌 Tổng quan

Local Content là nội dung lịch sử **do Giáo viên tự soạn**, gắn với trường học, **không phải nội dung toàn cầu** của platform.

| Role | Quyền |
|------|-------|
| `TEACHER` | Tạo / Sửa / Xóa nội dung của mình. Xem nội dung SCHOOL_WIDE của GV khác |
| `SCHOOL_ADMIN` | Xem tất cả nội dung trong trường. Duyệt / Trả lại / Ẩn |
| `SCHOOL_STUDENT` | Chỉ xem nội dung đã được PUBLISHED trong trường |
| `CUSTOMER`, `CONTENT_ADMIN`, `SYSTEM_ADMIN` | Không có quyền truy cập |

---

## 🔑 Enum Reference

### `contentScope`
| Giá trị | Ý nghĩa |
|---------|---------|
| `CLASS_ONLY` | *(Default)* Chỉ Teacher tác giả thấy |
| `SCHOOL_WIDE` | Tất cả GV cùng trường đều thấy (read-only với GV khác) |

### `status` (dùng trong approve endpoint)
| Giá trị | Ý nghĩa |
|---------|---------|
| `ACTIVE` | Duyệt — publish cho học sinh thấy |
| `DRAFT` | Trả lại GV chỉnh sửa |
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
  "contentScope": "CLASS_ONLY"
}
```

> `contentScope`: `CLASS_ONLY` (default) hoặc `SCHOOL_WIDE`  
> `era`: `ANCIENT` | `MEDIEVAL` | `MODERN` | `CONTEMPORARY`  
> `category`: `WAR` | `POLITICS` | `CULTURE` | `REVOLUTION`

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Local context created successfully (DRAFT)",
  "data": {
    "id": "uuid",
    "type": "CONTEXT",
    "name": "Khởi nghĩa Hai Bà Trưng tại Mê Linh",
    "description": "...",
    "imageUrl": "...",
    "status": "DRAFT",
    "isPublished": false,
    "contentScope": "CLASS_ONLY",
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
    "approvedBy": null,
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
> ⚠️ Thay đổi `contentScope` → **reset về DRAFT** + xóa `approvedBy`.

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
  "contentScope": "SCHOOL_WIDE"
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

### 6. Tạo nhân vật địa phương
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
  "contentScope": "SCHOOL_WIDE"
}
```

> `contextIds`: optional — link nhân vật vào các bối cảnh (có thể để `[]` hoặc bỏ qua).

---

### 7. Danh sách nhân vật địa phương (Teacher view)
```
GET /api/v1/teacher/local-contents/characters?search=&page=1&size=10
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 8. Chi tiết nhân vật địa phương
```
GET /api/v1/teacher/local-contents/characters/{characterId}
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 9. Cập nhật nhân vật địa phương
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
  "contentScope": "CLASS_ONLY"
}
```

---

### 10. Xóa mềm nhân vật địa phương
```
DELETE /api/v1/teacher/local-contents/characters/{characterId}
Authorization: Bearer {TEACHER_TOKEN}
```

---

## 👨‍💼 SCHOOL ADMIN APIs

### 11. Xem tất cả bối cảnh địa phương trong trường
```
GET /api/v1/school-admin/local-contents/contexts?search=&scope=&page=1&size=10
Authorization: Bearer {SCHOOL_ADMIN_TOKEN}
```

> **Query params:**  
> - `search`: tìm theo tên (optional)  
> - `scope`: `CLASS_ONLY` | `SCHOOL_WIDE` | *(bỏ trống = tất cả)*

---

### 12. Duyệt / Thay đổi trạng thái bối cảnh địa phương
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

> ⚠️ **Policy Disclaimer:** Bằng cách duyệt, nhà trường chịu toàn bộ trách nhiệm pháp lý về tính xác thực nội dung. HistoryTalk không chịu trách nhiệm.

---

### 13. Xem tất cả nhân vật địa phương trong trường
```
GET /api/v1/school-admin/local-contents/characters?search=&scope=&page=1&size=10
Authorization: Bearer {SCHOOL_ADMIN_TOKEN}
```

---

### 14. Duyệt / Thay đổi trạng thái nhân vật địa phương
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

### 15. Xem bối cảnh địa phương đã duyệt
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

### 16. Xem nhân vật địa phương đã duyệt
```
GET /api/v1/student/local-contents/characters
Authorization: Bearer {SCHOOL_STUDENT_TOKEN}
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
