# 📄 API CONTRACT: LOCAL HISTORY CONTENT (Lịch sử Địa phương)
> **Sprint:** Sprint 6 — US-SP6-02, US-SP6-03, UC-14  
> **Base URL:** `http://localhost:8080/Historical-tell`  
> **Auth:** Bearer JWT required for all endpoints  
> **Viết cho:** Frontend (Thành) & Mobile (nếu cần)  
> **Last updated:** 20/10/2026 — v4 (Full Feature Parity: Giáo viên và Trường học sở hữu 100% tính năng như Content Admin: Battle Map, Sửa tài liệu, Gắn kết Nhân vật - Bối cảnh động, Thùng rác trường học & Khôi phục, Ngân hàng Quiz & Câu hỏi, Map Pins & Focuses, Bảo mật cách ly phòng Chat)

---

## 📌 Tổng quan

Local Content là nội dung lịch sử **do Giáo viên tự soạn**, gắn với trường học, **không phải nội dung toàn cầu** của platform.

**Cơ chế hoàn thiện (v4):**
- Giáo viên có quyền tự do tải lên và **tự xuất bản (self-publish)** nội dung vào ngân hàng lịch sử của trường (School's History Bank) với đầy đủ 100% quyền năng tương đương Content Admin.
- **Battle Map:** Bối cảnh địa phương hỗ trợ trường `battleMap` (JSON object) chứa tọa độ địa lý, boundaries, historical layers...
- **Document Updating:** Giáo viên có thể chỉnh sửa tài liệu (`PUT /contexts/{id}/documents/{docId}` và `PUT /characters/{id}/documents/{docId}`), backend tự động kích hoạt AI re-vectorize async.
- **Dynamic Character-Context Linking:** Liên kết động giữa nhân vật và bối cảnh của trường (`POST/DELETE/GET /characters/{id}/contexts`).
- **School Trash & Restore:** Quản lý thùng rác riêng của trường (`/trash/contexts`, `/trash/characters`), hỗ trợ khôi phục (`restore`) và xóa vĩnh viễn (`permanent`).
- **School Quiz & Question Bank:** Giáo viên tạo, sửa, xóa, duyệt xuất bản Quiz và ngân hàng câu hỏi gắn liền với bối cảnh lịch sử trường.
- **Map Pins & Focuses:** Giáo viên và School Admin có toàn quyền tạo Map Pins giáo dục và điều chỉnh Map Camera Focuses trên bối cảnh trường mình.
- **Chat Security Isolation:** Học sinh và giáo viên chỉ có thể chat với bối cảnh/nhân vật của trường mình (hoặc nội dung toàn cầu). Không thể chat chéo sang trường khác.

| Role | Quyền |
|------|-------|
| `TEACHER` | Tạo / Sửa / Xóa / Tự xuất bản (Publish) nội dung của mình. Quản lý tài liệu, gắn bối cảnh, quiz, câu hỏi, bản đồ chiến sự, thùng rác trường. Xem nội dung `SCHOOL_WIDE` của GV khác cùng trường. |
| `SCHOOL_ADMIN` | Xem tất cả nội dung trong trường. Có quyền giám sát, thay đổi trạng thái, quản lý thùng rác, khôi phục nội dung toàn trường. |
| `SCHOOL_STUDENT` | Xem nội dung đã được PUBLISHED (`ACTIVE` & `isPublished = true`) trong trường của mình; làm Quiz, chat với nhân vật/bối cảnh của trường mình. |
| `CUSTOMER`, `CONTENT_ADMIN`, `SYSTEM_ADMIN` | Không có quyền truy cập vào nội dung trường học của trường khác |

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
  "battleMap": {
    "center": [21.1856, 105.7231],
    "zoom": 14,
    "layers": []
  },
  "contentScope": "SCHOOL_WIDE",
  "isPublished": true
}
```

> `battleMap`: *(JSON Object - optional)* Bản đồ chiến sự / bản đồ số tương tác (tọa độ, geojson layer, zoom, markers...)  
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
  "battleMap": {
    "center": [21.1856, 105.7231],
    "zoom": 15
  },
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

## 📸 TEACHER — Media & File Upload APIs (Tải lên ảnh/video/tài liệu)

> Hỗ trợ giáo viên tải ảnh, video, 3D model và file PDF khi tạo hoặc cập nhật bối cảnh / nhân vật lịch sử địa phương.

### 27. Tải ảnh nhanh (Standalone Upload trước khi tạo bối cảnh / nhân vật)
```
POST /api/v1/teacher/local-contents/media/upload
(Hoặc: POST /api/v1/media/map-image)
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: multipart/form-data
```

**Form Data:**
- `file`: File ảnh nhị phân (`PNG`, `JPG`, `JPEG`, `WebP`, tối đa 2 MB).

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Media uploaded successfully",
  "data": {
    "url": "https://...supabase.co/storage/v1/object/public/documents/battle-maps/..."
  }
}
```
> 💡 *Sử dụng:* Dùng khi giáo viên chọn ảnh ở màn hình tạo mới, FE gọi API này để lấy URL công khai và gán vào trường `imageUrl` khi gọi `POST /contexts` hoặc `POST /characters`.

---

### 28. Tải trực tiếp Media cho bối cảnh địa phương (Context Media Upload)
```
POST /api/v1/historical-contexts/{contextId}/media/upload-direct?mediaType=IMAGE_2D
(Hoặc: POST /api/v1/teacher/local-contents/contexts/{contextId}/media/upload-direct?mediaType=IMAGE_2D)
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: multipart/form-data
```

**Query Params:**
- `mediaType`: `IMAGE_2D` (ảnh tối đa 10MB) hoặc `VIDEO` (video tối đa 100MB).
- `width`: integer (optional).
- `height`: integer (optional).

**Form Data:**
- `file`: File nhị phân (`file`).

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Context media uploaded successfully",
  "data": {
    "metadataId": "uuid",
    "entityType": "CONTEXT",
    "entityId": "uuid-of-context",
    "mediaType": "IMAGE_2D",
    "publicUrl": "https://...",
    "viewUrl": "https://...",
    "url": "https://...",
    "storagePath": "CONTEXT/uuid/filename.png",
    "objectPath": "CONTEXT/uuid/filename.png"
  }
}
```
> 💡 *Tự động:* Backend sẽ tự động cập nhật trường `imageUrl` hoặc `videoUrl` của bối cảnh trong DB mà không cần gọi thêm lệnh cập nhật.

---

### 29. Xóa Media khỏi bối cảnh địa phương
```
DELETE /api/v1/historical-contexts/{contextId}/media
(Hoặc: DELETE /api/v1/teacher/local-contents/contexts/{contextId}/media)
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 30. Tải trực tiếp Media cho nhân vật địa phương (Character Media Upload)
```
POST /api/v1/characters/{characterId}/media/upload-direct?mediaType=IMAGE_2D
(Hoặc: POST /api/v1/teacher/local-contents/characters/{characterId}/media/upload-direct?mediaType=IMAGE_2D)
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: multipart/form-data
```

**Query Params:**
- `mediaType`: `IMAGE_2D` (ảnh tối đa 10MB) hoặc `MODEL_3D` (mô hình 3D tối đa 100MB).

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Character media uploaded successfully",
  "data": {
    "metadataId": "uuid",
    "entityType": "CHARACTER",
    "entityId": "uuid-of-character",
    "mediaType": "IMAGE_2D",
    "publicUrl": "https://...",
    "viewUrl": "https://...",
    "url": "https://...",
    "storagePath": "CHARACTER/uuid/filename.png",
    "objectPath": "CHARACTER/uuid/filename.png"
  }
}
```
> 💡 *Tự động:* Backend sẽ tự động cập nhật `imageUrl` (nếu là ảnh) hoặc `modelUrl` (nếu là 3D model) của nhân vật.

---

### 31. Xóa Media khỏi nhân vật địa phương
```
DELETE /api/v1/characters/{characterId}/media
(Hoặc: DELETE /api/v1/teacher/local-contents/characters/{characterId}/media)
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 32. Tải lên và trích xuất text từ file PDF (PDF Extraction)
```
POST /api/v1/documents/pdf/upload-and-extract
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: multipart/form-data
```

**Form Data:**
- `file`: File PDF tài liệu.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "PDF text extracted successfully",
  "data": {
    "text": "Nội dung trích xuất tự động từ PDF...",
    "fileUrl": "uploaded_document.pdf"
  }
}
```
> 💡 *Sử dụng:* Dùng khi giáo viên tải lên tài liệu PDF để tự động điền nội dung vào form tài liệu bối cảnh/nhân vật.

---

### 33. Đính kèm file PDF gốc vào tài liệu
```
POST /api/v1/documents/{docId}/upload-pdf
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: multipart/form-data
```

---

### 34. Sửa tài liệu bối cảnh địa phương (Update Context Document)
```
PUT /api/v1/teacher/local-contents/contexts/{contextId}/documents/{docId}
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Tên tài liệu cập nhật",
  "documentType": "TEXT",
  "content": "Nội dung văn bản lịch sử mới...",
  "author": "GS. Sử Học",
  "publishedYear": 2024,
  "confidenceScore": 0.95
}
```
> 💡 *Tự động:* Backend sẽ tự động cập nhật tài liệu và trigger AI vector reprocessing ở chế độ bất đồng bộ (async).

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Context document updated successfully",
  "data": {
    "id": "uuid-doc",
    "historicalContextId": "uuid-context",
    "title": "Tên tài liệu cập nhật",
    "documentType": "TEXT",
    "content": "Nội dung văn bản lịch sử mới...",
    "author": "GS. Sử Học",
    "publishedYear": 2024,
    "confidenceScore": 0.95,
    "createdAt": "2026-10-20T...",
    "updatedAt": "2026-10-20T..."
  }
}
```

---

### 35. Sửa tài liệu nhân vật địa phương (Update Character Document)
```
PUT /api/v1/teacher/local-contents/characters/{characterId}/documents/{docId}
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Tên tài liệu nhân vật cập nhật",
  "documentType": "TEXT",
  "content": "Tiểu sử chi tiết cập nhật của nhân vật...",
  "author": "Ban Sử học Trường",
  "publishedYear": 2024,
  "confidenceScore": 0.98
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Character document updated successfully",
  "data": {
    "id": "uuid-doc",
    "title": "Tên tài liệu nhân vật cập nhật",
    "documentType": "TEXT",
    "content": "Tiểu sử chi tiết cập nhật...",
    "author": "Ban Sử học Trường",
    "publishedYear": 2024,
    "confidenceScore": 0.98
  }
}
```

---

### 36. Gắn nhân vật vào bối cảnh địa phương (Link Character to Context)
```
POST /api/v1/teacher/local-contents/characters/{characterId}/contexts/{contextId}
(Hoặc: POST /api/v1/characters/{characterId}/contexts/{contextId})
Authorization: Bearer {TEACHER_TOKEN}
```

> Cho phép Teacher liên kết một nhân vật thuộc trường với một bối cảnh thuộc trường mình hoặc bối cảnh toàn cầu.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Character linked to context successfully",
  "data": null
}
```

---

### 37. Hủy liên kết nhân vật khỏi bối cảnh (Unlink Character from Context)
```
DELETE /api/v1/teacher/local-contents/characters/{characterId}/contexts/{contextId}
(Hoặc: DELETE /api/v1/characters/{characterId}/contexts/{contextId})
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Character unlinked from context successfully",
  "data": null
}
```

---

### 38. Lấy danh sách bối cảnh gắn với nhân vật (Get Character's Contexts)
```
GET /api/v1/teacher/local-contents/characters/{characterId}/contexts
(Hoặc: GET /api/v1/characters/{characterId}/contexts)
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Character contexts retrieved successfully",
  "data": [
    {
      "id": "uuid-context",
      "name": "Khởi nghĩa Hai Bà Trưng tại Mê Linh",
      "era": "ANCIENT",
      "category": "WAR",
      "year": 40
    }
  ]
}
```

---

### 39. Xem thùng rác bối cảnh của trường (Get Trashed Contexts)
```
GET /api/v1/teacher/local-contents/trash/contexts?page=1&size=10
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Trashed contexts retrieved successfully",
  "data": {
    "content": [
      {
        "id": "uuid",
        "name": "Bối cảnh đã xóa tạm thời",
        "deletedAt": "2026-10-20T...",
        "schoolId": "uuid-school"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "currentPage": 1,
    "pageSize": 10
  }
}
```

---

### 40. Khôi phục bối cảnh từ thùng rác (Restore Context)
```
POST /api/v1/teacher/local-contents/trash/contexts/{id}/restore
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Local context restored successfully",
  "data": null
}
```

---

### 41. Xóa vĩnh viễn bối cảnh khỏi hệ thống (Permanent Delete Context)
```
DELETE /api/v1/teacher/local-contents/trash/contexts/{id}/permanent
Authorization: Bearer {TEACHER_TOKEN}
```

> ⚠️ **Hành động không thể hoàn tác:** Xóa hoàn toàn bản ghi khỏi CSDL (hard-delete).

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Local context permanently deleted successfully",
  "data": null
}
```

---

### 42. Xem thùng rác nhân vật của trường (Get Trashed Characters)
```
GET /api/v1/teacher/local-contents/trash/characters?page=1&size=10
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Trashed characters retrieved successfully",
  "data": {
    "content": [
      {
        "id": "uuid",
        "name": "Nhân vật đã xóa tạm",
        "deletedAt": "2026-10-20T..."
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "currentPage": 1,
    "pageSize": 10
  }
}
```

---

### 43. Khôi phục nhân vật từ thùng rác (Restore Character)
```
POST /api/v1/teacher/local-contents/trash/characters/{id}/restore
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Local character restored successfully",
  "data": null
}
```

---

### 44. Xóa vĩnh viễn nhân vật khỏi hệ thống (Permanent Delete Character)
```
DELETE /api/v1/teacher/local-contents/trash/characters/{id}/permanent
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Local character permanently deleted successfully",
  "data": null
}
```

---

### 45. Lấy danh sách Quiz của bối cảnh trường (Get Local Context Quizzes)
```
GET /api/v1/teacher/local-contents/contexts/{contextId}/quizzes?page=1&size=10
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Context quizzes retrieved successfully",
  "data": {
    "content": [
      {
        "id": "uuid-quiz",
        "title": "Kiểm tra kiến thức Hai Bà Trưng",
        "description": "Bài ôn tập 15 phút",
        "passingScore": 7,
        "isPublished": true,
        "contextId": "uuid-context"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "currentPage": 1,
    "pageSize": 10
  }
}
```

---

### 46. Tạo mới Quiz cho bối cảnh trường (Create Local Quiz)
```
POST /api/v1/teacher/local-contents/contexts/{contextId}/quizzes
(Hoặc: POST /api/v1/staff/quizzes)
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Kiểm tra kiến thức Hai Bà Trưng",
  "description": "Bài ôn tập 15 phút",
  "passingScore": 7,
  "isPublished": true
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Quiz created successfully",
  "data": {
    "id": "uuid-quiz",
    "title": "Kiểm tra kiến thức Hai Bà Trưng",
    "description": "Bài ôn tập 15 phút",
    "passingScore": 7,
    "isPublished": true,
    "contextId": "uuid-context",
    "questions": []
  }
}
```

---

### 47. Xem chi tiết Quiz kèm bộ câu hỏi (Get Local Quiz Details)
```
GET /api/v1/teacher/local-contents/quizzes/{quizId}
(Hoặc: GET /api/v1/staff/quizzes/{quizId})
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Quiz retrieved successfully",
  "data": {
    "id": "uuid-quiz",
    "title": "Kiểm tra kiến thức Hai Bà Trưng",
    "description": "...",
    "passingScore": 7,
    "isPublished": true,
    "questions": [
      {
        "id": "uuid-question",
        "content": "Cuộc khởi nghĩa Hai Bà Trưng bùng nổ vào năm nào?",
        "options": ["Năm 40", "Năm 43", "Năm 938", "Năm 1010"],
        "correctOption": 0,
        "explanation": "Cuộc khởi nghĩa Hai Bà Trưng nổ ra vào mùa xuân năm 40 sau CN."
      }
    ]
  }
}
```

---

### 48. Sửa Quiz của trường (Update Local Quiz)
```
PUT /api/v1/teacher/local-contents/quizzes/{quizId}
(Hoặc: PUT /api/v1/staff/quizzes/{quizId})
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Tiêu đề Quiz sửa đổi",
  "description": "Mô tả mới",
  "passingScore": 8,
  "isPublished": true
}
```

---

### 49. Xóa mềm Quiz của trường (Delete Local Quiz)
```
DELETE /api/v1/teacher/local-contents/quizzes/{quizId}
(Hoặc: DELETE /api/v1/staff/quizzes/{quizId})
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 50. Bật/Tắt Publish Quiz của trường (Toggle Publish Quiz)
```
PATCH /api/v1/teacher/local-contents/quizzes/{quizId}/publish
(Hoặc: PATCH /api/v1/staff/quizzes/{quizId}/publish)
Authorization: Bearer {TEACHER_TOKEN}
```

**Response `200 OK`:** Trả về đối tượng Quiz với `isPublished` đã được chuyển đổi.

---

### 51. Thêm câu hỏi vào Quiz trường (Add Question to Local Quiz)
```
POST /api/v1/teacher/local-contents/quizzes/{quizId}/questions
(Hoặc: POST /api/v1/staff/quizzes/{quizId}/questions)
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "content": "Ai là người cùng Trưng Trắc lãnh đạo cuộc khởi nghĩa?",
  "options": ["Trưng Nhị", "Lê Chân", "Bát Nàn", "Triệu Thị Trinh"],
  "correctOption": 0,
  "explanation": "Hai chị em Trưng Trắc, Trưng Nhị đã phất cờ khởi nghĩa."
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Question added successfully",
  "data": {
    "id": "uuid-question",
    "content": "Ai là người cùng Trưng Trắc lãnh đạo cuộc khởi nghĩa?",
    "options": ["Trưng Nhị", "Lê Chân", "Bát Nàn", "Triệu Thị Trinh"],
    "correctOption": 0,
    "explanation": "..."
  }
}
```

---

### 52. Cập nhật câu hỏi trong Quiz (Update Question)
```
PUT /api/v1/teacher/local-contents/quizzes/questions/{questionId}
(Hoặc: PUT /api/v1/staff/quizzes/questions/{questionId})
Authorization: Bearer {TEACHER_TOKEN}
Content-Type: application/json
```

**Request Body:**
```json
{
  "content": "Nội dung câu hỏi cập nhật...",
  "options": ["Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D"],
  "correctOption": 1,
  "explanation": "Giải thích chi tiết..."
}
```

---

### 53. Xóa câu hỏi khỏi Quiz (Delete Question)
```
DELETE /api/v1/teacher/local-contents/quizzes/questions/{questionId}
(Hoặc: DELETE /api/v1/staff/quizzes/questions/{questionId})
Authorization: Bearer {TEACHER_TOKEN}
```

---

### 54. Quản lý Map Pins & Camera Focuses (Bản đồ số tương tác)

Giáo viên (`TEACHER`) và `SCHOOL_ADMIN` được cấp quyền quản lý bản đồ trên các bối cảnh thuộc trường mình:
- **Map Pins (`/api/v1/map-pins`):**
  - Giáo viên có thể tạo pin loại `EDUCATIONAL` (`pinType: "EDUCATIONAL"`) trên bối cảnh trường mình qua `POST /api/v1/map-pins`.
  - Giáo viên có thể cập nhật và xóa các pin mà mình hoặc trường mình đã tạo (`PUT /api/v1/map-pins/{id}`, `DELETE /api/v1/map-pins/{id}`).
- **Camera Focuses (`/api/v1/historical-contexts/{contextId}/map-focuses`):**
  - Giáo viên và School Admin có toàn quyền tạo mới (`POST`), sắp xếp thứ tự (`PUT /reorder`), sửa (`PUT /{id}`), xóa (`DELETE /{id}`) các góc camera định vị điểm nhấn chiến sự trên bản đồ bối cảnh của trường.

---

### 55. Quy tắc bảo mật cách ly phòng Chat (Chat Security Isolation)

- **Nguyên tắc cô lập:** Nhân vật và bối cảnh lịch sử thuộc trường học là tài sản dữ liệu nội bộ của trường đó.
- **Quyền hạn truy cập chat (`POST /api/v1/chat/sessions`):**
  - **Học sinh trường (`SCHOOL_STUDENT`):** Chỉ được chat với nhân vật/bối cảnh toàn cầu HOẶC nhân vật/bối cảnh của chính trường học mình đang theo học.
  - **Giáo viên trường (`TEACHER`):** Chỉ được chat với nhân vật/bối cảnh toàn cầu HOẶC nhân vật/bối cảnh của trường mình.
  - **Người dùng khác (`CUSTOMER`, học sinh trường khác, v.v.):** Cố tình tạo phòng chat với nhân vật/bối cảnh địa phương của một trường sẽ nhận lỗi `403 FORBIDDEN` ngay lập tức:
    ```json
    {
      "success": false,
      "message": "Access denied: Character/Context belongs to another educational institution",
      "data": null
    }
    ```

---

## ❌ Error Responses

| HTTP | Code | Trường hợp |
|------|------|-----------|
| `400` | `INVALID_REQUEST` | Thiếu field bắt buộc, scope không hợp lệ |
| `401` | `UNAUTHORIZED` | Chưa đăng nhập hoặc token hết hạn |
| `403` | `UNAUTHORIZED` | Sai role, hoặc Teacher cố sửa nội dung của GV khác, hoặc chưa liên kết trường, hoặc truy cập nội dung trường khác |
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
  battleMap?: Record<string, any> | null;  // GeoJSON, Map layers & coordinates

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
