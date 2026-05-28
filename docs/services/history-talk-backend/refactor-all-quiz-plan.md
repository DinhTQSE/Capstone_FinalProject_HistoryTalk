# Quiz Module — From-Scratch Implementation Plan

> Historical plan note: this file predates the lifecycle/trash refactor.
> Current quiz lifecycle uses `isPublished`, `deletedAt`, `status`, and
> `PATCH /api/v1/staff/quizzes/{quizId}/soft-delete`.
> Use `docs/API_CONTRACT.md` and `docs/PROJECT_HANDOFF.md` for current behavior.

## Context

All old quiz code has been deleted. This plan creates the entire quiz module fresh based on:
- **API contract** (15 APIs, sections 7 & 8)
- **Existing entities** (Quiz, Question, QuizSession, QuizAnswerDetail — unchanged)
- **Project conventions** (`copilot-instructions.md`)

---

## Entity Reference (DO NOT MODIFY)

### `Quiz`
```
quizId (UUID PK) | historicalContext (FK → HistoricalContext) | createdBy (FK → User)
title (String) | level (QuizLevel: EASY/MEDIUM/HARD) | isActive (Boolean, default true)
createdAt | updatedAt | deletedAt (nullable) | questions (List<Question>)
```
> `era` is **NOT** on `Quiz`. Read it from `quiz.getHistoricalContext().getEra()`.

### `Question`
```
questionId (UUID PK) | quiz (FK → Quiz) | content (TEXT) | options (TEXT, JSON array of 4 strings)
correctAnswer (Integer 0-3) | explanation (TEXT, nullable) | isActive (Boolean)
createdAt | updatedAt | deletedAt (nullable) | answerDetails (List<QuizAnswerDetail>)
```
> **No `orderIndex` column.** Sort by `createdAt ASC`.

### `QuizSession`
```
sessionId (UUID PK) | quiz (FK → Quiz) | user (FK → User)
limitedTime (Integer, seconds, nullable) | startTime (LocalDateTime)
endTime (LocalDateTime, nullable — NULL means NOT yet submitted)
score (Float, nullable) | isActive (Boolean)
createdAt | updatedAt | deletedAt (nullable) | answerDetails (List<QuizAnswerDetail>)
```
> **No `isSubmitted` field.** A submitted session has `endTime IS NOT NULL`.

### `QuizAnswerDetail`
```
answerId (UUID PK) | question (FK → Question) | quizSession (FK → QuizSession)
selectedOption (Integer) | isCorrect (Boolean) | isActive (Boolean)
createdAt | updatedAt | deletedAt (nullable)
```

---

## All 15 APIs

| # | Method | Path | Role | Description |
|---|---|---|---|---|
| 1 | GET | `/quizzes` | Public | List active quizzes. Returns array (not paginated). |
| 2 | GET | `/quizzes/:quizId` | Public | Quiz detail (QuizSet shape). |
| 3 | POST | `/quizzes/:quizId/start` | CUSTOMER | Create session. `?limitedTime=N` (seconds, optional). |
| 4 | POST | `/quizzes/submit` | CUSTOMER | Submit answers, terminate session. |
| 5 | GET | `/quizzes/results/me` | CUSTOMER | Paginated quiz history (completed sessions). |
| 6 | GET | `/staff/quizzes` | CONTENT_ADMIN / SYSTEM_ADMIN | Paginated quiz list for staff. |
| 7 | GET | `/staff/quizzes/:quizId` | CONTENT_ADMIN / SYSTEM_ADMIN | Full quiz detail with questions. |
| 8 | POST | `/staff/quizzes` | CONTENT_ADMIN / SYSTEM_ADMIN | Create quiz with initial questions. |
| 9 | PUT | `/staff/quizzes/:quizId` | CONTENT_ADMIN / SYSTEM_ADMIN | Update quiz metadata (not questions). |
| 10 | DELETE | `/staff/quizzes/:quizId` | CONTENT_ADMIN / SYSTEM_ADMIN | Hard delete quiz. |
| 11 | PATCH | `/staff/quizzes/:quizId/soft-delete` | CONTENT_ADMIN / SYSTEM_ADMIN | Soft delete (set `deletedAt`). |
| 12 | PATCH | `/staff/quizzes/:quizId/toggle-active` | CONTENT_ADMIN / SYSTEM_ADMIN | Flip `isActive`. |
| 13 | POST | `/staff/quizzes/:quizId/questions` | CONTENT_ADMIN / SYSTEM_ADMIN | Add question, returns new QuizQuestion. |
| 14 | PUT | `/staff/quizzes/:quizId/questions/:questionId` | CONTENT_ADMIN / SYSTEM_ADMIN | Partial update question. |
| 15 | DELETE | `/staff/quizzes/:quizId/questions/:questionId` | CONTENT_ADMIN / SYSTEM_ADMIN | Hard delete question. |

---

## Proposed Changes

---

### Step 1 — DTOs

All DTOs are in `com.historytalk.dto.quiz`.

---

#### [MODIFY] `QuizCustomerResponse.java`
Contract shape `QuizSet`:
```java
String quizId
String title
String level        // "EASY" | "MEDIUM" | "HARD"
String era          // from historicalContext.era
int playCount       // completed sessions count for this user
String contextTitle // nullable
```

---

#### [MODIFY] `QuizStaffResponse.java`
Contract shape `ContentAdminQuizSet`:
```java
String quizId
String title
String era          // from historicalContext.era
String level        // "EASY" | "MEDIUM" | "HARD"
int playCount       // total completed sessions across all users
String contextId
String contextTitle
String createdBy    // username
LocalDateTime createdDate
LocalDateTime updatedDate
Boolean isActive
LocalDateTime deletedAt   // nullable
List<QuestionResponse> questions
```

---

#### [MODIFY] `QuestionResponse.java`
Contract shape `QuizQuestion`:
```java
String questionId
String content
List<String> options    // 4 elements, deserialized from JSON
Integer correctAnswer   // 0-3
String explanation      // nullable
```

---

#### [MODIFY] `QuizStartResponse.java`
```java
String sessionId
String quizId
String title
List<QuestionResponse> questions
```

---

#### [MODIFY] `QuizSubmitRequest.java`
```java
@NotNull String sessionId
@NotEmpty List<AnswerDetailRequest> answers
```

---

#### [MODIFY] `AnswerDetailRequest.java`
```java
@NotNull String questionId
@NotNull Integer selectedAnswer
```

---

#### [MODIFY] `QuizSubmitResponse.java`
```java
String resultId        // = sessionId value
int score
int totalQuestions
double percentage
List<Integer> correctAnswers  // 0-based question index positions
List<Integer> wrongAnswers    // 0-based question index positions
```

---

#### [MODIFY] `QuizHistoryResponse.java`
```java
String sessionId
String quizId
String quizTitle
int score
int totalQuestions
double percentage
String completedAt   // ISO8601 from session.endTime
```

---

#### [MODIFY] `CreateQuizRequest.java`
```java
@NotBlank String title
@NotNull String contextId
String era          // optional, matches EventEra enum name
@NotNull String level   // required — QuizLevel enum (EASY/MEDIUM/HARD)
@NotEmpty List<QuestionRequest> questions
```

---

#### [MODIFY] `UpdateQuizRequest.java`
All fields optional (partial update):
```java
String title       // null = no change
String contextId   // null = no change (changes era indirectly)
String level       // null = no change
```
> `era` is NOT updatable directly — it belongs to the context. Change context to change era.

---

#### [MODIFY] `QuestionRequest.java`
```java
@NotBlank String content
List<String> options     // 4 elements
@NotNull Integer correctAnswer   // 0-3
String explanation       // optional
```

---

### Step 2 — Repositories

---

#### [MODIFY] `QuizRepository.java`

```java
// Customer: active quizzes, optional search
@Query("""
    SELECT q FROM Quiz q
    WHERE (:search IS NULL OR q.title ILIKE CONCAT('%', :search, '%'))
    AND q.isActive = true AND q.deletedAt IS NULL
    ORDER BY q.title ASC
""")
List<Quiz> findAllActiveForCustomer(@Param("search") String search);

// Customer: single active quiz by ID
@Query("SELECT q FROM Quiz q WHERE q.quizId = :quizId AND q.isActive = true AND q.deletedAt IS NULL")
Optional<Quiz> findActiveById(@Param("quizId") UUID quizId);

// Staff: paginated, era filter on historicalContext, show non-deleted only
@Query("""
    SELECT q FROM Quiz q JOIN q.historicalContext hc
    WHERE (:search IS NULL OR q.title ILIKE CONCAT('%', :search, '%'))
    AND (:era IS NULL OR hc.era = :era)
    AND q.deletedAt IS NULL
""")
Page<Quiz> findAllForStaff(@Param("search") String search,
                            @Param("era") EventEra era,
                            Pageable pageable);

// Duplicate title check (new quiz)
boolean existsByTitleIgnoreCase(String title);

// Duplicate title check (update — excluding self)
boolean existsByTitleIgnoreCaseAndQuizIdNot(String title, UUID quizId);
```

---

#### [MODIFY] `QuizSessionRepository.java`

```java
// Find by session ID
Optional<QuizSession> findBySessionId(UUID sessionId);

// History: completed sessions for a user, newest first
@Query("""
    SELECT s FROM QuizSession s
    WHERE s.user.uid = :uid
    AND s.endTime IS NOT NULL AND s.deletedAt IS NULL
    ORDER BY s.endTime DESC
""")
Page<QuizSession> findCompletedByUserUid(@Param("uid") UUID uid, Pageable pageable);

// playCount — per user per quiz (Customer)
@Query("""
    SELECT COUNT(s) FROM QuizSession s
    WHERE s.quiz.quizId = :quizId AND s.user.uid = :uid
    AND s.endTime IS NOT NULL AND s.deletedAt IS NULL
""")
long countCompletedByQuizAndUser(@Param("quizId") UUID quizId, @Param("uid") UUID uid);

// playCount — all users for a quiz (Staff)
@Query("""
    SELECT COUNT(s) FROM QuizSession s
    WHERE s.quiz.quizId = :quizId
    AND s.endTime IS NOT NULL AND s.deletedAt IS NULL
""")
long countCompletedByQuiz(@Param("quizId") UUID quizId);
```

---

#### [MODIFY] `QuestionRepository.java`

```java
// Active questions for a quiz, ordered by insertion (no orderIndex column)
@Query("""
    SELECT q FROM Question q
    WHERE q.quiz.quizId = :quizId AND q.deletedAt IS NULL
    ORDER BY q.createdAt ASC
""")
List<Question> findActiveByQuizId(@Param("quizId") UUID quizId);
```

---

### Step 3 — Service Interface

#### [MODIFY] `QuizService.java`

```java
// Customer
List<QuizCustomerResponse> getAllQuizzesForCustomer(String search, UUID userId);
QuizCustomerResponse getQuizByIdForCustomer(String quizId, UUID userId);
QuizStartResponse startQuiz(String quizId, UUID userId, Integer limitedTime);
QuizSubmitResponse submitQuiz(QuizSubmitRequest request, UUID userId);
PaginatedResponse<QuizHistoryResponse> getQuizHistory(UUID userId, Pageable pageable);

// Staff
PaginatedResponse<QuizStaffResponse> getAllQuizzesForStaff(String search, String era, Pageable pageable);
QuizStaffResponse getQuizByIdForStaff(String quizId);
QuizStaffResponse createQuiz(CreateQuizRequest request, UUID userId);
QuizStaffResponse updateQuiz(String quizId, UpdateQuizRequest request);
void deleteQuiz(String quizId);
void softDeleteQuiz(String quizId);
void toggleActiveQuiz(String quizId);
QuestionResponse addQuestion(String quizId, QuestionRequest request);
void updateQuestion(String quizId, String questionId, QuestionRequest request);
void deleteQuestion(String quizId, String questionId);
```

---

### Step 4 — Service Implementation

#### [MODIFY] `QuizServiceImpl.java`

**Dependencies (injected via `@RequiredArgsConstructor`):**
```java
QuizRepository quizRepository
QuestionRepository questionRepository
QuizSessionRepository quizSessionRepository
UserRepository userRepository
HistoricalContextRepository historicalContextRepository
ObjectMapper objectMapper
```

---

**`getAllQuizzesForCustomer(search, userId)`** `@Transactional(readOnly=true)`
1. `quizRepository.findAllActiveForCustomer(normalize(search))`
2. For each quiz: `quizSessionRepository.countCompletedByQuizAndUser(quiz.getQuizId(), userId)` (pass 0 if userId is null)
3. Map each quiz → `QuizCustomerResponse` using `mapToCustomerResponse(quiz, playCount)`

---

**`getQuizByIdForCustomer(quizId, userId)`** `@Transactional(readOnly=true)`
1. Parse UUID, throw `InvalidRequestException` on bad format
2. `quizRepository.findActiveById(uuid)` → throw `ResourceNotFoundException` if empty
3. Count: `quizSessionRepository.countCompletedByQuizAndUser(quizId, userId)`
4. Return `mapToCustomerResponse(quiz, playCount)`

---

**`startQuiz(quizId, userId, limitedTime)`** `@Transactional`
1. `quizRepository.findActiveById(uuid)` → 404 if not found
2. `userRepository.findById(userId)` → 404 if not found
3. Build `QuizSession`:
   ```java
   QuizSession.builder()
     .quiz(quiz).user(user)
     .startTime(LocalDateTime.now())
     .limitedTime(limitedTime) // null = no limit
     .build()
   ```
4. `quizSessionRepository.save(session)`
5. `questionRepository.findActiveByQuizId(quizId)` → list of questions
6. Return `QuizStartResponse { sessionId, quizId, title, questions[] }`

---

**`submitQuiz(request, userId)`** `@Transactional`
1. Parse sessionId UUID → `quizSessionRepository.findBySessionId(uuid)` → 404 if not found
2. **Ownership**: `session.getUser().getUid().equals(userId)` → throw 400 if mismatch
3. **Already submitted**: `if (session.getEndTime() != null)` → throw `InvalidRequestException("Quiz already submitted")`
4. **Time limit check** (if `session.getLimitedTime() != null`):
   ```java
   LocalDateTime deadline = session.getStartTime().plusSeconds(session.getLimitedTime());
   if (LocalDateTime.now().isAfter(deadline)) throw InvalidRequestException("Time limit expired")
   ```
5. Load ordered questions: `questionRepository.findActiveByQuizId(quiz.getQuizId())`
6. **Score + index tracking** — iterate by index position:
   ```java
   List<Integer> correctAnswers = new ArrayList<>();
   List<Integer> wrongAnswers = new ArrayList<>();
   int score = 0;
   for (int i = 0; i < questions.size(); i++) {
       Question q = questions.get(i);
       // find matching answer from request.getAnswers() by questionId
       Optional<AnswerDetailRequest> ans = request.getAnswers().stream()
           .filter(a -> a.getQuestionId().equals(q.getQuestionId().toString()))
           .findFirst();
       if (ans.isPresent()) {
           boolean correct = q.getCorrectAnswer().equals(ans.get().getSelectedAnswer());
           if (correct) { score++; correctAnswers.add(i); }
           else { wrongAnswers.add(i); }
       }
   }
   ```
7. **Save QuizAnswerDetail** for each answer in `request.getAnswers()`
8. **Update session**: `session.setEndTime(LocalDateTime.now())`, `session.setScore((float) score)`
9. `quizSessionRepository.save(session)`
10. Return:
    ```java
    QuizSubmitResponse {
      resultId = session.getSessionId().toString(),
      score, totalQuestions = questions.size(),
      percentage = (double) score / questions.size() * 100,
      correctAnswers, wrongAnswers
    }
    ```

---

**`getQuizHistory(userId, pageable)`** `@Transactional(readOnly=true)`
1. `quizSessionRepository.findCompletedByUserUid(userId, pageable)`
2. Map each session → `QuizHistoryResponse`:
   - `score = session.getScore() != null ? session.getScore().intValue() : 0`
   - `totalQuestions = questionRepository.findActiveByQuizId(quiz.getQuizId()).size()`
   - `percentage = totalQuestions > 0 ? (double) score / totalQuestions * 100 : 0`
   - `completedAt = session.getEndTime().toString()` (ISO8601)
3. **Pagination**: `currentPage = page.getNumber()` (0-indexed per contract)

---

**`getAllQuizzesForStaff(search, era, pageable)`** `@Transactional(readOnly=true)`
1. Parse `era` string → `EventEra` enum (throw 400 if invalid, pass null if blank/null)
2. `quizRepository.findAllForStaff(normalize(search), eraEnum, pageable)`
3. Map each → `mapToStaffResponse(quiz)` (playCount = `countCompletedByQuiz`)
4. `currentPage = page.getNumber()` (0-indexed)

---

**`getQuizByIdForStaff(quizId)`** `@Transactional(readOnly=true)`
1. `quizRepository.findById(uuid)` → 404 if not found (staff can see quiz even if soft-deleted)
2. Return `mapToStaffResponse(quiz)`

---

**`createQuiz(request, userId)`** `@Transactional`
1. Duplicate check: `quizRepository.existsByTitleIgnoreCase(request.getTitle())` → 409
2. `userRepository.findById(userId)` → 404
3. `historicalContextRepository.findById(UUID.fromString(request.getContextId()))` → 404
4. Parse `QuizLevel` from `request.getLevel()` → 400 if invalid
5. Build and save `Quiz { title, level, historicalContext, createdBy, isActive=true }`
6. For each `QuestionRequest` in `request.getQuestions()`: build and save `Question`
7. Return `mapToStaffResponse(quiz)`

---

**`updateQuiz(quizId, request)`** `@Transactional`
1. `quizRepository.findById(uuid)` → 404
2. If `request.getTitle() != null` and differs: check duplicate → 409, then `quiz.setTitle(title)`
3. If `request.getContextId() != null`: find context → 404, `quiz.setHistoricalContext(context)`
4. If `request.getLevel() != null`: parse → 400 if invalid, `quiz.setLevel(level)`
5. Save and return `mapToStaffResponse(quiz)`

---

**`deleteQuiz(quizId)`** `@Transactional`
1. `quizRepository.findById(uuid)` → 404
2. `quizRepository.delete(quiz)`

---

**`softDeleteQuiz(quizId)`** `@Transactional`
1. `quizRepository.findById(uuid)` → 404
2. `quiz.setDeletedAt(LocalDateTime.now())`
3. Save

---

**`toggleActiveQuiz(quizId)`** `@Transactional`
1. `quizRepository.findById(uuid)` → 404
2. `quiz.setIsActive(!quiz.getIsActive())`
3. Save

---

**`addQuestion(quizId, request)`** `@Transactional`
1. `quizRepository.findById(uuid)` → 404
2. Build `Question { content, options=serialize(request.getOptions()), correctAnswer, explanation, quiz }`
3. `questionRepository.save(question)`
4. Return `mapQuestionToResponse(question)`

---

**`updateQuestion(quizId, questionId, request)`** `@Transactional`
1. `quizRepository.findById(quizUuid)` → 404 (validate quiz exists)
2. `questionRepository.findById(questionUuid)` → 404
3. Verify `question.getQuiz().getQuizId().equals(quizUuid)` → 400 mismatch
4. Update only non-null fields: `content`, `options` (serialize), `correctAnswer`, `explanation`
5. Save

---

**`deleteQuestion(quizId, questionId)`** `@Transactional`
1. `quizRepository.findById(quizUuid)` → 404
2. `questionRepository.findById(questionUuid)` → 404
3. Verify question belongs to quiz
4. `questionRepository.delete(question)`

---

**Private helpers:**

```java
// Serialize List<String> → JSON string stored in Question.options
private String serializeOptions(List<String> options)

// Deserialize JSON string → List<String> for response
private List<String> deserializeOptions(String options)

// Map Question entity → QuestionResponse DTO
private QuestionResponse mapQuestionToResponse(Question q)

// Map Quiz entity → QuizCustomerResponse DTO (playCount passed in)
private QuizCustomerResponse mapToCustomerResponse(Quiz quiz, long playCount)

// Map Quiz entity → QuizStaffResponse DTO (queries playCount internally)
private QuizStaffResponse mapToStaffResponse(Quiz quiz)

// Map Page<T> → PaginatedResponse<T> (0-indexed currentPage)
private <T> PaginatedResponse<T> toPaginatedResponse(Page<T> page)

// Normalize search: null/blank → null, else lowercase
private String normalize(String s)

// Parse UUID from string, throw InvalidRequestException on failure
private UUID parseUuid(String id, String fieldName)
```

---

### Step 5 — Controllers

---

#### [MODIFY] `QuizController.java`
```
@RestController
@RequestMapping("/api/v1/quizzes")
@RequiredArgsConstructor @Slf4j
@Tag(name = "Quizzes (Customer)")
```

| Method | Endpoint | Auth | Params / Body |
|---|---|---|---|
| GET | `/` | Public | `@RequestParam(required=false) String search` |
| GET | `/{quizId}` | Public | Path var |
| POST | `/{quizId}/start` | `@PreAuthorize("hasRole('CUSTOMER')")` | Path + `@RequestParam(required=false) Integer limitedTime` |
| POST | `/submit` | `@PreAuthorize("hasRole('CUSTOMER')")` | `@Valid @RequestBody QuizSubmitRequest` |
| GET | `/results/me` | `@PreAuthorize("hasRole('CUSTOMER')")` | `@RequestParam(defaultValue="0") int page`, `@RequestParam(defaultValue="10") int size` |

**Notes for customer controllers:**
- For `GET /quizzes` and `GET /quizzes/:quizId`: extract userId via `SecurityUtils.getUserId()` wrapped in try-catch (public endpoint — user may not be logged in). Pass `null` UUID if unauthenticated → service returns `playCount = 0`.
- `GET /quizzes` returns `ResponseEntity<ApiResponse<List<QuizCustomerResponse>>>` (array, not paginated).
- `POST /quizzes/:quizId/start` returns 200 (not 201).

---

#### [MODIFY] `StaffQuizController.java`
```
@RestController
@RequestMapping("/api/v1/staff/quizzes")
@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor @Slf4j
@Tag(name = "Quizzes (Staff)")
```

| Method | Endpoint | Returns | Notes |
|---|---|---|---|
| GET | `/` | `PaginatedResponse<QuizStaffResponse>` | `?search`, `?era`, `?page=0`, `?size=10` |
| GET | `/{quizId}` | `QuizStaffResponse` | |
| POST | `/` | `QuizStaffResponse` | `@Valid @RequestBody CreateQuizRequest` |
| PUT | `/{quizId}` | `QuizStaffResponse` | `@Valid @RequestBody UpdateQuizRequest` |
| DELETE | `/{quizId}` | `ApiResponse<Void>` 200 | |
| PATCH | `/{quizId}/soft-delete` | `ApiResponse<Void>` 200 | |
| PATCH | `/{quizId}/toggle-active` | `ApiResponse<Void>` 200 | |
| POST | `/{quizId}/questions` | `QuestionResponse` | `@Valid @RequestBody QuestionRequest` |
| PUT | `/{quizId}/questions/{questionId}` | `ApiResponse<Void>` 200 | `@RequestBody QuestionRequest` (no @Valid — all optional) |
| DELETE | `/{quizId}/questions/{questionId}` | `ApiResponse<Void>` 200 | |

**Notes:**
- Staff controller does **NOT** extract `userId`/`userRole` from `SecurityUtils` — role is enforced by `@PreAuthorize` at class level. Service methods don't take userId/userRole for ownership checks.
- All responses wrapped in `ApiResponse.success(data, message)`.

---

## Verification

### Build
```bash
mvn clean compile
```

### API Test Checklist (Swagger)

| # | Test | Expected |
|---|---|---|
| 1 | `GET /quizzes` (unauthenticated) | Array `[{quizId, title, level, era, playCount:0, contextTitle}]` |
| 2 | `GET /quizzes` (logged in as CUSTOMER) | Same, `playCount` reflects user's completed sessions |
| 3 | `GET /quizzes/:id` | Single `{quizId, title, level, era, playCount, contextTitle}` |
| 4 | `POST /quizzes/:id/start` (no param) | `{sessionId, quizId, title, questions[]}` — `limitedTime` NOT in response |
| 5 | `POST /quizzes/:id/start?limitedTime=10` | Same response; DB row has `limited_time=10` |
| 6 | `POST /quizzes/submit` within 10s | `{resultId, score, totalQuestions, percentage, correctAnswers[], wrongAnswers[]}` — indices, not selected values |
| 7 | `POST /quizzes/submit` after 10s elapsed | 400 "Time limit expired" |
| 8 | `POST /quizzes/submit` again same session | 400 "Quiz already submitted" |
| 9 | `GET /quizzes/results/me?page=0&size=10` | `{content:[{sessionId, quizId, quizTitle, score, totalQuestions, percentage, completedAt}], currentPage:0, ...}` |
| 10 | `GET /staff/quizzes?era=ANCIENT&page=0&size=5` | Paginated, each item = `ContentAdminQuizSet`, `playCount` = total all-user completions |
| 11 | `GET /staff/quizzes/:id` | Full `ContentAdminQuizSet` with `questions[]` |
| 12 | `POST /staff/quizzes` | Returns created quiz as `ContentAdminQuizSet` |
| 13 | `PUT /staff/quizzes/:id` | Returns updated quiz |
| 14 | `DELETE /staff/quizzes/:id` | 200 success |
| 15 | `PATCH /staff/quizzes/:id/soft-delete` | 200; quiz.deletedAt is now set |
| 16 | `PATCH /staff/quizzes/:id/toggle-active` | 200; quiz.isActive flipped |
| 17 | `POST /staff/quizzes/:id/questions` | Returns `{questionId, content, options, correctAnswer, explanation}` |
| 18 | `PUT /staff/quizzes/:id/questions/:qid` | 200 success |
| 19 | `DELETE /staff/quizzes/:id/questions/:qid` | 200 success |
