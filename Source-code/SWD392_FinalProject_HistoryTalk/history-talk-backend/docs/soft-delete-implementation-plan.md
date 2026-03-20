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
