# 📋 USER STORIES: LOCAL HISTORY CONTENT WORKFLOW & DISCLAIMER POLICY
> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Phân hệ:** Quản lý Nội dung Lịch sử Địa phương (Local Content), Cơ chế Scope Chia sẻ & Phê duyệt  
> **Đã implement:** ✅ Hoàn thành (14/10/2026)

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | Trạng thái |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP6-02`** | Giáo viên Tạo & Chia sẻ Nội dung Lịch sử Địa phương | `Teacher` | 🟢 Khải (KhaiVDD) | ✅ Done |
| **`US-SP6-03`** | School Admin Duyệt Local Content & Policy Disclaimer | `School Admin` | 🟢 Khải (KhaiVDD) | ✅ Done |
| **`UC-14`** | Học sinh Xem Nội dung Địa phương đã Duyệt | `School Student` | 🟢 Khải (KhaiVDD) | ✅ Done |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP6-02: Giáo viên Tạo & Tự Xuất Bản (Publish) Nội dung Lịch sử Địa phương
* **Actor:** `Teacher`
* **Thời gian thực hiện:** 12/10/2026 – 19/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là Giáo viên, tôi muốn tạo mới và tự do xuất bản Bối cảnh hoặc Nhân vật mang tính chất Lịch sử Địa phương lên Ngân hàng lịch sử của trường (School's History Bank) mà không cần phải chờ School Admin duyệt trước (tương tự như Content Admin xuất bản nội dung chung), để phục vụ việc giao bài tập đặc thù của nhà trường một cách nhanh chóng.

* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/teacher/local-contents/contexts` tạo bối cảnh địa phương gắn liền với `school_id` (lấy từ JWT/Security context) và `created_by` (teacher đang đăng nhập). Mặc định `is_published = true`, trạng thái là `ACTIVE`, `approved_by` lưu teacher. Giáo viên có thể tùy chọn lưu `DRAFT` nếu truyền `isPublished: false`.
  2. API `POST /api/v1/teacher/local-contents/characters` tạo nhân vật địa phương tương tự.
  3. Teacher chọn **`contentScope`** lúc tạo:
     - `CLASS_ONLY`: dùng trong phạm vi lớp của Teacher tác giả.
     - `SCHOOL_WIDE` *(mặc định)*: lưu vào kho nội dung toàn trường — GV khác cùng trường có thể xem và dùng.
  4. Thay đổi `contentScope` sau khi tạo **KHÔNG** làm reset trạng thái về DRAFT hay xóa approved_by. GV có thể đổi scope tự do.
  5. API `/submit` và `/status` cho phép Giáo viên chủ động quản lý vòng đời nội dung (`ACTIVE`, `DRAFT`, `INACTIVE`) của chính mình.
  6. Teacher chỉ có thể **sửa/xóa nội dung do chính mình tạo** (ownership enforcement). GV khác không thể sửa dù cùng trường.
  7. Người dùng thuộc trường khác hoặc B2C Customer **tuyệt đối không thể truy cập** nội dung địa phương này.

* **API Endpoints:**

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/v1/teacher/local-contents/contexts` | Tạo bối cảnh địa phương (mặc định publish ngay) |
  | `GET` | `/api/v1/teacher/local-contents/contexts` | Danh sách context teacher có thể thấy |
  | `GET` | `/api/v1/teacher/local-contents/contexts/{contextId}` | Chi tiết context |
  | `PUT` | `/api/v1/teacher/local-contents/contexts/{contextId}` | Cập nhật (chỉ tác giả) |
  | `DELETE` | `/api/v1/teacher/local-contents/contexts/{contextId}` | Xóa mềm (chỉ tác giả) |
  | `PUT` | `/api/v1/teacher/local-contents/contexts/{contextId}/submit` | Xuất bản nhanh bối cảnh lên kho trường |
  | `PUT` | `/api/v1/teacher/local-contents/contexts/{contextId}/status` | Thay đổi trạng thái bối cảnh (ACTIVE/DRAFT/INACTIVE) |
  | `GET` | `/api/v1/teacher/local-contents/contexts/{contextId}/documents` | Danh sách tài liệu của context |
  | `POST` | `/api/v1/teacher/local-contents/contexts/{contextId}/documents` | Thêm tài liệu vào context |
  | `DELETE` | `/api/v1/teacher/local-contents/contexts/{contextId}/documents/{docId}` | Xóa tài liệu (chỉ người upload) |
  | `POST` | `/api/v1/teacher/local-contents/characters` | Tạo nhân vật địa phương (mặc định publish ngay) |
  | `GET` | `/api/v1/teacher/local-contents/characters` | Danh sách character teacher có thể thấy |
  | `GET` | `/api/v1/teacher/local-contents/characters/{characterId}` | Chi tiết character |
  | `PUT` | `/api/v1/teacher/local-contents/characters/{characterId}` | Cập nhật (chỉ tác giả) |
  | `DELETE` | `/api/v1/teacher/local-contents/characters/{characterId}` | Xóa mềm (chỉ tác giả) |
  | `PUT` | `/api/v1/teacher/local-contents/characters/{characterId}/submit` | Xuất bản nhanh nhân vật lên kho trường |
  | `PUT` | `/api/v1/teacher/local-contents/characters/{characterId}/status` | Thay đổi trạng thái nhân vật (ACTIVE/DRAFT/INACTIVE) |
  | `GET` | `/api/v1/teacher/local-contents/characters/{characterId}/documents` | Danh sách tài liệu của character |
  | `POST` | `/api/v1/teacher/local-contents/characters/{characterId}/documents` | Thêm tài liệu vào character |
  | `DELETE` | `/api/v1/teacher/local-contents/characters/{characterId}/documents/{docId}` | Xóa tài liệu (chỉ người upload) |

---

### 🆔 US-SP6-03: School Admin Giám Sát Local Content & Policy Disclaimer Enforcement
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 13/10/2026 – 19/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin, tôi muốn giám sát toàn bộ nội dung Lịch sử Địa phương do Giáo viên trường mình đăng tải lên kho trường, có quyền can thiệp ẩn/kích hoạt lại hoặc đưa về bản nháp nếu nội dung có vấn đề, để đảm bảo chất lượng và tính chuẩn mực.

* **Acceptance Criteria (AC):**
  1. School Admin xem được **toàn bộ** nội dung địa phương trong trường mình (bao gồm `CLASS_ONLY` và `SCHOOL_WIDE`) tại:
     - `GET /api/v1/school-admin/local-contents/contexts`
     - `GET /api/v1/school-admin/local-contents/characters`
  2. API `PUT /api/v1/school-admin/local-contents/contexts/{contextId}/status` và `.../characters/{characterId}/status` cho phép can thiệp chuyển trạng thái:
     - `ACTIVE` → Duyệt/kích hoạt (`is_published = true`, lưu `approved_by`).
     - `DRAFT` → Trả lại GV chỉnh sửa (`is_published = false`, xóa `approved_by`).
     - `INACTIVE` → Ẩn tạm thời (`is_published = false`, giữ `approved_by` để audit).
  3. Khi duyệt (`ACTIVE`), lưu `approved_by = school_admin_id` để phục vụ đối soát nội bộ.
  4. ⚠️ **Policy Disclaimer (Điều khoản miễn trách):** Nhà trường xác nhận chịu **toàn bộ trách nhiệm pháp lý** về tính xác thực của nội dung Lịch sử Địa phương do giáo viên nhà trường đăng tải. Bên cung cấp ứng dụng HistoryTalk hoàn toàn **miễn trừ trách nhiệm** đối với nội dung do nhà trường tự đăng tải.

* **API Endpoints:**

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/v1/school-admin/local-contents/contexts` | Xem tất cả context trong trường |
  | `PUT` | `/api/v1/school-admin/local-contents/contexts/{contextId}/status` | Giám sát / thay đổi trạng thái context |
  | `GET` | `/api/v1/school-admin/local-contents/characters` | Xem tất cả character trong trường |
  | `PUT` | `/api/v1/school-admin/local-contents/characters/{characterId}/status` | Giám sát / thay đổi trạng thái character |

---

### 🆔 UC-14: Học sinh Xem Nội dung Lịch sử Địa phương
* **Actor:** `School Student`
* **User Story:**  
  Là Học sinh, tôi muốn xem được các bối cảnh và nhân vật lịch sử địa phương của trường mình đã được School Admin duyệt, để chuẩn bị hoặc làm bài tập được giáo viên giao.

* **Acceptance Criteria (AC):**
  1. Học sinh chỉ xem được nội dung có `is_published = true` và thuộc `school_id` của mình.
  2. Phân quyền chi tiết theo lớp (`CLASS_ONLY` vs `SCHOOL_WIDE`) được xử lý ở tầng Assignment — học sinh chỉ thực sự tương tác khi GV giao bài tập.
  3. B2C Customer hoặc học sinh trường khác **không thể truy cập**.

* **API Endpoints:**

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/v1/student/local-contents/contexts` | Xem context đã publish của trường |
  | `GET` | `/api/v1/student/local-contents/characters` | Xem character đã publish của trường |

---

## 🏗️ THIẾT KẾ KỸ THUẬT

### Database — Approach: Tái dụng bảng hiện có
Thay vì tạo bảng mới, thêm 3 cột vào `historical_context` và `character`:

```sql
-- Flyway V32
ALTER TABLE historical_schema.historical_context
    ADD COLUMN school_id     UUID        NULL,  -- NULL = global, NOT NULL = local
    ADD COLUMN content_scope VARCHAR(20) NULL,  -- 'CLASS_ONLY' | 'SCHOOL_WIDE'
    ADD COLUMN approved_by   UUID        NULL;  -- School Admin UID

ALTER TABLE historical_schema."character"
    ADD COLUMN school_id     UUID        NULL,
    ADD COLUMN content_scope VARCHAR(20) NULL,
    ADD COLUMN approved_by   UUID        NULL;
```

**Lý do chọn approach này:**
- Dùng chung RAG pipeline (bge-m3 embeddings) cho local content.
- Không duplicate logic query.
- Global content không bị ảnh hưởng (school_id = NULL).

### Scope Visibility Logic
```
Teacher gọi GET /teacher/local-contents/contexts:
  WHERE school_id = :mySchool
    AND (created_by = :myId              ← CLASS_ONLY của mình
      OR content_scope = 'SCHOOL_WIDE')  ← SCHOOL_WIDE của GV khác
```

### ContentScope Enum
```java
public enum ContentScope {
    CLASS_ONLY,   // default — chỉ teacher tác giả thấy
    SCHOOL_WIDE   // toàn bộ GV trong trường thấy
}
```

### Status Lifecycle
```
[DRAFT] ──(School Admin approve)──► [ACTIVE/PUBLISHED]
[ACTIVE] ──(School Admin revert)──► [DRAFT]
[ACTIVE] ──(School Admin hide)────► [INACTIVE]
[DRAFT/ACTIVE] ──(Teacher delete)─► [soft-deleted]
```
