# Soft Delete Implementation Plan – Dual-Delete Strategy (Hard + Soft)

**Date:** March 19, 2026  
**Status:** Planning Phase (Awaiting Review)  
**Strategy:** Coexistence of hard delete (original) and soft delete (new) via separate API endpoints

---

## Executive Summary

Currently, only **Quiz** and **Question** entities have soft delete implemented. This plan outlines:

1. **Add soft-delete field** (`deleted_at`) to all 9 remaining entities
2. **Database migration** to add columns + indexes
3. **Keep existing DELETE APIs** unchanged (hard delete behavior)
4. **Add new soft-delete APIs** (separate endpoints for soft deletion)
5. **Automatic filtering** via `@Where` clause (soft-deleted items hidden from all queries)

**Result:** Backward-compatible implementation with parallel hard + soft delete capability.

---

## Current Status

### ✅ Entities WITH Soft Delete (2/11)

| Entity       | Soft Delete | Field                       | Details                      |
| ------------ | :---------: | --------------------------- | ---------------------------- |
| **Quiz**     |   ✅ Yes    | `deleted_at: LocalDateTime` | Uses `@SQLDelete` + `@Where` |
| **Question** |   ✅ Yes    | `deleted_at: LocalDateTime` | Uses `@SQLDelete` + `@Where` |

### ❌ Entities WITHOUT Soft Delete (9/11)

| Entity                        | Module             | Priority | Reason                                                 |
| ----------------------------- | ------------------ | -------- | ------------------------------------------------------ |
| **Character**                 | Character          | HIGH     | Content entity; should preserve history                |
| **CharacterDocument**         | Character          | HIGH     | Child of Character; should cascade soft delete         |
| **HistoricalContext**         | Historical Context | HIGH     | Primary content entity; data loss risk                 |
| **HistoricalContextDocument** | Historical Context | HIGH     | Child of HistoricalContext; should cascade soft delete |
| **ChatSession**               | Chat               | MEDIUM   | User interaction data; aids analytics                  |
| **Message**                   | Chat               | MEDIUM   | Child of ChatSession; preserve conversation history    |
| **QuizResult**                | Quiz               | MEDIUM   | User interaction data; audit trail                     |
| **QuizAnswerDetail**          | Quiz               | MEDIUM   | Child of QuizResult; audit trail                       |
| **QuizSession**               | Quiz               | MEDIUM   | Session/attempt tracking; audit                        |
| **User**                      | User               | LOW      | Rare deletion; compliance consideration                |

---

## Core Design: Dual-Delete Strategy

### **Principle**

- **Hard Delete (Original):** DELETE endpoint performs physical removal from DB
- **Soft Delete (New):** PATCH endpoint marks record as deleted (preserves data)
- **Filtering (Automatic):** `@Where(clause = "deleted_at IS NULL")` hides soft-deleted from all queries
- **Backward Compatible:** Existing clients continue using DELETE; new clients can use PATCH

### **API Naming Convention**

```
Old (Hard Delete):
  DELETE /v1/entities/{id}
          → repository.deleteById(id)
          → Physical row removal

New (Soft Delete):
  PATCH /v1/entities/{id}/soft-delete
        → entity.setDeletedAt(NOW())
        → repository.save(entity)
        → Row marked; still in DB
```

---

## Implementation Strategy by Module

### **1. CHARACTER MODULE** (2 entities)

#### **1.1 Character Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE \"character\" SET deleted_at = NOW() WHERE character_id = ?")
  @Where(clause = "deleted_at IS NULL")

Notes:
  - Hard delete: @SQLDelete handles automatic conversion
  - Soft delete: Manual service method calls repository.save()
  - Query filtering: @Where applied to all queries automatically
  - Unique constraint (Character.name): Soft-deleted records still occupy space
    → Acceptable for rare deletions
```

#### **1.2 CharacterDocument Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE character_document SET deleted_at = NOW() WHERE doc_id = ?")
  @Where(clause = "deleted_at IS NULL")

Cascade Behavior:
  - Hard delete Character → @SQLDelete triggers → soft-deletes all CharacterDocuments
  - Soft delete Character → Service calls softDeleteChildren() → soft-deletes docs
```

#### **API Changes: Character Module**

```
EXISTING (Hard Delete - Unchanged):
  DELETE /v1/characters/{characterId}
    - Authorization: owner or admin
    - Behavior: Physical removal from DB
    - Response: 204 No Content

NEW (Soft Delete):
  PATCH /v1/characters/{characterId}/soft-delete
    - Authorization: owner or admin
    - Behavior: Set deleted_at = NOW()
    - Response: 200 OK + { message: "Character soft-deleted" }
    - Request body: (empty) or { reason?: "string" }

GET /v1/characters, GET /v1/characters/{id}
  - Unchanged: @Where automatically filters deleted_at IS NULL
  - Soft-deleted records return 404 (not found)

NEW (Optional Future):
  POST /v1/characters/{characterId}/restore (admin only)
    - Restore soft-deleted: SET deleted_at = NULL
    - For now: not implemented (Phase 2)
```

---

### **2. HISTORICAL CONTEXT MODULE** (2 entities)

#### **2.1 HistoricalContext Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE historical_context SET deleted_at = NOW() WHERE context_id = ?")
  @Where(clause = "deleted_at IS NULL")

Notes:
  - Hard delete cascades via @SQLDelete: HistoricalContext → Documents, Characters, Quizzes all hard-deleted
  - Soft delete: Manual service method sets deleted_at + cascades soft-delete to children
  - Unique constraint (HistoricalContext.name): Soft-deleted records occupy space temporarily
```

#### **2.2 HistoricalContextDocument Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE historical_context_document SET deleted_at = NOW() WHERE doc_id = ?")
  @Where(clause = "deleted_at IS NULL")

Cascade with Parent:
  - Hard delete parent → @SQLDelete also hard-deletes docs
  - Soft delete parent → Service calls softDeleteChildren() → soft-deletes docs
```

#### **API Changes: Historical Context Module**

```
EXISTING (Hard Delete - Unchanged):
  DELETE /v1/contexts/{contextId}
    - Authorization: owner or admin
    - Behavior: Physical removal + cascade to docs/characters/quizzes
    - Response: 204 No Content

  DELETE /v1/contexts/{contextId}/documents/{docId}
    - Authorization: owner or admin
    - Behavior: Physical removal
    - Response: 204 No Content

NEW (Soft Delete):
  PATCH /v1/contexts/{contextId}/soft-delete
    - Authorization: owner or admin
    - Behavior: Set deleted_at = NOW() + cascade soft-delete to children
    - Response: 200 OK + { message: "Context soft-deleted", deletedChildren: 5 }
    - Request body: (empty) or { reason?: "string" }

  PATCH /v1/contexts/{contextId}/documents/{docId}/soft-delete
    - Authorization: owner or admin
    - Behavior: Set deleted_at = NOW()
    - Response: 200 OK + { message: "Document soft-deleted" }

Query (Unchanged):
  GET /v1/contexts (list)
    - @Where automatically filters deleted_at IS NULL

  GET /v1/contexts/{contextId} (detail)
    - @Where filter → 404 if soft-deleted
```

---

### **3. CHAT MODULE** (2 entities)

#### **3.1 ChatSession Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE chat_session SET deleted_at = NOW() WHERE session_id = ?")
  @Where(clause = "deleted_at IS NULL")

Notes:
  - Hard delete: Cascades via @SQLDelete to all Messages
  - Soft delete: Manual service sets deleted_at + cascades to children
  - Filtering: @Where applied to findAllByUserUid(), findBySessionIdAndUserUid(), etc.
```

#### **3.2 Message Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE message SET deleted_at = NOW() WHERE message_id = ?")
  @Where(clause = "deleted_at IS NULL")

Notes:
  - Hard delete: Parent ChatSession hard-deleted also hard-deletes messages
  - Soft delete: Service sets deleted_at on individual messages
  - Data retention: suggestedQuestions preserved in soft-deleted rows
```

#### **API Changes: Chat Module**

```
EXISTING (Hard Delete - Unchanged):
  DELETE /v1/chat/sessions/{sessionId}
    - Authorization: owner only
    - Behavior: Physical removal + cascade delete all messages
    - Response: 204 No Content

NEW (Soft Delete):
  PATCH /v1/chat/sessions/{sessionId}/soft-delete
    - Authorization: owner only
    - Behavior: Set deleted_at = NOW() + cascade soft-delete messages
    - Response: 200 OK + { message: "Chat session soft-deleted", deletedMessages: 10 }
    - Request body: (empty)

Query (Unchanged):
  GET /v1/chat/sessions (list)
    - @Where filters: only active sessions shown

  GET /v1/chat/sessions/{sessionId}/messages (list)
    - @Where filters: only non-deleted messages shown

  GET /v1/chat/history (grouped)
    - @Where applied: deleted sessions hidden
```

---

### **4. QUIZ MODULE** (4 entities, 2 already partial)

#### **4.1 QuizResult Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE quiz_result SET deleted_at = NOW() WHERE result_id = ?")
  @Where(clause = "deleted_at IS NULL")

Notes:
  - Hard delete: Physical removal + cascade to QuizAnswerDetail
  - Soft delete: Manual service sets deleted_at + cascades
  - User history: Soft-deleted results hidden from user's result list
```

#### **4.2 QuizAnswerDetail Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE quiz_answer_detail SET deleted_at = NOW() WHERE detail_id = ?")
  @Where(clause = "deleted_at IS NULL")

Cascade with Parent:
  - Parent QuizResult hard-deleted → QuizAnswerDetail also hard-deleted
  - Parent QuizResult soft-deleted → Service cascades soft-delete
```

#### **4.3 QuizSession Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE quiz_session SET deleted_at = NOW() WHERE session_id = ?")
  @Where(clause = "deleted_at IS NULL")

Notes:
  - Hard delete: Physical removal
  - Soft delete: Mark abandoned/cancelled attempts
  - State: Can be expired (expiresAt < NOW()) AND/OR soft-deleted (deleted_at IS NOT NULL)
```

#### **API Changes: Quiz Module**

**Quiz (already soft-delete implemented):**

```
EXISTING (Hard Delete):
  DELETE /v1/quizzes/{quizId}
    - Might already soft-delete; verify in Quiz controller

NEW (Soft Delete - if not already):
  PATCH /v1/quizzes/{quizId}/soft-delete
    - Behavior: Set deleted_at = NOW() + cascade to results/sessions
    - Response: 200 OK + { message: "Quiz soft-deleted", deletedResults: 3 }
```

**Question (already soft-delete implemented):**

```
Similar approach: verify existing behavior; add PATCH endpoint if needed
```

**QuizResult (NEW):**

```
EXISTING (Hard Delete):
  DELETE /v1/quiz-results/{resultId} (if exists)
    - Physical removal

NEW (Soft Delete):
  PATCH /v1/quiz-results/{resultId}/soft-delete
    - Behavior: Set deleted_at = NOW() + cascade to answer details
    - Response: 200 OK + { message: "Quiz result soft-deleted" }
```

**QuizSession (NEW):**

```
EXISTING (Hard Delete):
  DELETE /v1/quiz-sessions/{sessionId} (if exists)
    - Physical removal

NEW (Soft Delete):
  PATCH /v1/quiz-sessions/{sessionId}/soft-delete
    - Behavior: Set deleted_at = NOW() (mark time-out/abandon)
    - Response: 200 OK + { message: "Quiz session soft-deleted" }
```

**Query Endpoints (All Unchanged):**

```
GET /v1/quiz-results
  - @Where filters: only non-deleted results shown

GET /v1/quiz-sessions
  - @Where filters: only non-deleted sessions shown
```

---

### **5. USER ENTITY** (1 entity)

#### **5.1 User Entity**

```
Add Fields:
  + deleted_at: LocalDateTime (nullable)

Add Annotations:
  @SQLDelete(sql = "UPDATE \"user\" SET deleted_at = NOW() WHERE uid = ?")
  @Where(clause = "deleted_at IS NULL")

Critical Notes:
  ✅ Hard delete: User row physically removed (also via @SQLDelete)
  ✅ Soft delete: User row marked with deleted_at; preserved for audit
  ⚠️  Email/userName unique constraints: Soft-deleted records occupy space
      → After soft delete, same email cannot be reused (unless 90-day purge)
      → Acceptable for rare user deletion
```

#### **API Changes: User Module**

**Account Deactivation (Soft Delete):**

```
NEW (Soft Delete):
  PATCH /v1/auth/me/deactivate
    - Authorization: authenticated (self) or admin
    - Behavior: Set user.deleted_at = NOW()
    - Cascade: Soft-delete all user's owned content:
      ✓ HistoricalContexts (+ documents + characters + quizzes)
      ✓ Characters (+ documents + chat sessions)
      ✓ ChatSessions (+ messages)
      ✓ QuizResults (+ answer details)
      ✓ QuizSessions
    - Response: 200 OK + { message: "Account deactivated", message: "All your content archived" }
    - Request body: (empty) or { reason?: "string" }

Behavior:
  - User cannot login after deactivation (CustomUserDetailsService rejects)
  - All user's content hidden from other users via @Where filters
  - 90-day retention: content recoverable if user reactivates early
  - Data kept for audit/compliance purposes
```

**Account Deletion (Hard Delete - Admin Only):**

```
EXISTING/NEW (Hard Delete):
  DELETE /v1/users/{userId} (admin only)
    - Authorization: admin
    - Behavior: Physical removal of user + cascade hard-delete all content
    - Response: 204 No Content
    - Precondition: User already soft-deleted (delete within 90 days) or immediate deletion

  DELETE /v1/auth/me (self-delete - hard)
    - Authorization: authenticated (self only)
    - Behavior: Physical removal of user + cascade hard-delete all content
    - Response: 204 No Content
    - Warning: Irreversible; all data permanently lost
    - Request: { password: "confirm-password" } // Prevent accidents
```

**Authentication Impact:**

```
LOGIN - Deactivated User:
  POST /v1/auth/login
    - Input: email, password
    - Check: CustomUserDetailsService.loadUserByUsername()
      if (user != null && user.getDeletedAt() != null) {
          throw new UnauthorizedException("Account deactivated");
      }
    - Response: 401 Unauthorized + { message: "Account deactivated or not found" }

REFRESH - Deactivated User:
  POST /v1/auth/refresh-token
    - JWT validation extracts user ID
    - Repository query respects @Where (deleted_at IS NULL)
    - User not found → 401 Unauthorized
```

**Query Endpoints (All Unchanged):**

```
GET /v1/users/{userId}
  - @Where filter: 404 if soft-deleted

GET /v1/auth/me
  - @Where filter: 404 if soft-deleted (user deactivated)
```

---

## Database Migration Strategy

### Migration V5: Add Soft Delete Columns

**File:** `src/main/resources/db/migration/V5__add_soft_delete_columns.sql`

```sql
-- Add deleted_at columns to all content entities
ALTER TABLE "character" ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE character_document ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE historical_context ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE historical_context_document ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE chat_session ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE message ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE quiz_result ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE quiz_answer_detail ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE quiz_session ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE "user" ADD COLUMN deleted_at TIMESTAMP NULL;

-- Index deleted_at for query performance
CREATE INDEX idx_character_deleted_at ON "character"(deleted_at);
CREATE INDEX idx_character_doc_deleted_at ON character_document(deleted_at);
CREATE INDEX idx_context_deleted_at ON historical_context(deleted_at);
CREATE INDEX idx_context_doc_deleted_at ON historical_context_document(deleted_at);
CREATE INDEX idx_chat_session_deleted_at ON chat_session(deleted_at);
CREATE INDEX idx_message_deleted_at ON message(deleted_at);
CREATE INDEX idx_quiz_result_deleted_at ON quiz_result(deleted_at);
CREATE INDEX idx_quiz_answer_detail_deleted_at ON quiz_answer_detail(deleted_at);
CREATE INDEX idx_quiz_session_deleted_at ON quiz_session(deleted_at);
CREATE INDEX idx_user_deleted_at ON "user"(deleted_at);
```

---

## Sample Data Migration (Flyway)

### Migration V7: Seed Sample Data

**File:** `src/main/resources/db/migration/V7__seed_sample_data.sql`

**Mục tiêu:** Tạo sample data đầy đủ cho tất cả 11 entity, phục vụ testing soft-delete flow trên môi trường dev/staging. Sử dụng fixed UUID để dễ dàng reference FK giữa các bảng.

**Thứ tự INSERT (theo dependency):**

```
1. User (không phụ thuộc)
2. HistoricalContext (FK → User)
3. HistoricalContextDocument (FK → HistoricalContext, User)
4. Character (FK → User) + character_historical_context (join table)
5. CharacterDocument (FK → Character, User)
6. Quiz (FK → HistoricalContext, User)
7. Question (FK → Quiz)
8. ChatSession (FK → User, Character, HistoricalContext)
9. Message (FK → ChatSession)
10. QuizResult (FK → User, Quiz)
11. QuizAnswerDetail (FK → QuizResult, Question)
12. QuizSession (FK → Quiz, User)
```

```sql
-- ============================================================
-- V7__seed_sample_data.sql
-- Sample data cho testing soft-delete (dual-delete strategy)
-- ============================================================

-- ============================================================
-- 1. USERS (3 users: ADMIN, STAFF, CUSTOMER)
-- ============================================================
-- Password hash = BCrypt của '123456789'
INSERT INTO "user" (uid, email, password, role, user_name)
VALUES
    ('a0000000-0000-0000-0000-000000000001'::uuid,
     'admin@historytalk.com',
     '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy',
     'ADMIN', 'ADMIN1'),

    ('a0000000-0000-0000-0000-000000000002'::uuid,
     'staff@historytalk.com',
     '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy',
     'STAFF', 'STAFF_DEMO'),

    ('a0000000-0000-0000-0000-000000000003'::uuid,
     'demo@historytalk.com',
     '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy',
     'CUSTOMER', 'CUSTOMER_DEMO')
ON CONFLICT (email) DO NOTHING;

-- ============================================================
-- 2. HISTORICAL CONTEXTS (3 contexts)
-- ============================================================
INSERT INTO historical_context
    (context_id, name, description, era, category, year, start_year, end_year,
     before_tcn, location, image_url, video_url, created_by, created_date, updated_date)
VALUES
    -- Nhà Trần & Kháng chiến chống Nguyên Mông
    ('b0000000-0000-0000-0000-000000000001'::uuid,
     'Kháng chiến chống quân Nguyên Mông',
     'Ba lần kháng chiến chống quân Nguyên Mông (1258, 1285, 1288) dưới triều đại nhà Trần là một trong những trang sử hào hùng nhất của dân tộc Việt Nam.',
     'MEDIEVAL', 'WAR', 1258, 1258, 1288, false,
     'Đại Việt', NULL, NULL,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NOW()),

    -- Chiến thắng Điện Biên Phủ
    ('b0000000-0000-0000-0000-000000000002'::uuid,
     'Chiến thắng Điện Biên Phủ 1954',
     'Chiến dịch Điện Biên Phủ (13/3 – 7/5/1954) là trận đánh quyết định kết thúc cuộc kháng chiến chống thực dân Pháp, mở ra kỷ nguyên mới cho dân tộc Việt Nam.',
     'CONTEMPORARY', 'WAR', 1954, 1954, 1954, false,
     'Điện Biên Phủ, Tây Bắc Việt Nam', NULL, NULL,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NOW()),

    -- Hai Bà Trưng
    ('b0000000-0000-0000-0000-000000000003'::uuid,
     'Khởi nghĩa Hai Bà Trưng',
     'Cuộc khởi nghĩa của Hai Bà Trưng (năm 40) là cuộc khởi nghĩa đầu tiên trong lịch sử Việt Nam chống lại ách đô hộ của nhà Hán, giành lại độc lập trong 3 năm.',
     'ANCIENT', 'WAR', 40, 40, 43, false,
     'Mê Linh, Giao Chỉ', NULL, NULL,
     'a0000000-0000-0000-0000-000000000001'::uuid,
     NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 3. HISTORICAL CONTEXT DOCUMENTS (3 docs)
-- ============================================================
INSERT INTO historical_context_document
    (doc_id, title, content, context_id, created_by, upload_date, updated_date)
VALUES
    ('c0000000-0000-0000-0000-000000000001'::uuid,
     'Diễn biến trận Bạch Đằng 1288',
     'Tháng 4 năm 1288, Hưng Đạo Vương Trần Quốc Tuấn đã bố trí trận địa cọc ngầm trên sông Bạch Đằng, đánh tan đoàn thuyền chiến của Ô Mã Nhi...',
     'b0000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NOW()),

    ('c0000000-0000-0000-0000-000000000002'::uuid,
     'Chiến dịch Điện Biên Phủ – 56 ngày đêm',
     'Chiến dịch diễn ra trong 56 ngày đêm, chia làm 3 đợt tấn công. Đợt 1 (13-17/3): tiêu diệt cứ điểm Him Lam và Độc Lập...',
     'b0000000-0000-0000-0000-000000000002'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NOW()),

    ('c0000000-0000-0000-0000-000000000003'::uuid,
     'Hai Bà Trưng – Nữ vương đầu tiên',
     'Trưng Trắc và Trưng Nhị là hai chị em, con gái Lạc tướng huyện Mê Linh. Năm 40, hai bà phất cờ khởi nghĩa tại cửa sông Hát...',
     'b0000000-0000-0000-0000-000000000003'::uuid,
     'a0000000-0000-0000-0000-000000000001'::uuid,
     NOW(), NOW())
ON CONFLICT (doc_id) DO NOTHING;

-- ============================================================
-- 4. CHARACTERS (4 nhân vật lịch sử)
-- ============================================================
INSERT INTO "character"
    (character_id, name, title, background, image, personality, lifespan, side, created_by)
VALUES
    ('d0000000-0000-0000-0000-000000000001'::uuid,
     'Trần Hưng Đạo',
     'Quốc công Tiết chế',
     'Trần Quốc Tuấn (1228–1300), tước Hưng Đạo Vương, là vị tướng tài ba đã ba lần đánh bại quân Nguyên-Mông xâm lược Đại Việt.',
     NULL, 'Cương nghị, mưu lược, yêu nước, khoan dung', '1228–1300', 'Nhà Trần',
     'a0000000-0000-0000-0000-000000000002'::uuid),

    ('d0000000-0000-0000-0000-000000000002'::uuid,
     'Trần Quốc Toản',
     'Hoài Văn Hầu',
     'Trần Quốc Toản (1267–1285) là một thiếu niên anh hùng trong cuộc kháng chiến chống Nguyên lần thứ hai, nổi tiếng với lá cờ "Phá cường địch, báo hoàng ân".',
     NULL, 'Dũng cảm, nhiệt huyết, trung thành', '1267–1285', 'Nhà Trần',
     'a0000000-0000-0000-0000-000000000002'::uuid),

    ('d0000000-0000-0000-0000-000000000003'::uuid,
     'Võ Nguyên Giáp',
     'Đại tướng',
     'Đại tướng Võ Nguyên Giáp (1911–2013) là vị tướng huyền thoại, Tổng tư lệnh Quân đội Nhân dân Việt Nam, chỉ huy chiến thắng Điện Biên Phủ 1954.',
     NULL, 'Thông minh, bình tĩnh, quyết đoán, nhân hậu', '1911–2013', 'Việt Nam Dân chủ Cộng hòa',
     'a0000000-0000-0000-0000-000000000002'::uuid),

    ('d0000000-0000-0000-0000-000000000004'::uuid,
     'Trưng Trắc',
     'Trưng Nữ Vương',
     'Trưng Trắc (?–43) là nữ vương đầu tiên trong lịch sử Việt Nam, lãnh đạo cuộc khởi nghĩa chống nhà Hán năm 40, xưng vương và đóng đô tại Mê Linh.',
     NULL, 'Kiên cường, quả cảm, yêu nước, trọng nghĩa', '? – 43', 'Lạc Việt',
     'a0000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (character_id) DO NOTHING;

-- Liên kết Character ↔ HistoricalContext (many-to-many)
INSERT INTO character_historical_context (character_id, context_id)
VALUES
    ('d0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000001'::uuid),
    ('d0000000-0000-0000-0000-000000000002'::uuid, 'b0000000-0000-0000-0000-000000000001'::uuid),
    ('d0000000-0000-0000-0000-000000000003'::uuid, 'b0000000-0000-0000-0000-000000000002'::uuid),
    ('d0000000-0000-0000-0000-000000000004'::uuid, 'b0000000-0000-0000-0000-000000000003'::uuid)
ON CONFLICT DO NOTHING;

-- ============================================================
-- 5. CHARACTER DOCUMENTS (2 docs)
-- ============================================================
INSERT INTO character_document
    (doc_id, title, content, character_id, created_by, upload_date)
VALUES
    ('e0000000-0000-0000-0000-000000000001'::uuid,
     'Hịch tướng sĩ – Trần Hưng Đạo',
     'Ta thường tới bữa quên ăn, nửa đêm vỗ gối; ruột đau như cắt, nước mắt đầm đìa; chỉ căm tức chưa xả thịt lột da, nuốt gan uống máu quân thù...',
     'd0000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW()),

    ('e0000000-0000-0000-0000-000000000002'::uuid,
     'Tiểu sử Đại tướng Võ Nguyên Giáp',
     'Võ Nguyên Giáp sinh ngày 25 tháng 8 năm 1911 tại làng An Xá, huyện Lệ Thủy, tỉnh Quảng Bình. Ông là một trong những vị tướng lỗi lạc nhất thế kỷ 20...',
     'd0000000-0000-0000-0000-000000000003'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid,
     NOW())
ON CONFLICT (doc_id) DO NOTHING;

-- ============================================================
-- 6. QUIZZES (2 quizzes)
-- ============================================================
INSERT INTO quiz
    (quiz_id, title, description, grade, chapter_number, chapter_title,
     era, duration_seconds, play_count, rating, context_id, created_by)
VALUES
    ('f0000000-0000-0000-0000-000000000001'::uuid,
     'Trắc nghiệm: Kháng chiến chống Nguyên Mông',
     'Kiểm tra kiến thức về ba lần kháng chiến chống quân Nguyên Mông của nhà Trần.',
     7, 1, 'Nhà Trần và cuộc kháng chiến chống Nguyên',
     'MEDIEVAL', 600, 0, 0.0,
     'b0000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid),

    ('f0000000-0000-0000-0000-000000000002'::uuid,
     'Trắc nghiệm: Chiến thắng Điện Biên Phủ',
     'Tìm hiểu về chiến dịch lịch sử Điện Biên Phủ năm 1954.',
     9, 5, 'Kháng chiến chống Pháp',
     'CONTEMPORARY', 900, 0, 0.0,
     'b0000000-0000-0000-0000-000000000002'::uuid,
     'a0000000-0000-0000-0000-000000000002'::uuid)
ON CONFLICT (quiz_id) DO NOTHING;

-- ============================================================
-- 7. QUESTIONS (4 câu hỏi, mỗi quiz 2 câu)
-- ============================================================
INSERT INTO question
    (question_id, content, options, correct_answer, order_index, explanation, quiz_id)
VALUES
    -- Quiz 1: Kháng chiến chống Nguyên Mông
    ('f1000000-0000-0000-0000-000000000001'::uuid,
     'Ai là Tổng chỉ huy quân đội nhà Trần trong cuộc kháng chiến chống Nguyên Mông lần thứ 2 và 3?',
     '["Trần Thái Tông","Trần Hưng Đạo","Trần Nhân Tông","Trần Quốc Toản"]',
     1, 1,
     'Trần Hưng Đạo (Trần Quốc Tuấn) được phong làm Quốc công Tiết chế, tổng chỉ huy quân đội.',
     'f0000000-0000-0000-0000-000000000001'::uuid),

    ('f1000000-0000-0000-0000-000000000002'::uuid,
     'Trận Bạch Đằng năm 1288 sử dụng chiến thuật gì nổi tiếng?',
     '["Phục kích trên bộ","Cọc ngầm trên sông","Hỏa công","Vây thành"]',
     1, 2,
     'Trần Hưng Đạo đã cho đóng cọc nhọn bịt sắt xuống lòng sông Bạch Đằng, lợi dụng thủy triều để tiêu diệt đoàn thuyền địch.',
     'f0000000-0000-0000-0000-000000000001'::uuid),

    -- Quiz 2: Chiến thắng ĐBP
    ('f1000000-0000-0000-0000-000000000003'::uuid,
     'Chiến dịch Điện Biên Phủ diễn ra trong bao nhiêu ngày đêm?',
     '["45 ngày đêm","56 ngày đêm","60 ngày đêm","75 ngày đêm"]',
     1, 1,
     'Chiến dịch Điện Biên Phủ kéo dài 56 ngày đêm, từ 13/3 đến 7/5/1954.',
     'f0000000-0000-0000-0000-000000000002'::uuid),

    ('f1000000-0000-0000-0000-000000000004'::uuid,
     'Ai là Tổng tư lệnh chiến dịch Điện Biên Phủ phía Việt Nam?',
     '["Hồ Chí Minh","Phạm Văn Đồng","Võ Nguyên Giáp","Nguyễn Chí Thanh"]',
     2, 2,
     'Đại tướng Võ Nguyên Giáp là Tổng tư lệnh trực tiếp chỉ huy chiến dịch Điện Biên Phủ.',
     'f0000000-0000-0000-0000-000000000002'::uuid)
ON CONFLICT (question_id) DO NOTHING;

-- ============================================================
-- 8. CHAT SESSIONS (2 sessions)
-- ============================================================
INSERT INTO chat_session
    (session_id, uid, character_id, context_id, title, last_message_at, create_date)
VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     'd0000000-0000-0000-0000-000000000001'::uuid,
     'b0000000-0000-0000-0000-000000000001'::uuid,
     'Trò chuyện với Trần Hưng Đạo',
     NOW(), NOW()),

    ('10000000-0000-0000-0000-000000000002'::uuid,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     'd0000000-0000-0000-0000-000000000003'::uuid,
     'b0000000-0000-0000-0000-000000000002'::uuid,
     'Trò chuyện với Đại tướng Võ Nguyên Giáp',
     NOW(), NOW())
ON CONFLICT (session_id) DO NOTHING;

-- ============================================================
-- 9. MESSAGES (4 messages, mỗi session 2 tin)
-- ============================================================
INSERT INTO message
    (message_id, content, is_from_ai, role, session_id, "timestamp", suggested_questions)
VALUES
    -- Session 1: User hỏi Trần Hưng Đạo
    ('11000000-0000-0000-0000-000000000001'::uuid,
     'Chào Hưng Đạo Vương, ngài có thể kể về trận Bạch Đằng không ạ?',
     false, 'USER',
     '10000000-0000-0000-0000-000000000001'::uuid,
     NOW(), NULL),

    ('11000000-0000-0000-0000-000000000002'::uuid,
     'Ta rất vui khi hậu thế quan tâm đến lịch sử dân tộc! Trận Bạch Đằng năm 1288 là trận đánh quyết định trong cuộc kháng chiến lần thứ ba chống quân Nguyên...',
     true, 'ASSISTANT',
     '10000000-0000-0000-0000-000000000001'::uuid,
     NOW(),
     '["Chiến thuật cọc ngầm được chuẩn bị như thế nào?","Quân Nguyên có bao nhiêu thuyền chiến?","Sau trận Bạch Đằng, nhà Nguyên có xâm lược lại không?"]'),

    -- Session 2: User hỏi Võ Nguyên Giáp
    ('11000000-0000-0000-0000-000000000003'::uuid,
     'Thưa Đại tướng, quyết định chuyển từ "đánh nhanh thắng nhanh" sang "đánh chắc tiến chắc" được đưa ra như thế nào?',
     false, 'USER',
     '10000000-0000-0000-0000-000000000002'::uuid,
     NOW(), NULL),

    ('11000000-0000-0000-0000-000000000004'::uuid,
     'Đó là quyết định khó khăn nhất trong đời chỉ huy của tôi. Sau khi phân tích kỹ tình hình, tôi nhận thấy phương án "đánh nhanh thắng nhanh" tiềm ẩn nhiều rủi ro...',
     true, 'ASSISTANT',
     '10000000-0000-0000-0000-000000000002'::uuid,
     NOW(),
     '["Quân Pháp phòng thủ Điện Biên Phủ mạnh cỡ nào?","Vai trò của dân công trong chiến dịch?","Cảm xúc của Đại tướng khi chiến thắng?"]')
ON CONFLICT (message_id) DO NOTHING;

-- ============================================================
-- 10. QUIZ RESULTS (2 results: Customer làm 2 quiz)
-- ============================================================
INSERT INTO quiz_result
    (result_id, score, duration_seconds, uid, quiz_id, taken_date)
VALUES
    ('12000000-0000-0000-0000-000000000001'::uuid,
     2, 180,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     'f0000000-0000-0000-0000-000000000001'::uuid,
     NOW()),

    ('12000000-0000-0000-0000-000000000002'::uuid,
     1, 420,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     'f0000000-0000-0000-0000-000000000002'::uuid,
     NOW())
ON CONFLICT (result_id) DO NOTHING;

-- ============================================================
-- 11. QUIZ ANSWER DETAILS (4 answers)
-- ============================================================
INSERT INTO quiz_answer_detail
    (detail_id, selected_option, is_correct, result_id, question_id)
VALUES
    -- Result 1: Quiz Nguyên Mông – 2/2 đúng
    ('13000000-0000-0000-0000-000000000001'::uuid,
     'Trần Hưng Đạo', true,
     '12000000-0000-0000-0000-000000000001'::uuid,
     'f1000000-0000-0000-0000-000000000001'::uuid),

    ('13000000-0000-0000-0000-000000000002'::uuid,
     'Cọc ngầm trên sông', true,
     '12000000-0000-0000-0000-000000000001'::uuid,
     'f1000000-0000-0000-0000-000000000002'::uuid),

    -- Result 2: Quiz ĐBP – 1/2 đúng
    ('13000000-0000-0000-0000-000000000003'::uuid,
     '56 ngày đêm', true,
     '12000000-0000-0000-0000-000000000002'::uuid,
     'f1000000-0000-0000-0000-000000000003'::uuid),

    ('13000000-0000-0000-0000-000000000004'::uuid,
     'Hồ Chí Minh', false,
     '12000000-0000-0000-0000-000000000002'::uuid,
     'f1000000-0000-0000-0000-000000000004'::uuid)
ON CONFLICT (detail_id) DO NOTHING;

-- ============================================================
-- 12. QUIZ SESSIONS (2 sessions: 1 submitted, 1 in-progress)
-- ============================================================
INSERT INTO quiz_session
    (session_id, quiz_id, uid, started_at, expires_at, is_submitted)
VALUES
    -- Session đã submit
    ('14000000-0000-0000-0000-000000000001'::uuid,
     'f0000000-0000-0000-0000-000000000001'::uuid,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     NOW() - INTERVAL '1 hour', NOW() - INTERVAL '50 minutes', true),

    -- Session đang làm (chưa hết hạn)
    ('14000000-0000-0000-0000-000000000002'::uuid,
     'f0000000-0000-0000-0000-000000000002'::uuid,
     'a0000000-0000-0000-0000-000000000003'::uuid,
     NOW(), NOW() + INTERVAL '15 minutes', false)
ON CONFLICT (session_id) DO NOTHING;
```

### Sample Data Summary

| Entity                        | Số bản ghi | Mô tả                                                         |
| ----------------------------- | :--------: | -------------------------------------------------------------- |
| **User**                      |     3      | ADMIN, STAFF, CUSTOMER (password: `123456789`)                 |
| **HistoricalContext**         |     3      | Nguyên Mông, Điện Biên Phủ, Hai Bà Trưng                      |
| **HistoricalContextDocument** |     3      | Mỗi context 1 tài liệu chi tiết                               |
| **Character**                 |     4      | Trần Hưng Đạo, Trần Quốc Toản, Võ Nguyên Giáp, Trưng Trắc    |
| **CharacterDocument**         |     2      | Hịch tướng sĩ, Tiểu sử Võ Nguyên Giáp                        |
| **Quiz**                      |     2      | Nguyên Mông quiz, ĐBP quiz                                    |
| **Question**                  |     4      | Mỗi quiz 2 câu hỏi trắc nghiệm                               |
| **ChatSession**               |     2      | Customer chat với Trần Hưng Đạo & Võ Nguyên Giáp              |
| **Message**                   |     4      | Mỗi session 1 user message + 1 AI response                    |
| **QuizResult**                |     2      | Customer làm 2 quiz (score: 2/2 và 1/2)                       |
| **QuizAnswerDetail**          |     4      | Chi tiết từng câu trả lời                                     |
| **QuizSession**               |     2      | 1 đã submit, 1 đang làm                                       |
| **Tổng**                      |   **29**   | Đủ data cho testing soft-delete, cascade, @Where filter        |

### Soft-Delete Test Scenarios với Sample Data

```
Scenario 1: Soft-delete Character "Trần Hưng Đạo"
  → CharacterDocuments: 1 doc soft-deleted (cascade)
  → ChatSessions: 1 session soft-deleted (cascade)
  → Messages: 2 messages soft-deleted (cascade)
  → Verify: GET /v1/characters → 3 characters (not 4)

Scenario 2: Soft-delete HistoricalContext "Kháng chiến chống Nguyên Mông"
  → HistoricalContextDocuments: 1 doc soft-deleted
  → Characters: 2 characters detached (many-to-many)
  → Quizzes: 1 quiz soft-deleted + 2 questions + 1 result + 2 answers
  → Verify: GET /v1/contexts → 2 contexts (not 3)

Scenario 3: Soft-delete User "CUSTOMER_DEMO"
  → ChatSessions: 2 sessions + 4 messages soft-deleted
  → QuizResults: 2 results + 4 answer details soft-deleted
  → QuizSessions: 2 sessions soft-deleted
  → Verify: Login demo@historytalk.com → 401 Unauthorized

Scenario 4: Hard-delete (DELETE endpoint) sau soft-delete
  → Verify: row physically removed from DB
  → Verify: cascade hard-delete children cũng bị xóa vật lý
```

### Lưu ý khi chạy Migration

```
⚠️  Thứ tự migration:
  V5 → Add soft-delete columns (phải chạy trước)
  V6 → Add soft-delete indexes
  V7 → Seed sample data (chạy sau cùng)

⚠️  Idempotent:
  Tất cả INSERT đều dùng ON CONFLICT ... DO NOTHING
  → Có thể chạy lại nhiều lần mà không bị lỗi duplicate

⚠️  Fixed UUID:
  Sử dụng UUID cố định (a0..., b0..., d0..., f0...)
  → Dễ debug, dễ reference trong test scripts
  → KHÔNG dùng gen_random_uuid() để đảm bảo FK tham chiếu chính xác
```

---

## Implementation Phases

### **Phase 1: Add Soft-Delete Field & Annotations (All Entities)**

**Timeline:** 1-2 days  
**Entities:** All 9 entities (Character, CharacterDocument, HistoricalContext, HistoricalContextDocument, ChatSession, Message, QuizResult, QuizAnswerDetail, QuizSession, User)

**Steps:**

1. Add `deleted_at: LocalDateTime` field to each entity
2. Add `@SQLDelete` + `@Where` annotations to each entity class
3. Create database migration V5 (add columns + indexes)
4. Run migration on dev/staging environment
5. **No API changes yet** → Only foundation layer

**Code Changes:**

- Entity files only: 10 files to modify
- Add same boilerplate to each: field + 2 annotations
- Database: 10 new columns + 12 new indexes

### **Phase 2: Add Soft-Delete Service Methods (Per Module)**

**Timeline:** 2-3 days  
**Scope:** 5 service classes (CharacterService, HistoricalContextService, ChatSessionService, QuizResultService, AuthService)

**Steps per module:**

1. Add `softDelete(UUID id)` method to service
2. Implement cascade logic for parent-child relationships
3. Authorization checks (owner or admin)
4. Manual `entity.setDeletedAt(NOW()); repository.save(entity);`
5. Test soft-delete method (not API yet)

**Example Method:**

```java
@Transactional
public void softDelete(UUID id, String userId, String role) {
    Character entity = repository.findById(id)
        .orElseThrow(ResourceNotFoundException::new);

    // Authorization
    if (!isOwnerOrAdmin(entity.getCreatedBy().getUid(), userId, role)) {
        throw new UnauthorizedException();
    }

    // Set deleted_at
    entity.setDeletedAt(LocalDateTime.now());
    repository.save(entity);

    // Cascade soft-delete children
    entity.getDocuments().forEach(doc -> {
        doc.setDeletedAt(LocalDateTime.now());
        docRepository.save(doc);
    });
}
```

### **Phase 3: Add PATCH Soft-Delete Endpoints**

**Timeline:** 2-3 days  
**Scope:** 5 controller classes

**Steps per controller:**

1. Add `@PatchMapping("/{id}/soft-delete")` endpoint
2. Call `service.softDelete(id)`
3. Return `200 OK + ApiResponse.success(null, "Entity soft-deleted")`
4. OLD DELETE endpoint unchanged (hard delete behavior)
5. E2E test: soft-delete + verify data in DB + @Where filters

**Example Endpoint:**

```java
@PatchMapping("/{id}/soft-delete")
public ResponseEntity<ApiResponse<?>> softDelete(
    @PathVariable String id,
    @RequestHeader(value = "Authorization") String auth
) {
    String userId = SecurityUtils.getUserId();
    String role = SecurityUtils.getRoleName();
    characterService.softDelete(UUID.fromString(id), userId, role);
    return ResponseEntity.ok(ApiResponse.success(null, "Character soft-deleted"));
}
```

### **Phase 4: Cascade Soft-Delete Logic for Child Entities**

**Timeline:** 1-2 days  
**Scope:** Service methods managing parent-child cascade

**Steps:**

1. When parent soft-deleted → auto soft-delete all children via @Where (automatic)
2. OR → Manually cascade in service method (for complex scenarios)
3. Test cascade: delete parent → verify all children soft-deleted
4. Verify @Where filter hides all in queries

**Cascade Map:**

```
Character (soft-deleted)
  ↑ auto-cascade soft-delete via @Where
  L→ CharacterDocuments
  L→ ChatSessions (has @Where)

HistoricalContext (soft-deleted)
  ↑ auto-cascade soft-delete via @Where
  L→ HistoricalContextDocuments
  L→ Characters (+ their docs)
  L→ Quizzes (+ results + sessions)

User (soft-deleted)
  ↑ MANUAL cascade in AuthService
  L→ HistoricalContexts (+ all descendants)
  L→ Characters (+ docs + chat sessions)
  L→ ChatSessions (+ messages)
  L→ QuizResults (+ answer details)
  L→ QuizSessions
```

### **Phase 5: Update Authentication Flow (User Soft-Delete)**

**Timeline:** 0.5 days  
**Scope:** CustomUserDetailsService, AuthServiceImpl

**Steps:**

1. Update `CustomUserDetailsService.loadUserByUsername()`:
   - Check `user.getDeletedAt() != null`
   - Throw `UnauthorizedException("Account deactivated")`
2. Update `AuthServiceImpl.login()` similarly
3. Update JWT validation: if user soft-deleted → deny token refresh
4. Test: soft-delete user → login returns 401 → message clear

**Code Change:**

```java
@Override
public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = userRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    if (user.getDeletedAt() != null) {
        throw new UnauthorizedException("Account deactivated or not found");
    }

    return new UserPrincipal(user);
}
```

---

## Testing Strategy

### Unit Tests (Per Entity)

```
✓ Entity has deleted_at field with LocalDateTime type
✓ @SQLDelete annotation applied correctly
✓ @Where annotation filters deleted_at IS NULL
✓ Serialization/deserialization includes deleted_at
```

### Integration Tests (Per Module)

```
Hard Delete Path:
  ✓ DELETE /v1/entities/{id} → row physically removed
  ✓ GET /v1/entities/{id} → 404 (row gone)
  ✓ Database query: row doesn't exist

Soft Delete Path:
  ✓ PATCH /v1/entities/{id}/soft-delete → deleted_at SET to NOW()
  ✓ Row still exists in DB with deleted_at timestamp
  ✓ GET /v1/entities/{id} → 404 (via @Where filter)
  ✓ GET /v1/entities → list excludes soft-deleted (via @Where)

Cascade Soft Delete:
  ✓ Soft-delete parent → verify all children soft-deleted
  ✓ Hard-delete parent → verify all children hard-deleted
  ✓ Soft-delete child → parent unchanged

Query Filtering:
  ✓ LIST endpoint pagination excludes soft-deleted items
  ✓ totalElements count = active items only
  ✓ Search queries respect @Where filter
```

### E2E Tests (Critical Paths)

```
Character Flow:
  1. Create character → soft-delete → verify hidden from lists
  2. Hard-delete character → verify row removed from DB
  3. Soft-delete with documents → verify docs also soft-deleted
  4. Pagination: 100 characters, soft-delete 10 → verify count = 90

HistoricalContext Flow:
  1. Create context with 5 children → soft-delete → all hidden
  2. Hard-delete context → cascade removes all children
  3. Soft-delete then hard-delete (timeline: days 1→3)

User Flow (High Risk):
  1. User soft-deletes account (PATCH /v1/auth/me/deactivate)
     → All owned content soft-deleted
     → User cannot login
  2. Login attempt after deactivation → 401 Unauthorized
  3. Hard-delete user (admin) → cascade removes all content
  4. Verify user_id references respect soft-deleted state

Chat Flow:
  1. Chat soft-delete → all messages also soft-deleted
  2. Message hard-delete → parent unchanged
  3. List chat history → excludes soft-deleted sessions
  4. Pagination correctness with large deletion count

Query Performance:
  ✓ 1,000,000 active records + 100,000 soft-deleted
  ✓ Query time with index on deleted_at: < 100ms
  ✓ Verify index scan used (EXPLAIN ANALYZE)
```

---

## Backward Compatibility

### API Coexistence Strategy

```
OLD (Hard Delete):
  DELETE /v1/entities/{id}
  - Existing clients continue unchanged
  - Physical removal from DB
  - Response: 204 No Content

NEW (Soft Delete):
  PATCH /v1/entities/{id}/soft-delete
  - New clients use this endpoint
  - Data preserved; marked as deleted
  - Response: 200 OK + { message: string }

Result:
  ✅ No breaking changes
  ✅ Both deletion methods available
  ✅ Migration path for clients: DELETE → PATCH over time
```

---

## Unique Constraint Handling

### Problem: Soft-Delete + Unique Constraints

**Entities Affected:**

- Character.name (unique)
- HistoricalContext.name (unique)
- User.email (unique)
- User.userName (unique)

**Issue:**

- Hard delete → name/email released; can be reused
- Soft delete → name/email still "occupied" in unique index
- Attempting to create new Character with same name → UNIQUE CONSTRAINT VIOLATION

**Solution: Accept for Now**

```
Rationale:
  - Character/Context deletion is rare
  - Soft-deleted records occupy space temporarily (90–day retention)
  - After 90 days: hard-delete cleanup (batch job) frees up name

If Project Later Requires Reuse:
  Option 1: Partial Unique Index on PostgreSQL
    CREATE UNIQUE INDEX idx_character_name_active
      ON "character"(name) WHERE deleted_at IS NULL;
    DROP INDEX idx_unique_character_name;

  Option 2: Rename on Soft-Delete
    entity.setName(name + "_DELETED_" + UUID);
    entity.setDeletedAt(NOW());
    repository.save(entity);
    → Lossy but releases constraint

Recommendation:
  ✅ Accept Option 1 (partial index) if constraints become a blocker
  → For now: Accept temporary blocking
```

---

## Estimated Timeline (New Dual-Delete Strategy)

| Phase     | Task                                         | Duration      | Risk       | Key Activities                                                 |
| --------- | -------------------------------------------- | ------------- | ---------- | -------------------------------------------------------------- |
| Phase 1   | Add soft-delete column + field + annotations | 1-2 days      | 🟢 LOW     | Entity changes (10 files), Migration V5, DB run                |
| Phase 2   | Service soft-delete methods + cascade logic  | 2-3 days      | 🟡 MEDIUM  | 5 service classes, test soft-delete vs hard-delete             |
| Phase 3   | PATCH endpoints for soft-delete              | 2-3 days      | 🟡 MEDIUM  | 5 controllers, new endpoints, E2E test both delete paths       |
| Phase 4   | Cascade soft-delete & Auth flow              | 1-2 days      | 🔴 HIGH    | User cascade, CustomUserDetailsService, login rejection        |
| Phase 5   | Full E2E testing + performance validation    | 2-3 days      | 🟡 MEDIUM  | Pagination, @Where filter, index performance, data consistency |
| **Total** |                                              | **8-13 days** | **MEDIUM** | Backward-compatible; both hard + soft delete working           |

**Notes:**

- ✅ Existing DELETE endpoints unchanged (hard delete intact)
- ✅ New PATCH endpoints for soft delete (opt-in for new clients)
- ✅ @Where automatically filters all queries (transparent)
- ✅ Can test incrementally (Phase 1 → Phase 2 → etc.)

---

## Implementation Checklist

### Phase 1: Entity Foundation

- [ ] Add `deleted_at: LocalDateTime` field to 10 entities
- [ ] Add `@SQLDelete` + `@Where` annotations to 10 entities
- [ ] Create `V6__add_soft_delete_columns.sql` migration
- [ ] Run migration on dev environment
- [ ] Verify column + indexes created in DB
- [ ] Create `V7__seed_sample_data.sql` migration (sample data cho 11 entities)
- [ ] Run V7 migration: verify 29 sample records inserted
- [ ] Verify sample data accessible via GET endpoints (Swagger)
- [ ] Build & compile: `mvn clean install`
- [ ] No code errors; no functional tests yet

### Phase 2: Service Methods

- [ ] CharacterService: add `softDelete(id, userId, role)` method
- [ ] CharacterService: implement cascade to CharacterDocuments
- [ ] HistoricalContextService: add `softDelete()` + cascade
- [ ] ChatSessionService: add `softDelete()` + cascade to Messages
- [ ] QuizResultService: add `softDelete()` + cascade to QuizAnswerDetail
- [ ] AuthService: add `deactivateUser()` + cascade to all user content
- [ ] Test each service method in isolation (unit tests)
- [ ] Build & compile successfully

### Phase 3: Controller Endpoints

- [ ] CharacterController: `@PatchMapping("/{id}/soft-delete")`
- [ ] HistoricalContextController: `@PatchMapping("/{id}/soft-delete")`
- [ ] ChatSessionController: `@PatchMapping("/{id}/soft-delete")`
- [ ] QuizResultController: `@PatchMapping("/{id}/soft-delete")`
- [ ] AuthController: `@PatchMapping("/me/deactivate")`
- [ ] Verify old DELETE endpoints still hard-delete ✅
- [ ] Test both endpoint types in Swagger
- [ ] Build & compile successfully

### Phase 4: Authentication & Cascade

- [ ] CustomUserDetailsService: check `deleted_at != null` → throw exception
- [ ] AuthServiceImpl: reject soft-deleted user login
- [ ] User.softDelete(): cascade soft-delete all owned content
- [ ] Test: soft-delete user → cannot login
- [ ] Test: cascade affects all 6 content types correctly
- [ ] Build & compile successfully

### Phase 5: Complete Testing

- [ ] Unit tests: entity + annotations + field
- [ ] Integration tests: hard delete vs soft delete behavior
- [ ] E2E tests: full flow per module
- [ ] Performance tests: index usage, pagination with 100K+ deleted rows
- [ ] Regression tests: existing DELETE functionality unchanged
- [ ] Verify all LIST/GET/SEARCH queries respect @Where
- [ ] Documentation updated with new endpoints
- [ ] Final build & test: `mvn clean install && mvn test`

---

## Key Decisions Made (Dual-Delete Strategy)

✅ **Keep old DELETE endpoints:** Physical removal (hard delete) unchanged  
✅ **Add new PATCH endpoints:** Soft deletion via separate endpoints  
✅ **Add `deleted_at` to all entities:** Unified soft-delete capability  
✅ **Use `@SQLDelete` + `@Where`:** Automatic hard-delete conversion + query filtering  
✅ **Cascade soft-delete:** Parent soft-deleted → children also soft-deleted (auto via @Where)  
✅ **Accept unique constraint occupation:** Rare deletion → acceptable; optimize later if needed  
✅ **90-day retention period:** Soft-deleted records preserved; hard-delete after 90 days via batch job

---

## Next Steps (After Plan Approval)

1. ✅ **Review & Approve this Plan**
   - Confirm 5 phases + timeline
   - Confirm dual-delete strategy (hard + soft coexistence)
   - Confirm cascade behavior
   - Confirm unique constraint handling

2. → **Phase 1: Entity Changes** (1-2 days)
   - Modify 10 entity files
   - Create migration V5
   - Run migration + compile

3. → **Phase 2-5: Service → Controller → Auth → Testing** (6-11 days)
   - Services: soft-delete methods
   - Controllers: PATCH endpoints
   - Auth: deactivation + cascade
   - Testing: full E2E coverage

4. → **Final Merge & Deploy**
   - Code review + testing pass
   - Merge to main branch
   - Deploy to production (if approved)

---

## Questions Before Starting

Confirm:

1. ✅ **Dual-delete coexistence**: Keep old DELETE + add new PATCH? (Y/N)
2. ✅ **Cascade behavior**: When parent soft-deleted, children also soft-deleted? (Y/N)
3. ✅ **Unique constraints**: Accept permanent occupation? Or implement partial indexes later? (Accept/Later)
4. ✅ **90-day retention**: Keep soft-deleted records for 90 days before hard-delete? (Y/N)
5. ✅ **Restore API**: Implement admin-only restore (PATCH /{id}/restore) now or later? (Now/Later)

**All set! Ready to begin Phase 1 once approved.** 🚀
