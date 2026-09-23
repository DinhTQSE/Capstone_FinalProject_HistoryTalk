# Quiz Session Detail API

## Background

Currently `GET /api/v1/quizzes/results/me` returns a paginated list of completed sessions with only summary data
(`score`, `percentage`, `completedAt`). There is no endpoint to drill into a single session and see
which answer the user picked for every question.

All the raw data is already persisted — `QuizAnswerDetail` rows are saved at submit time with `selectedOption`
and `isCorrect`. The implementation is purely additive: one new DTO, one repository query, one service method,
and one controller route.

---

## Proposed Changes

### 1. DTO Layer — [NEW] `QuizSessionDetailResponse.java`

**Path:** `src/main/java/com/historytalk/dto/quiz/QuizSessionDetailResponse.java`

A new response DTO that wraps the session summary **plus** a list of per-question answer entries:

```java
QuizSessionDetailResponse {
    String sessionId
    String quizId
    String quizTitle
    int    score
    int    totalQuestions
    double percentage
    Integer limitedTime   // seconds set by user at start — null if no time limit
    String startedAt      // ISO-8601 — session.startTime
    String completedAt    // ISO-8601 — session.endTime
    List<QuestionResultItem> questions
}

QuestionResultItem {          // inner static class / separate file
    String  questionId
    String  content           // question text
    List<String> options      // all 4 options (deserialized)
    int     correctAnswer     // 0-based index of the right answer
    Integer selectedAnswer    // 0-based index the user chose (null if unanswered)
    boolean isCorrect
    String  explanation       // may be null
}
```

> **Note:** `correctAnswer` and `explanation` are included so the frontend can show what the right answer
> was alongside the user's choice — no separate call needed.

---

### 2. Repository Layer

#### [MODIFY] `QuizAnswerDetailRepository.java`

Add one JPQL query to fetch all non-deleted answer details for a session, with `question` eagerly joined
to avoid N+1 selects:

```java
@Query("""
    SELECT d FROM QuizAnswerDetail d
    JOIN FETCH d.question q
    WHERE d.quizSession.sessionId = :sessionId
      AND d.deletedAt IS NULL
      AND q.deletedAt IS NULL
    ORDER BY q.createdAt ASC
""")
List<QuizAnswerDetail> findBySessionId(@Param("sessionId") UUID sessionId);
```

#### [MODIFY] `QuizSessionRepository.java`

Add two new queries for the admin history list endpoints:

```java
// All completed sessions across all users (admin — paginated)
@Query("""
    SELECT s FROM QuizSession s
    WHERE s.endTime IS NOT NULL
      AND s.deletedAt IS NULL
    ORDER BY s.endTime DESC
""")
Page<QuizSession> findAllCompleted(Pageable pageable);

// All completed sessions for one specific user (admin lookup by userId)
@Query("""
    SELECT s FROM QuizSession s
    WHERE s.user.uid = :uid
      AND s.endTime IS NOT NULL
      AND s.deletedAt IS NULL
    ORDER BY s.endTime DESC
""")
Page<QuizSession> findCompletedByUserUidForAdmin(@Param("uid") UUID uid, Pageable pageable);
```

---

### 3. Service Layer

#### [MODIFY] `QuizService.java`

Add new method signatures — `// Customer` section:

```java
// userId = null → admin mode, skip ownership check
QuizSessionDetailResponse getSessionDetail(String sessionId, UUID userId);
```

Add new method signatures — `// Staff` section:

```java
PaginatedResponse<QuizHistoryResponse> getAllUsersQuizHistory(Pageable pageable);
PaginatedResponse<QuizHistoryResponse> getQuizHistoryByUserId(String userId, Pageable pageable);
```

#### [MODIFY] `QuizServiceImpl.java`

Implement `getSessionDetail`:

```
1. Parse sessionId → UUID
2. Load QuizSession via quizSessionRepository.findBySessionId(...)
   → throw ResourceNotFoundException if absent
3. If userId != null (CUSTOMER mode): ownership check — throw 400 if mismatch
   If userId == null (SYSTEM_ADMIN mode): skip ownership check
4. Completion check: endTime must NOT be null → throw 400 if null
5. Load answer details via quizAnswerDetailRepository.findBySessionId(sessionUuid)
   → build Map<UUID questionId, QuizAnswerDetail> for O(1) lookup
6. Load ordered questions via questionRepository.findActiveByQuizId(quiz.getQuizId())
7. For each question, build QuestionResultItem (selectedAnswer = null if skipped)
8. Return QuizSessionDetailResponse including limitedTime from session entity
```

Implement `getAllUsersQuizHistory`:
```
1. Call quizSessionRepository.findAllCompleted(pageable)
2. Map each session through existing mapToHistoryResponse()
3. Return as PaginatedResponse
```

Implement `getQuizHistoryByUserId`:
```
1. Parse userId → UUID
2. Verify user exists via userRepository.findById() → throw 404 if not
3. Call quizSessionRepository.findCompletedByUserUidForAdmin(uid, pageable)
4. Map each session through mapToHistoryResponse()
5. Return as PaginatedResponse
```


---

### 4. Controller Layer

#### [MODIFY] `QuizController.java`

Add one new endpoint directly below the existing `GET /results/me`:

```
GET /api/v1/quizzes/results/me/{sessionId}
  Role:        CUSTOMER (bearerAuth required)
  Path var:    sessionId — UUID string
  Returns:     ApiResponse<QuizSessionDetailResponse>
  Swagger:     "Get quiz session detail"
  Description: "Returns the full answer breakdown for one of the caller's own completed sessions."
  Guard:       ownership check — throws 400 if session belongs to another user
```

#### [MODIFY] `StaffQuizController.java`

Add **three** new endpoints, all restricted to `SYSTEM_ADMIN` via method-level `@PreAuthorize`:

```
// 1 — All users' history (paginated)
GET /api/v1/staff/quizzes/sessions
  Role:        SYSTEM_ADMIN
  Params:      page (default 0), size (default 10)
  Returns:     ApiResponse<PaginatedResponse<QuizHistoryResponse>>
  Swagger:     "Get all users' quiz history (admin)"
  Description: "Paginated list of all completed quiz sessions across all users."

// 2 — One specific user's history (paginated)
GET /api/v1/staff/quizzes/sessions?userId={userId}
  Role:        SYSTEM_ADMIN
  Params:      userId (optional UUID), page (default 0), size (default 10)
  Returns:     ApiResponse<PaginatedResponse<QuizHistoryResponse>>
  Swagger:     "Get a specific user's quiz history (admin)"
  Description: "When userId is provided, filters results to that user only."
  Note:        Merged into endpoint #1 via optional @RequestParam userId

// 3 — Any session's full detail
GET /api/v1/staff/quizzes/sessions/{sessionId}
  Role:        SYSTEM_ADMIN
  Path var:    sessionId — UUID string
  Returns:     ApiResponse<QuizSessionDetailResponse>
  Swagger:     "Get any user's quiz session detail (admin)"
  Description: "Full answer breakdown for any completed session regardless of owner."
  Guard:       passes userId = null to service (skip ownership check)
```

> [!NOTE]
> Endpoints #1 and #2 are combined into a single controller method:
> `GET /api/v1/staff/quizzes/sessions` with an **optional** `?userId=` param.
> When `userId` is absent → call `getAllUsersQuizHistory(pageable)`.
> When `userId` is present → call `getQuizHistoryByUserId(userId, pageable)`.

---

## Final Endpoint Summary

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/quizzes/results/me` | CUSTOMER | Own paginated history list *(existing)* |
| `GET` | `/api/v1/quizzes/results/me/{sessionId}` | CUSTOMER | **New** — Own session full detail |
| `GET` | `/api/v1/staff/quizzes/sessions` | SYSTEM_ADMIN | **New** — All users' history (paginated) |
| `GET` | `/api/v1/staff/quizzes/sessions?userId={id}` | SYSTEM_ADMIN | **New** — One specific user's history |
| `GET` | `/api/v1/staff/quizzes/sessions/{sessionId}` | SYSTEM_ADMIN | **New** — Any user's session full detail |

---

## Access Control

| Role | Scope |
|---|---|
| `CUSTOMER` | Can only view their **own** completed sessions |
| `SYSTEM_ADMIN` | Can view **any** user's completed session detail |
| `CONTENT_ADMIN` | No access to session detail (manages quiz content only) |

This results in **two separate routes** following the existing pattern:

- `GET /api/v1/quizzes/results/me/{sessionId}` — `CUSTOMER` in `QuizController` (ownership enforced)
- `GET /api/v1/staff/quizzes/sessions/{sessionId}` — `SYSTEM_ADMIN` in `StaffQuizController` (no ownership check; can view anyone's session)

> [!NOTE]
> `correctAnswer` is **always returned** in the response. The endpoint only works on fully submitted sessions
> (`endTime != null`), so there is no scenario where a quiz is "in progress" here. If a user submitted
> but skipped some questions, those skipped questions still appear with `selectedAnswer: null` and
> `correctAnswer` is shown — which is the correct and expected behaviour.

---

## Verification Plan

### Automated compile check
```
mvn -q -DskipTests compile
```

### Manual Swagger verification (`:8080/Historical-tell/api/v1/swagger-ui`)
1. Authenticate as a CUSTOMER.
2. Start and submit a quiz → note the `sessionId` from the submit response.
3. Call `GET /quizzes/results/me/{sessionId}` — verify:
   - All questions appear, including any skipped ones (`selectedAnswer: null`)
   - `correctAnswer` and `isCorrect` match the quiz definition
   - `selectedAnswer` matches what was submitted
4. Call the endpoint with another user's `sessionId` → expect `400 Not authorized`.
5. Call with a non-existent `sessionId` → expect `404 Not found`.
6. Call with a session that was started but not yet submitted → expect `400 Session not completed yet`.
