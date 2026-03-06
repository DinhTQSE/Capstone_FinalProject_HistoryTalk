# Enum Fields Plan – EventEra, EventCategory, MessageRole

**Status:** ⏳ Pending Implementation  
**Created:** March 6, 2026

---

## 1. Phân Tích Hiện Trạng

### `HistoricalContextDocument` (entity hiện tại)
| Column | Type | Ghi chú |
|--------|------|---------|
| `doc_id` | UUID | PK |
| `title` | String (255) | |
| `content` | TEXT | |
| `context_id` | FK | → HistoricalContext |
| `staff_id` | FK | → Staff |
| `upload_date` | LocalDateTime | |
| `updated_date` | LocalDateTime | |
| ❌ `era` | — | **Chưa có** |
| ❌ `category` | — | **Chưa có** |
| ❌ `period` | — | **Chưa có** |

### `Message` (entity hiện tại)
| Column | Type | Ghi chú |  
|--------|------|---------|
| `message_id` | UUID | PK |
| `content` | TEXT | |
| `is_from_ai` | Boolean | true → AI, false → user |
| `session_id` | FK | → ChatSession |
| `timestamp` | LocalDateTime | |
| ❌ `role` | — | **Chưa có** (hiện dùng `is_from_ai` boolean) |

---

## 2. Enums Cần Tạo

### `EventEra` — `enum/EventEra.java`
```
ANCIENT     → "Cổ đại"   → trước 938
MEDIEVAL    → "Trung đại" → 938 – 1857
MODERN      → "Cận đại"  → 1858 – 1944
CONTEMPORARY → "Hiện đại" → 1945 – nay
```
Package: `com.historyTalk.entity.enums`

### `EventCategory` — `enum/EventCategory.java`
```
WAR         → "Chiến tranh"
POLITICS    → "Chính trị"
CULTURE     → "Văn hoá"
SCIENCE     → "Khoa học"
RELIGION    → "Tôn giáo"
OTHER       → "Khác"
```
Package: `com.historyTalk.entity.enums`

### `MessageRole` — `enum/MessageRole.java`
```
USER        → tin nhắn của người dùng
ASSISTANT   → tin nhắn của nhân vật AI
```
Package: `com.historyTalk.entity.enums`

---

## 3. Solution

### 3.1 Lưu Enum trong DB
Dùng `@Enumerated(EnumType.STRING)` để lưu tên enum dưới dạng VARCHAR thay vì INT — dễ debug, không bị lệch thứ tự khi thêm value mới.

### 3.2 `HistoricalContextDocument` — Thêm 3 column

| Column DB | Java field | Java type | Constraint |
|-----------|-----------|-----------|------------|
| `era` | `era` | `EventEra` | `nullable = true` (optional — tài liệu cũ chưa có) |
| `category` | `category` | `EventCategory` | `nullable = true` (optional) |
| `period` | `period` | `String` (length 100) | `nullable = true` — ví dụ: "938 – 1857" |

> `period` là text mô tả khoảng năm tự do (FE có thể auto-fill dựa trên `era`, hoặc staff nhập tay).

### 3.3 `Message` — Thêm `role` column

Thêm field `role` (`MessageRole` enum) song song với `is_from_ai`:
- `is_from_ai` **giữ nguyên** để tránh breaking change với code cũ.
- `role` thêm mới, `nullable = true`.
- `@PrePersist` tự derive `role` từ `is_from_ai` nếu `role == null`:
  - `is_from_ai = true` → `role = ASSISTANT`
  - `is_from_ai = false` → `role = USER`

---

## 4. Files Cần Thay Đổi

### 🆕 Tạo Mới

| File | Nội dung |
|------|---------|
| `entity/enums/EventEra.java` | Enum với 4 values |
| `entity/enums/EventCategory.java` | Enum với 6 values |
| `entity/enums/MessageRole.java` | Enum với 2 values |

### ✏️ Chỉnh Sửa Entity

| File | Thay đổi |
|------|---------|
| `entity/historicalContext/HistoricalContextDocument.java` | Thêm fields: `era`, `category`, `period` |
| `entity/chat/Message.java` | Thêm field: `role`; cập nhật `@PrePersist` để auto-derive |

### ✏️ Chỉnh Sửa DTOs

| File | Thay đổi |
|------|---------|
| `dto/historicalContext/CreateHistoricalContextDocumentRequest.java` | Thêm `era` (`EventEra`), `category` (`EventCategory`), `period` (`String`) — tất cả optional |
| `dto/historicalContext/UpdateHistoricalContextDocumentRequest.java` | Tương tự — optional |
| `dto/historicalContext/HistoricalContextDocumentResponse.java` | Thêm `era`, `category`, `period` vào response |

> `MessageRole` không cần DTO riêng vì `Message` chưa có CRUD API công khai — giữ scope nhỏ.

---

## 5. Không Cần Thay Đổi

- `SecurityConfig` — không có route mới
- `Repository`, `Service`, `Controller` cho Document — chỉ thêm fields, không thêm query mới
- `HistoricalContextDocumentService` — mapper đọc field mới tự động qua entity getter

---

## 6. DB Migration Note

`ddl-auto=update` — Hibernate tự thêm column mới vào bảng khi khởi động. Column mới đều `nullable = true` nên **không breaking** với data cũ.

---

## 7. Checklist Implementation

- [x] `EventEra.java` enum
- [x] `EventCategory.java` enum
- [x] `MessageRole.java` enum
- [x] `HistoricalContextDocument.java` — thêm `era`, `category`, `period`
- [x] `Message.java` — thêm `role`, cập nhật `@PrePersist`
- [x] `CreateHistoricalContextDocumentRequest.java` — thêm 3 fields
- [x] `UpdateHistoricalContextDocumentRequest.java` — thêm 3 fields
- [x] `HistoricalContextDocumentResponse.java` — thêm 3 fields
- [x] `HistoricalContextDocumentService.java` — map era/category/period trong create, update, mapToResponse
