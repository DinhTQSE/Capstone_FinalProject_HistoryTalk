# Soft-Delete FK Reference Fix Plan — Bỏ `@Where`, Manual Filter theo Role

**Date:** March 23, 2026  
**Status:** Planning Phase (Awaiting Review)  
**Strategy:** Bỏ `@Where` + `@SQLDelete` → manual filter `deleted_at IS NULL` cho User, query full cho Admin/Staff

---

## Tóm tắt chiến lược

```
                 ┌─────────────────────────────────┐
                 │        SOFT-DELETE FIX           │
                 └─────────────────────────────────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         ▼                      ▼                      ▼
   ┌───────────┐        ┌─────────────┐        ┌─────────────┐
   │ LAYER 1   │        │  LAYER 2    │        │  LAYER 3    │
   │ Remove    │        │ Cascade     │        │ Null-safe   │
   │ @Where +  │        │ soft-delete │        │ mapping     │
   │ @SQLDelete│        │ parent→child│        │ (try-catch) │
   │ + manual  │        │             │        │             │
   │ filter    │        │             │        │             │
   └───────────┘        └─────────────┘        └─────────────┘
   Admin: full data      Xóa cha → con          Phòng orphan
   User: active only     cũng bị xóa mềm       từ data cũ
```

**3 lớp bảo vệ:**

1. **Layer 1 — Bỏ `@Where` + `@SQLDelete`, Manual Filter:** Admin thấy tất cả, User chỉ thấy `deleted_at IS NULL`. FK lazy-load **KHÔNG BAO GIỜ LỖI** vì Hibernate load được row dù đã bị xóa mềm.
2. **Layer 2 — Cascade Soft-Delete:** Khi xóa mềm parent → tự động xóa mềm children.
3. **Layer 3 — Null-Safe Mapping:** Try-catch khi truy cập FK parent trong mapping code, phòng edge case.

---

## Problem Analysis (Nhắc lại)

### Root Cause hiện tại

`@Where(clause = "deleted_at IS NULL")` trên entity chặn lazy-load `@ManyToOne` FK → child truy cập parent đã bị xóa mềm → crash `EntityNotFoundException`.

### Giải pháp: Bỏ `@Where` hoàn toàn

- Hibernate load được **tất cả** row kể cả soft-deleted → FK lazy-load luôn thành công
- Filter `deleted_at IS NULL` được **đẩy về Repository/Service layer** → kiểm soát theo role

---

## Proposed Changes

### Phase 1: Entity Changes — Bỏ `@Where` + `@SQLDelete`

Xóa 2 annotation khỏi **TẤT CẢ 11 entity**:

#### Danh sách entity cần sửa

| # | Entity | File | Annotations cần XÓA |
|---|---|---|---|
| 1 | `Quiz` | `entity/quiz/Quiz.java` | `@SQLDelete(...)` + `@Where(...)` |
| 2 | `Question` | `entity/quiz/Question.java` | `@SQLDelete(...)` + `@Where(...)` |
| 3 | `QuizResult` | `entity/quiz/QuizResult.java` | `@SQLDelete(...)` + `@Where(...)` |
| 4 | `QuizAnswerDetail` | `entity/quiz/QuizAnswerDetail.java` | `@SQLDelete(...)` + `@Where(...)` |
| 5 | `QuizSession` | `entity/quiz/QuizSession.java` | `@SQLDelete(...)` + `@Where(...)` |
| 6 | `Character` | `entity/character/Character.java` | `@SQLDelete(...)` + `@Where(...)` |
| 7 | `CharacterDocument` | `entity/character/CharacterDocument.java` | `@SQLDelete(...)` + `@Where(...)` |
| 8 | `HistoricalContext` | `entity/historicalContext/HistoricalContext.java` | `@SQLDelete(...)` + `@Where(...)` |
| 9 | `HistoricalContextDocument` | `entity/historicalContext/HistoricalContextDocument.java` | `@SQLDelete(...)` + `@Where(...)` |
| 10 | `ChatSession` | `entity/chat/ChatSession.java` | `@SQLDelete(...)` + `@Where(...)` |
| 11 | `Message` | `entity/chat/Message.java` | `@SQLDelete(...)` + `@Where(...)` |
| 12 | `User` | `entity/user/User.java` | `@SQLDelete(...)` + `@Where(...)` |

**Ví dụ thay đổi (Quiz.java):**
```diff
 @Entity
 @Table(name = "quiz")
-@SQLDelete(sql = "UPDATE quiz SET deleted_at = NOW() WHERE quiz_id=?")
-@Where(clause = "deleted_at IS NULL")
 public class Quiz {
     // ... giữ nguyên field deleted_at
 }
```

> [!IMPORTANT]
> Sau khi bỏ `@Where`, `@SQLDelete`:
> - `repository.delete(entity)` sẽ **HARD DELETE** (xóa vật lý) — đây **CHÍNH LÀ** behavior mong muốn cho các DELETE endpoint
> - Tất cả `repository.findAll()`, `findById()` sẽ **trả về cả row đã bị xóa mềm** → cần thêm filter ở Repository (Phase 2)
> - Các method `softDeleteXxx()` (PATCH endpoint) **GIỮ NGUYÊN logic cũ** (`entity.setDeletedAt(now)` + `repository.save()`) → chỉ thêm cascade children
>
> **⚠️ NGUYÊN TẮC: KHÔNG SỬA các method `deleteXxx()` (hard delete). Chỉ sửa `softDeleteXxx()` + GET queries.**

---

### Phase 2: Repository Changes — Thêm Manual Filter

#### Logic filter:
- Có tham số `includeDeleted` (boolean):
  - `true` → Admin/Staff: query tất cả (kể cả soft-deleted)
  - `false` → User: chỉ query `deleted_at IS NULL`

#### [MODIFY] `QuizRepository.java`

```diff
 // findAllWithSearch — thêm filter deleted_at
 SELECT q FROM Quiz q
 WHERE (:search IS NULL OR :search = ''
        OR q.title ILIKE CONCAT('%', :search, '%')
        OR q.description ILIKE CONCAT('%', :search, '%'))
 AND (:grade IS NULL OR q.grade = :grade)
 AND (:era IS NULL OR q.era = :era)
+AND (:includeDeleted = true OR q.deletedAt IS NULL)

 // findAllByContextWithSearch — thêm filter deleted_at
+AND (:includeDeleted = true OR q.deletedAt IS NULL)

 // findAllSimple (customer) — đổi filter
-AND q.deletedAt IS NULL
+AND (:includeDeleted = true OR q.deletedAt IS NULL)

 // findByTitleIgnoreCase — thêm custom query
+@Query("SELECT q FROM Quiz q WHERE UPPER(q.title) = UPPER(:title) AND q.deletedAt IS NULL")
+Optional<Quiz> findActiveByTitleIgnoreCase(@Param("title") String title);
```

#### [MODIFY] `QuestionRepository.java`

```diff
-@Query("SELECT q FROM Question q WHERE q.quiz.quizId = :quizId ORDER BY q.orderIndex ASC")
+@Query("SELECT q FROM Question q WHERE q.quiz.quizId = :quizId AND (:includeDeleted = true OR q.deletedAt IS NULL) ORDER BY q.orderIndex ASC")
+List<Question> findByQuizIdOrderByOrderIndex(@Param("quizId") UUID quizId, @Param("includeDeleted") boolean includeDeleted);
```

#### [MODIFY] `HistoricalContextRepository.java`

```diff
 // findAllWithSearch
+AND (:includeDeleted = true OR hc.deletedAt IS NULL)

 // findAllSimple
+AND (:includeDeleted = true OR hc.deletedAt IS NULL)
```

> [!NOTE]
> `findAllDeleted()` và `restoreById()` — **GIỮ NGUYÊN** vì chúng đã dùng native query filter `deleted_at IS NOT NULL`.

#### [MODIFY] `CharacterRepository.java`

```diff
 // findByContextIdOrderByNameAsc
+AND (:includeDeleted = true OR c.deletedAt IS NULL)

 // findAllWithFilter
+AND (:includeDeleted = true OR c.deletedAt IS NULL)
```

> [!NOTE]
> `findAllDeleted()` và `restoreById()` — **GIỮ NGUYÊN**.

#### [MODIFY] `ChatSessionRepository.java`

```diff
 // findByUserAndCharacterAndContext
+AND cs.deletedAt IS NULL

 // findAllByUserUid — thêm includeDeleted
+AND (:includeDeleted = true OR cs.deletedAt IS NULL)
```

#### [MODIFY] `HistoricalContextDocumentRepository.java`

```diff
 // search — thêm filter
+AND (:includeDeleted = true OR hcd.deletedAt IS NULL)

 // findAllByOrderByUploadDateDesc — thay bằng custom query
+@Query("SELECT d FROM HistoricalContextDocument d WHERE (:includeDeleted = true OR d.deletedAt IS NULL) ORDER BY d.uploadDate DESC")
+List<HistoricalContextDocument> findAllActive(@Param("includeDeleted") boolean includeDeleted);
```

#### [MODIFY] `QuizResultRepository.java`

```diff
-Page<QuizResult> findByUserUid(UUID uid, Pageable pageable);
+@Query("SELECT r FROM QuizResult r WHERE r.user.uid = :uid AND (:includeDeleted = true OR r.deletedAt IS NULL) ORDER BY r.takenDate DESC")
+Page<QuizResult> findByUserUid(@Param("uid") UUID uid, @Param("includeDeleted") boolean includeDeleted, Pageable pageable);
```

#### [MODIFY] `MessageRepository.java`

```diff
-List<Message> findByChatSessionSessionIdOrderByTimestampAsc(UUID sessionId);
+@Query("SELECT m FROM Message m WHERE m.chatSession.sessionId = :sessionId AND (:includeDeleted = true OR m.deletedAt IS NULL) ORDER BY m.timestamp ASC")
+List<Message> findByChatSessionSessionIdOrderByTimestampAsc(@Param("sessionId") UUID sessionId, @Param("includeDeleted") boolean includeDeleted);
```

#### `UserRepository.java` — Đặc biệt

```diff
 // findByEmailIgnoreCase — KHÔNG thêm filter (cần tìm user bất kể trạng thái để check login)
 // GIỮ NGUYÊN — check deleted_at trong AuthService thay vì repository
```

---

### Phase 3: Service Changes — Truyền `includeDeleted` theo Role

#### Logic chung trong Service:

```java
// Helper method — dùng trong tất cả service
private boolean isStaffOrAdmin(String role) {
    return role != null && ("STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
}
```

#### [MODIFY] `QuizServiceImpl.java`

**GET queries — thêm `includeDeleted`:**
```diff
 // getAllQuizzesForStaff — Admin/Staff thấy tất cả
-Page<Quiz> page = quizRepository.findAllWithSearch(normalize(search), grade, era, pageable);
+Page<Quiz> page = quizRepository.findAllWithSearch(normalize(search), grade, era, true, pageable);

 // getAllQuizzesForCustomer — Customer chỉ thấy active
-return quizRepository.findAllSimple(normalize(search))
+return quizRepository.findAllSimple(normalize(search), false)

 // getQuizHistory — Customer chỉ thấy active results
-Page<QuizResult> page = quizResultRepository.findByUserUid(..., pageable);
+Page<QuizResult> page = quizResultRepository.findByUserUid(..., false, pageable);
```

**⚠️ `deleteQuiz()` (hard delete) — KHÔNG SỬA, giữ nguyên `quizRepository.delete(quiz)` → xóa vật lý**

**Soft-delete methods — thêm cascade (xem Phase 4):**
- `softDeleteQuizResult()` → thêm cascade tới `QuizAnswerDetail`
- `softDeleteQuizSession()` → giữ nguyên (không có children)
- Thêm `softDeleteQuiz()` method (nếu chưa có) → cascade tới Questions, QuizResults, QuizSessions

**Null-safe mapping (Layer 3):**

```diff
 // mapToStaffResponse
-.contextId(quiz.getHistoricalContext().getContextId().toString())
-.contextTitle(quiz.getHistoricalContext().getName())
+.contextId(safeGetContextId(quiz))
+.contextTitle(safeGetContextName(quiz))

 // mapToHistoryResponse
-.quizTitle(result.getQuiz().getTitle())
+.quizTitle(safeGetQuizTitle(result))
```

```java
// Thêm helper methods
private String safeGetContextId(Quiz quiz) {
    try {
        HistoricalContext ctx = quiz.getHistoricalContext();
        return ctx != null ? ctx.getContextId().toString() : null;
    } catch (Exception e) {
        log.warn("Failed to load context for quiz {}", quiz.getQuizId());
        return null;
    }
}

private String safeGetContextName(Quiz quiz) {
    try {
        HistoricalContext ctx = quiz.getHistoricalContext();
        return ctx != null ? ctx.getName() : "[Deleted Context]";
    } catch (Exception e) {
        return "[Deleted Context]";
    }
}

private String safeGetQuizTitle(QuizResult result) {
    try {
        Quiz quiz = result.getQuiz();
        return quiz != null ? quiz.getTitle() : "[Deleted Quiz]";
    } catch (Exception e) {
        return "[Deleted Quiz]";
    }
}
```

#### [MODIFY] `CharacterService.java`

**GET queries — thêm `includeDeleted`:**
```diff
 // getAllCharacters
-Page<Character> result = characterRepository.findAllWithFilter(normalize(search), era, includeDraft, pageable);
+boolean includeDeleted = isStaffOrAdmin(role);
+Page<Character> result = characterRepository.findAllWithFilter(normalize(search), era, includeDraft, includeDeleted, pageable);
```

**⚠️ `deleteCharacter()` (hard delete) — KHÔNG SỬA, giữ nguyên `characterRepository.delete(character)` → xóa vật lý**

**`softDeleteCharacter()` — ĐÃ CÓ cascade, verify lại:**
- ✅ Đã cascade soft-delete `CharacterDocuments`, `ChatSessions`, `Messages`
- Chỉ cần verify cascade depth đúng

#### [MODIFY] `ChatSessionService.java`

```diff
 // mapToResponse — null-safe
-.characterId(session.getCharacter().getCharacterId().toString())
-.contextId(session.getHistoricalContext().getContextId().toString())
+.characterId(safeGetCharacterId(session))
+.contextId(safeGetContextId(session))
```

```java
private String safeGetCharacterId(ChatSession session) {
    try {
        Character c = session.getCharacter();
        return c != null ? c.getCharacterId().toString() : null;
    } catch (Exception e) {
        return null;
    }
}
```

#### [MODIFY] `HistoricalContextService.java`

```diff
 // getAllContexts — truyền includeDeleted theo role
+boolean includeDeleted = isStaffOrAdmin(role);
```

#### [MODIFY] `HistoricalContextDocumentService.java`

Tương tự, truyền `includeDeleted` theo role.

---

### Phase 4: Cascade Soft-Delete (Layer 2 — Prevention)

Khi soft-delete parent → manual cascade set `deleted_at` cho tất cả children.

#### Cascade Map

```
Quiz (soft-delete)
  └→ Questions (set deleted_at)
       └→ QuizAnswerDetails (set deleted_at)
  └→ QuizResults (set deleted_at)
       └→ QuizAnswerDetails (set deleted_at)
  └→ QuizSessions (set deleted_at)

Character (soft-delete)
  └→ CharacterDocuments (set deleted_at)
  └→ ChatSessions (set deleted_at)
       └→ Messages (set deleted_at)

HistoricalContext (soft-delete)
  └→ HistoricalContextDocuments (set deleted_at)
  └→ Quizzes (set deleted_at) + cascade Quiz children
  └→ Characters (detach many-to-many, KHÔNG xóa)

User (soft-delete / deactivate)
  └→ ChatSessions + Messages
  └→ QuizResults + QuizAnswerDetails
  └→ QuizSessions
  └→ (KHÔNG xóa content do User tạo — chỉ xóa interaction data)
```

**Ví dụ code cascade soft-delete Quiz:**

```java
@Transactional
public void softDeleteQuiz(String quizId, String userId, String userRole) {
    Quiz quiz = quizRepository.findById(UUID.fromString(quizId))
        .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
    
    checkOwnershipOrAdmin(quiz.getCreatedBy().getUid().toString(), userId, userRole);
    
    LocalDateTime now = LocalDateTime.now();
    quiz.setDeletedAt(now);
    
    // Cascade → Questions + their AnswerDetails
    quiz.getQuestions().forEach(q -> {
        q.setDeletedAt(now);
        q.getAnswerDetails().forEach(ad -> ad.setDeletedAt(now));
    });
    
    // Cascade → QuizResults + their AnswerDetails
    quiz.getQuizResults().forEach(r -> {
        r.setDeletedAt(now);
        r.getAnswerDetails().forEach(ad -> ad.setDeletedAt(now));
    });
    
    quizRepository.save(quiz); // cascades via CascadeType.ALL
}
```

---

## Summary of ALL File Changes

| # | File | Change | Description |
|---|---|---|---|
| **Entity (12 files)** | | | |
| 1-12 | All 12 entity files | MODIFY | Xóa `@SQLDelete` + `@Where` annotations |
| **Repository (9 files)** | | | |
| 13 | `QuizRepository.java` | MODIFY | Thêm `includeDeleted` param vào 4 queries |
| 14 | `QuestionRepository.java` | MODIFY | Thêm `includeDeleted` param |
| 15 | `HistoricalContextRepository.java` | MODIFY | Thêm `includeDeleted` param vào 2 queries |
| 16 | `CharacterRepository.java` | MODIFY | Thêm `includeDeleted` param vào 2 queries |
| 17 | `ChatSessionRepository.java` | MODIFY | Thêm filter + `includeDeleted` |
| 18 | `HistoricalContextDocumentRepository.java` | MODIFY | Thêm `includeDeleted` param |
| 19 | `QuizResultRepository.java` | MODIFY | Thêm `includeDeleted` param |
| 20 | `MessageRepository.java` | MODIFY | Thêm `includeDeleted` param |
| 21 | `QuizSessionRepository.java` | MODIFY | Thêm `includeDeleted` param |
| **Service (5+ files)** | | | |
| 22 | `QuizServiceImpl.java` | MODIFY | Truyền `includeDeleted`, cascade soft-delete, null-safe mapping |
| 23 | `CharacterService.java` | MODIFY | Truyền `includeDeleted`, cascade soft-delete |
| 24 | `ChatSessionService.java` | MODIFY | Truyền `includeDeleted`, null-safe mapping |
| 25 | `HistoricalContextService.java` | MODIFY | Truyền `includeDeleted` |
| 26 | `HistoricalContextDocumentService.java` | MODIFY | Truyền `includeDeleted` |

**Tổng: ~26 files cần sửa**

---

## What Does NOT Need to Change

| Case | Reason |
|---|---|
| **Hard-delete methods** (`deleteQuiz()`, `deleteCharacter()`, `deleteSession()`, etc.) | Giữ nguyên `repository.delete()` → xóa vật lý. **KHÔNG SỬA.** |
| **Hard-delete controllers** (`@DeleteMapping`) | Giữ nguyên — endpoint + logic không đổi |
| `UserRepository` | Check `deleted_at` trong AuthService, không trong repository |
| `findAllDeleted()` methods | Đã dùng native query `deleted_at IS NOT NULL` — giữ nguyên |
| `restoreById()` methods | Đã dùng native query — giữ nguyên |
| Entity `deleted_at` field | Giữ nguyên field, chỉ xóa annotation |
| `@OneToMany` collections | Không cần thay đổi — collection load tất cả, filter ở service |

> [!CAUTION]
> **Quy tắc quan trọng:** Chỉ sửa:
> 1. **Entity:** Bỏ `@Where` + `@SQLDelete`
> 2. **Repository:** Thêm `includeDeleted` filter vào GET queries
> 3. **Service GET methods:** Truyền `includeDeleted` theo role
> 4. **Service `softDeleteXxx()` methods:** Thêm cascade children
> 5. **Service mapping methods:** Thêm null-safe try-catch
>
> **KHÔNG SỬA:** `deleteXxx()` methods, `@DeleteMapping` controllers, hard-delete logic

---

## Verification Plan

### Build
- `mvn clean install` phải pass

### Manual Testing via Swagger

1. **Admin GET all quizzes** → Thấy cả quiz đã bị soft-delete (có `deleted_at` timestamp)
2. **Customer GET all quizzes** → Chỉ thấy quiz active (`deleted_at IS NULL`)
3. **Soft-delete Quiz** → Questions, QuizResults, QuizSessions cũng bị cascade soft-delete
4. **Customer GET quiz history** → QuizResult trỏ tới Quiz đã xóa → hiện `[Deleted Quiz]`, không lỗi 500
5. **Soft-delete Character** → ChatSessions + Messages cũng bị cascade soft-delete
6. **Admin GET chat sessions** → Thấy cả sessions đã bị soft-delete
7. **Customer GET chat sessions** → Chỉ thấy sessions active

### Edge Cases
- Soft-delete parent **KHÔNG** soft-delete grandchild qua FK → verify cascade depth
- Restore parent → children **VẪN** bị soft-delete (cần restore riêng hoặc cascade restore)
- Login user đã deactivated → 401 Unauthorized (check trong AuthService)
