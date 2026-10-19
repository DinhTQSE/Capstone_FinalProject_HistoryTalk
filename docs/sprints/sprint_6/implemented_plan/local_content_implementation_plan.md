# 🏗️ IMPLEMENTATION PLAN: Local History Content Feature
> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **US:** US-SP6-02, US-SP6-03, UC-14  
> **Completed:** ✅ 14/10/2026  
> **Developer:** Khải (KhaiVDD)

---

## 1. Tổng quan thiết kế

### Approach: Tái dụng bảng hiện có (Discriminator Column Pattern)

Thay vì tạo bảng mới (`local_historical_context`, `local_character`), ta **tái dụng** bảng `historical_context` và `character` bằng cách thêm 3 cột discriminator:

| Cột mới | Ý nghĩa |
|---------|---------|
| `school_id UUID NULL` | `NULL` = global content. `NOT NULL` = school-owned local content |
| `content_scope VARCHAR(20) NULL` | `CLASS_ONLY` hoặc `SCHOOL_WIDE` |
| `approved_by UUID NULL` | UID của School Admin đã duyệt |

**Lý do chọn approach này:**
- Dùng chung RAG/embedding pipeline cho local content (không cần refactor AI backend).
- Không duplicate business logic (quiz linking, character context mapping).
- Global content hoàn toàn không bị ảnh hưởng (`school_id = NULL`).
- Simple isolation: tất cả queries đều có `WHERE school_id = :schoolId`.

---

## 2. Các file đã tạo/sửa

### 2.1 Database Migration
| File | Mô tả |
|------|-------|
| `V32__add_local_content_fields.sql` | Thêm 3 cột vào `historical_context` và `character` + indexes |

### 2.2 Enum mới
| File | Mô tả |
|------|-------|
| `entity/enums/ContentScope.java` | `CLASS_ONLY` / `SCHOOL_WIDE` |

### 2.3 Entity updates
| File | Thay đổi |
|------|---------|
| `entity/historicalContext/HistoricalContext.java` | Thêm `school`, `contentScope`, `approvedBy` |
| `entity/character/Character.java` | Thêm `school`, `contentScope`, `approvedBy` |

### 2.4 DTOs (package: `dto/localcontent/`)
| File | Mục đích |
|------|---------|
| `CreateLocalContextRequest.java` | Teacher tạo context địa phương |
| `CreateLocalCharacterRequest.java` | Teacher tạo character địa phương |
| `UpdateLocalContextRequest.java` | Cập nhật context (partial) |
| `UpdateLocalCharacterRequest.java` | Cập nhật character (partial, đầy đủ date fields) |
| `UpdateLocalContentStatusRequest.java` | School Admin approve/reject |
| `LocalContentResponse.java` | Unified response (type = CONTEXT / CHARACTER) |

### 2.5 Repositories
| File | Queries chính |
|------|--------------|
| `LocalContextRepository.java` | `findVisibleToTeacher`, `findAllBySchool`, `findPublishedBySchool`, `findByIdAndSchool` |
| `LocalCharacterRepository.java` | Tương tự cho character |

### 2.6 Service
| File | Mô tả |
|------|-------|
| `service/localcontent/LocalContentService.java` | Interface (bao gồm 6 document methods) |
| `service/localcontent/LocalContentServiceImpl.java` | Full implementation: CRUD context/character + 6 document methods + `DocumentRepository` + `AiServiceClient` |

### 2.7 Controllers
| File | Base Path | Role |
|------|-----------|------|
| `controller/localcontent/TeacherLocalContentController.java` | `/api/v1/teacher/local-contents` | `TEACHER` — 16 endpoints (CRUD context/character + 6 document) |
| `controller/localcontent/SchoolAdminLocalContentController.java` | `/api/v1/school-admin/local-contents` | `SCHOOL_ADMIN` |
| `controller/localcontent/StudentLocalContentController.java` | `/api/v1/student/local-contents` | `SCHOOL_STUDENT` |

---

## 3. Business Rules & Security

### 3.1 Ownership Enforcement
```java
// Teacher chỉ sửa/xóa content của mình
if (!content.getCreatedBy().getUid().equals(teacherId)) {
    throw new UnauthorizedException("You can only modify content you created");
}
```

### 3.2 Scope Visibility (Teacher view)
```sql
-- Teacher thấy: content mình tạo (mọi scope) + SCHOOL_WIDE của GV khác
WHERE school_id = :mySchool
  AND (created_by = :myId OR content_scope = 'SCHOOL_WIDE')
  AND deleted_at IS NULL
```

### 3.3 Scope Change & Trạng Thái Xuất Bản
Giáo viên có thể tự do thay đổi `contentScope` (`CLASS_ONLY` ↔ `SCHOOL_WIDE`). Thay đổi này **không** làm mất trạng thái phê duyệt và không reset về `DRAFT`, giúp nội dung đã duyệt/publish vẫn giữ nguyên hiệu lực.

### 3.4 School Isolation
- `school_id` được resolve an toàn từ JWT/SecurityContext thông qua `SecurityUtils.getUserId()` và `userRepository.findById()` (tránh NPE khi `principal` type mismatch).
- Không nhận `school_id` từ request body (không thể giả mạo).
- Mọi query đều filter `school_id = :schoolId`.

---

## 4. Status Lifecycle (Teacher Self-Publishing)

```
Tạo mới ──────────────────────────────────► ACTIVE (is_published=true, approved_by=teacher)
  │ (nếu isPublished=false)                     │
  └────────────────────────► DRAFT              │
                               │                │
            Teacher submit /   │                │
          Teacher set ACTIVE ──┤                │
                               ▼                ▼
                 PUBLISHED (is_published=true, approved_by = teacher_id/admin_id)
                               │                ▲
          Teacher/Admin set    │                │  Teacher/Admin set ACTIVE
          INACTIVE / DRAFT ────┴────────────────┘
```

---

## 5. ContentScope Rules

| Scope | Teacher tác giả | GV khác cùng trường | GV trường khác | School Admin | Student |
|-------|----------------|--------------------|----|---|---|
| `CLASS_ONLY` | ✅ Xem & sửa | ❌ | ❌ | ✅ Xem (giám sát) | ✅ (nếu PUBLISHED) |
| `SCHOOL_WIDE` | ✅ Xem & sửa | ✅ Xem (read-only) | ❌ | ✅ Xem (giám sát) | ✅ (nếu PUBLISHED) |

---

## 6. API Summary Table

### Teacher (`TEACHER` role)
| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/teacher/local-contents/contexts` | Tạo context (mặc định ACTIVE, publish ngay) |
| GET | `/teacher/local-contents/contexts` | Danh sách (có pagination + search) |
| GET | `/teacher/local-contents/contexts/{id}` | Chi tiết |
| PUT | `/teacher/local-contents/contexts/{id}` | Cập nhật (giữ nguyên trạng thái) |
| DELETE | `/teacher/local-contents/contexts/{id}` | Xóa mềm |
| PUT | `/teacher/local-contents/contexts/{id}/submit` | Xuất bản nhanh context sang ACTIVE |
| PUT | `/teacher/local-contents/contexts/{id}/status` | Đổi trạng thái context (ACTIVE/DRAFT/INACTIVE) |
| POST | `/teacher/local-contents/characters` | Tạo character (mặc định ACTIVE, publish ngay) |
| GET | `/teacher/local-contents/characters` | Danh sách |
| GET | `/teacher/local-contents/characters/{id}` | Chi tiết |
| PUT | `/teacher/local-contents/characters/{id}` | Cập nhật (giữ nguyên trạng thái) |
| DELETE | `/teacher/local-contents/characters/{id}` | Xóa mềm |
| PUT | `/teacher/local-contents/characters/{id}/submit` | Xuất bản nhanh character sang ACTIVE |
| PUT | `/teacher/local-contents/characters/{id}/status` | Đổi trạng thái character (ACTIVE/DRAFT/INACTIVE) |

### School Admin (`SCHOOL_ADMIN` role - Giám sát & Quản trị trường)
| Method | Path | Mô tả |
|--------|------|-------|
| GET | `/school-admin/local-contents/contexts` | Tất cả context trong trường |
| PUT | `/school-admin/local-contents/contexts/{id}/status` | Giám sát / thay đổi trạng thái (ACTIVE/DRAFT/INACTIVE) |
| GET | `/school-admin/local-contents/characters` | Tất cả character trong trường |
| PUT | `/school-admin/local-contents/characters/{id}/status` | Giám sát / thay đổi trạng thái (ACTIVE/DRAFT/INACTIVE) |

### Student (`SCHOOL_STUDENT` role)
| Method | Path | Mô tả |
|--------|------|-------|
| GET | `/student/local-contents/contexts` | Published context của trường |
| GET | `/student/local-contents/characters` | Published character của trường |

### Teacher — Document (`TEACHER` role)
| Method | Path | Mô tả |
|--------|------|—-----|
| GET | `/teacher/local-contents/contexts/{id}/documents` | Danh sách documents của context |
| POST | `/teacher/local-contents/contexts/{id}/documents` | Thêm document vào context |
| DELETE | `/teacher/local-contents/contexts/{id}/documents/{docId}` | Xóa mềm document |
| GET | `/teacher/local-contents/characters/{id}/documents` | Danh sách documents của character |
| POST | `/teacher/local-contents/characters/{id}/documents` | Thêm document vào character |
| DELETE | `/teacher/local-contents/characters/{id}/documents/{docId}` | Xóa mềm document |

**Tổng: 26 endpoints**

---

## 7. Known Constraints & Future Work

| Hạng mục | Tình trạng | Ghi chú |
|----------|-----------|---------|
| **Local Quiz** | ⏳ Chưa làm | US-SP6-02 mention quiz nhưng sprint này chỉ scope context + character |
| **Per-class Filtering** | ⏳ Chưa làm | Student hiện thấy tất cả published của trường; per-class filter sẽ xử lý ở tầng Assignment |
| **"Kéo vào lớp" flow** | ⏳ Chưa làm | GV xem SCHOOL_WIDE content → assign vào lớp mình (sẽ làm ở Assignment feature) |
| **Cross-schema FK** | ⚠️ Soft constraint | `school_id` trong `historical_schema.historical_context` không có DB-level FK tới `school` table (khác schema). Integrity enforced at app layer |
| **RAG Integration** | ✅ Partial | Document upload tự động gọi `aiServiceClient.processDocumentAsync()`. Cần test end-to-end với AI backend |
| **Policy Disclaimer UI** | 🔲 FE cần làm | FE phải hiển thị warning khi School Admin click Approve |
