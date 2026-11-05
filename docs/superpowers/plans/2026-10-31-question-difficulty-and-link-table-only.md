# Per-Question Difficulty & Link-Table-Only Quiz Membership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move difficulty (EASY/MEDIUM/HARD) from `quiz` to each `question`, and make `quiz_question` the only source of quiz ↔ question membership (stop reading/writing `question.quiz_id`, then drop the column).

**Architecture:** Two Flyway migrations: the first backfills every `question.quiz_id` into `quiz_question` and drops `question.quiz_id`; the second adds `question.difficulty` (backfilled from the owning quiz's level) and drops `quiz.level`. The Java side follows in the same order: all membership reads/writes go through `QuizQuestionRepository` (questions of a quiz = `quiz_question JOIN question` filtered by `quiz_id`), then difficulty becomes a `Question` field. The quiz itself carries no difficulty; every question returned — inside a quiz (start/staff detail) or from the question bank — carries its own `difficulty` for the FE to display, and the bank can filter by it. Soft-deleting a historical context soft-deletes its quizzes only, never questions.

**Tech Stack:** Spring Boot 3.2, Java 21, Spring Data JPA/Hibernate (`ddl-auto=none`), Flyway (`out-of-order=true`), PostgreSQL schema `historical_schema`, JUnit 5 + Mockito.

**Spec:** User request (2026-10-31): "sửa lại quiz thành độ khó cho từng question, và dừng hoàn toàn việc sử dụng quiz_id trong bảng question, giờ chỉ dùng bảng trung gian" + follow-up: "bỏ luôn quiz level, drop cột đó trong db; khi load quiz lên không load độ khó gì hết nữa; load câu hỏi của question bank thì có level độ khó của từng question và có thể filter theo độ khó; không đụng gì ngoài module quiz/question (ví dụ dashboard)." + follow-up 2: "historical context khi xóa thì bỏ các quiz liên quan, không xóa question nữa; khi load quiz thì trong đó có question — load độ khó của từng question luôn, quiz không còn độ khó nên trả độ khó từng câu hỏi về cho FE hiển thị."

`JAVA` below = `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java`. All Maven commands run from `JAVA`.

## Global Constraints

- Difficulty values: exactly `EASY`, `MEDIUM`, `HARD` (enum `QuestionDifficulty`, replaces `QuizLevel`).
- DB column: `historical_schema.question.difficulty VARCHAR(10) NOT NULL`, CHECK `IN ('EASY','MEDIUM','HARD')`.
- `quiz.level` and `question.quiz_id` are dropped. No Java code may reference either afterwards.
- Quiz-level responses (`QuizStaffResponse`, `QuizCustomerResponse`, top-level of `QuizStartResponse`) expose **no** difficulty/level field.
- Every question payload exposes its own `difficulty`: `QuestionResponse` (used for questions inside `QuizStartResponse` and `QuizStaffResponse.questions`) and `QuestionBankResponse`. `GET /staff/question-bank` accepts optional `difficulty` filter.
- Soft-deleting a historical context sets `deletedAt` on its quizzes only; questions are never soft-deleted by that cascade.
- Hard-deleting a quiz (`QuizServiceImpl.deleteQuiz`, System Trash `hardDeleteQuizzes`) removes only the quiz and its `quiz_question` rows; questions stay in the bank (no JPA cascade from `Quiz.questions`).
- `quiz_question` gets **no** `deleted_at` column. Whether a link is "live" is derived at read time from `quiz.deleted_at IS NULL`.
- `QuestionBankResponse.assignedToQuiz` is computed, never stored: `true` iff the question is linked to at least one quiz with `deleted_at IS NULL` (draft quizzes count). One batched query per bank page, no per-row query.
- **Do not edit files outside the quiz/question module**: no changes to `service/dashboard/*`, `dto/dashboard/*`, `repository/dashboard/*`, `service/user/*`, `service/assignment/*` or their tests. Single approved exception: the quiz cascade block in `HistoricalContextServiceImpl.softDeleteContext` (lines ~300-308) plus its test. Native SQL that lives in quiz-module repositories (`QuizRepository`, `QuizAnswerDetailRepository`) is fixed in place so it keeps running after the column drops.
- Migrations use the explicit `historical_schema.` prefix (same as `V34`) and timestamp versions `V20261031…`.
- Vietnamese error messages, matching existing style.
- Do not alter the time-limit check in `QuizServiceImpl.submitQuiz` (committed in `5cecfff`) or the batch-loading helpers merged from `main` (`mapToStaffPaginatedResponse`, `mapToHistoryPaginatedResponse`, `getQuizHistory(userId, quizId, pageable)`); only their repository queries change underneath.
- Keep the `:search = ''` guard in `QuestionRepository.findAllForBank` (merged from `main`, fixes PostgreSQL binding a null `:search` as bytea).
- Baseline before Task 1 (branch `Bao-mainBranch` @ `f92f2a2`): `mvn test` → 360 run, 14 failures, all outside quiz (`DocumentFileServiceImplTest` 3, `SupabaseDocumentStorageServiceTest` 8, `PasswordResetEmailServiceImplTest` 2, `UserServiceImplTest.updateMyProfileRejectsDeactivatedUser` 1). "Suite green" in this plan means: no failures beyond these 14.

## Review Focus

1. **Questions created after V34 exist only in `question.quiz_id`** (`createQuiz`, `addQuestion`, CSV import never wrote `quiz_question`). If migration 1 does not backfill them, those quizzes appear empty. → Task 1 migration step + manual DB check in Task 3.
2. **Callers outside the module still depend on quiz membership**: `quiz.getQuestions()` fallbacks in `StudentAssignmentServiceImpl` (~L210) and `AnalyticsDashboardServiceImpl` (~L603), and the batch query `QuestionRepository.countActiveByQuizIds` used by `UserServiceImpl` and `AnalyticsDashboardServiceImpl`. They must keep compiling and returning correct counts without edits. → `Quiz.questions` is remapped as a read-only `@ManyToMany` over `quiz_question`, and `findActiveByQuizIds` / `countActiveByQuizIds` are rewritten link-only with unchanged signatures (Task 1); whole suite must stay at baseline.
3. **Dashboard native queries reference `q.level` and `question.quiz_id`**; after the drops they would throw SQL errors. → Task 1 / Task 2 rewrite them inside the quiz repositories; dashboard Java untouched (top-quiz `level` becomes `NULL`).
4. **A shared bank question removed from one quiz must not disappear from the bank or other quizzes.** → `deleteQuestion(quizId, questionId)` becomes unlink-only (Task 1 test `deleteQuestion_unlinksWithoutDeletingQuestion`).
5. **Partial question updates without difficulty must not wipe it** (null = no change), but creation without difficulty must fail with a clear message. → Task 2 tests `updateQuestion_nullDifficultyKeepsExisting`, `createBankQuestion_missingDifficulty_rejected`.

6. **Deleting a historical context must not remove shared bank questions** from other quizzes or from the bank. → Task 1 removes the question cascade; test `softDeleteContext_softDeletesQuizzesButKeepsQuestions`.
7. **FE needs difficulty on every question it renders inside a quiz**, including the learner start screen. → Task 2 test `startQuiz_questionsCarryDifficulty` and staff-detail assertion.
8. **A question whose only quiz was soft-deleted must show `assignedToQuiz = false`**, and flip back to `true` when that quiz is restored from Trash. → Task 1 test `getQuestionBank_linkOnlyToDeletedQuiz_notAssigned` + DB check in Task 3.
9. **Permanently deleting a quiz must not delete its questions** (previously `cascade = ALL, orphanRemoval = true` did). → Task 1 test `deleteQuiz_keepsQuestionsInBank` + DB check in Task 3.

---

### Task 1: Quiz membership only through `quiz_question`

**Files:**
- Create: `JAVA/src/main/resources/db/migration/V20261031100000__question_membership_via_link_table_only.sql`
- Modify: `JAVA/src/main/java/com/historytalk/entity/quiz/Question.java` (remove `quiz` field, lines 34-36)
- Modify: `JAVA/src/main/java/com/historytalk/entity/quiz/Quiz.java` (`questions` mapping, lines 69-71)
- Modify: `JAVA/src/main/java/com/historytalk/repository/QuestionRepository.java` (`findActiveByQuizId`, `findActiveByQuizIds`, `countActiveByQuizIds`)
- Modify: `JAVA/src/main/java/com/historytalk/controller/quiz/StaffQuizController.java` (`@Operation` descriptions of delete quiz / delete question only)
- Modify: `JAVA/src/main/java/com/historytalk/repository/QuizQuestionRepository.java`
- Modify: `JAVA/src/main/java/com/historytalk/repository/QuizRepository.java` (`findTopQuizzesForDashboard` question-count subquery)
- Modify: `JAVA/src/main/java/com/historytalk/repository/QuizAnswerDetailRepository.java` (`findTopWrongQuestionsForDashboard` join)
- Modify: `JAVA/src/main/java/com/historytalk/service/quiz/QuizServiceImpl.java`
- Modify: `JAVA/src/main/java/com/historytalk/service/historicalContext/HistoricalContextServiceImpl.java` (quiz cascade in `softDeleteContext`, lines ~300-308 only)
- Test: `JAVA/src/test/java/com/historytalk/service/quiz/QuizServiceImplTest.java`, `QuestionBankServiceImplTest.java`, `QuizServiceImplImportTest.java`, `JAVA/src/test/java/com/historytalk/service/historicalContext/HistoricalContextServiceImplTest.java`

**Interfaces:**
- Produces:
  - `QuizQuestionRepository.Set<UUID> findActivelyLinkedQuestionIds(Collection<UUID> questionIds)` — ids among `questionIds` linked to at least one quiz with `deletedAt IS NULL`.
  - `QuizServiceImpl` private `QuestionBankResponse mapToBankResponse(Question q, boolean assignedToQuiz)` — replaces the one-arg version.
  - `QuizServiceImpl` private helper `void linkQuestion(Quiz quiz, Question question, int orderIndex)` — saves a `QuizQuestion`.
  - `QuizServiceImpl` private `Question buildQuestion(QuestionRequest request)` — **no quiz parameter**.
  - `QuestionRepository.findActiveByQuizId(UUID)` — same signature, now link-only, ordered by `orderIndex`.

- [ ] **Step 1: Update/write failing tests**

In `QuizServiceImplTest` add `@Mock private QuizQuestionRepository quizQuestionRepository;` and remove `.quiz(quiz)` from the `Question` builder (line ~104; the test must instead stub `quizQuestionRepository.existsByQuiz_QuizIdAndQuestion_QuestionId(quizId, questionId)` → `true`). In `QuestionBankServiceImplTest` remove `.quiz(...)` / `getQuiz()` usages (lines ~363, 371, 385, 398) — line 398 asserts the captured `QuizQuestion` link instead.

New tests in `QuizServiceImplTest`:

```java
@Test
void createQuiz_linksEachQuestionInOrder() {
    // stub user, context, quizRepository.save/findById, questionRepository.save returns question with random id
    quizService.createQuiz(requestWithTwoQuestions, userId);
    ArgumentCaptor<QuizQuestion> links = ArgumentCaptor.forClass(QuizQuestion.class);
    verify(quizQuestionRepository, times(2)).save(links.capture());
    assertThat(links.getAllValues()).extracting(QuizQuestion::getOrderIndex).containsExactly(0, 1);
    assertThat(links.getAllValues()).allSatisfy(l -> assertThat(l.getQuiz().getQuizId()).isEqualTo(quizId));
}

@Test
void addQuestion_linksAfterCurrentMaxOrder() {
    when(quizQuestionRepository.findMaxOrderIndex(quizId)).thenReturn(4);
    quizService.addQuestion(quizId.toString(), request);
    // captured QuizQuestion.orderIndex == 5
}

@Test
void updateQuestion_notLinkedToQuiz_rejected() {
    when(quizQuestionRepository.existsByQuiz_QuizIdAndQuestion_QuestionId(quizId, questionId)).thenReturn(false);
    assertThatThrownBy(() -> quizService.updateQuestion(quizId.toString(), questionId.toString(), request))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Câu hỏi không thuộc về quiz");
}

@Test
void deleteQuestion_unlinksWithoutDeletingQuestion() {
    when(quizQuestionRepository.findById(new QuizQuestionId(quizId, questionId))).thenReturn(Optional.of(link));
    quizService.deleteQuestion(quizId.toString(), questionId.toString());
    verify(quizQuestionRepository).delete(link);
    verify(questionRepository, never()).delete(any());
}

@Test
void deleteQuiz_keepsQuestionsInBank() {
    Quiz quiz = Quiz.builder().quizId(quizId).questions(List.of(question)).build();
    when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
    quizService.deleteQuiz(quizId.toString());
    verify(quizRepository).delete(quiz);
    verify(questionRepository, never()).delete(any());
    verify(questionRepository, never()).deleteAll(any());
}
```

In `QuestionBankServiceImplTest`:

```java
@Test
void getQuestionBank_assignedToQuizComesFromActiveLinks() {
    // findAllForBank returns Q1, Q2; findActivelyLinkedQuestionIds([Q1, Q2]) -> {Q1}
    // -> Q1.assignedToQuiz == true, Q2.assignedToQuiz == false
    // verify findActivelyLinkedQuestionIds called exactly once (batched, no per-row lookup)
}

@Test
void getQuestionBank_linkOnlyToDeletedQuiz_notAssigned() {
    // Q1 has a link row but its quiz is soft-deleted -> repository returns empty set
    // -> Q1.assignedToQuiz == false
}

@Test
void createBankQuestion_isNotAssigned() {
    // response.isAssignedToQuiz() == false; findActivelyLinkedQuestionIds never called
}
```

In `QuizServiceImplImportTest.importsAllThreeTypesInOneQuiz` add: `verify(quizQuestionRepository, times(3)).save(any(QuizQuestion.class))` with captured orderIndex `0, 1, 2`.

In `HistoricalContextServiceImplTest` (add whatever `@Mock`s `softDeleteContext` needs — document/chat-session repositories, map pin/focus services — following the existing `@Mock` style):

```java
@Test
void softDeleteContext_softDeletesQuizzesButKeepsQuestions() {
    Question question = Question.builder().questionId(UUID.randomUUID()).build();
    Quiz quiz = Quiz.builder().quizId(UUID.randomUUID()).questions(List.of(question)).build();
    // context (staff-owned, passes ownership check) with getQuizzes() = List.of(quiz)
    service.softDeleteContext(contextId.toString(), userId, "CONTENT_ADMIN");
    assertThat(quiz.getDeletedAt()).isNotNull();
    assertThat(question.getDeletedAt()).isNull();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest="QuizServiceImplTest,QuestionBankServiceImplTest,QuizServiceImplImportTest,HistoricalContextServiceImplTest"`
Expected: compilation errors / failures (`findActivelyLinkedQuestionIds` undefined, no link saves).

- [ ] **Step 3: Write migration `V20261031100000__question_membership_via_link_table_only.sql`**

```sql
-- Link every question still attached only through question.quiz_id (created after V34)
INSERT INTO historical_schema.quiz_question (quiz_id, question_id, order_index, points, created_at)
SELECT q.quiz_id,
       q.question_id,
       COALESCE((SELECT MAX(l.order_index) FROM historical_schema.quiz_question l WHERE l.quiz_id = q.quiz_id), -1)
           + ROW_NUMBER() OVER (PARTITION BY q.quiz_id ORDER BY q.created_at, q.question_id),
       1,
       COALESCE(q.created_at, CURRENT_TIMESTAMP)
FROM historical_schema.question q
WHERE q.quiz_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM historical_schema.quiz_question l
                  WHERE l.quiz_id = q.quiz_id AND l.question_id = q.question_id);

-- Membership now lives only in quiz_question (FK constraint is dropped with the column)
ALTER TABLE historical_schema.question DROP COLUMN IF EXISTS quiz_id;
```

- [ ] **Step 4: Entities**

- `Question`: delete the `quiz` field and its `@ManyToOne/@JoinColumn`.
- `Quiz.questions`: replace the `@OneToMany(mappedBy = "quiz", cascade = ALL, orphanRemoval = true)` with a read-only view so outside callers keep working:

```java
/** Read-only view over quiz_question; write links via QuizQuestionRepository. */
@Builder.Default
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(name = "quiz_question",
        joinColumns = @JoinColumn(name = "quiz_id"),
        inverseJoinColumns = @JoinColumn(name = "question_id"))
private List<Question> questions = new ArrayList<>();
```

- [ ] **Step 5: Repositories**

- `QuestionRepository.findActiveByQuizId`:
  ```
  SELECT q FROM QuizQuestion qq JOIN qq.question q
  WHERE qq.quiz.quizId = :quizId AND q.deletedAt IS NULL
  ORDER BY qq.orderIndex ASC, q.createdAt ASC
  ```
  Update the Javadoc (no more "direct FK column").
- `QuestionRepository.findActiveByQuizIds` (merged from `main`, returns `[quizId, Question]` rows; signature unchanged):
  ```
  SELECT qq.quiz.quizId, q FROM QuizQuestion qq JOIN qq.question q
  WHERE qq.quiz.quizId IN :quizIds AND q.deletedAt IS NULL
  ORDER BY qq.orderIndex ASC, q.createdAt ASC
  ```
- `QuestionRepository.countActiveByQuizIds` (returns `[quizId, count]`; signature unchanged):
  ```
  SELECT qq.quiz.quizId, COUNT(q) FROM QuizQuestion qq JOIN qq.question q
  WHERE qq.quiz.quizId IN :quizIds AND q.deletedAt IS NULL
  GROUP BY qq.quiz.quizId
  ```
  (`COUNT(DISTINCT …)` is no longer needed: the composite PK makes each (quiz, question) pair unique.)
- `QuizQuestionRepository`: add
  ```java
  @Query("""
          SELECT DISTINCT qq.question.questionId FROM QuizQuestion qq
          WHERE qq.question.questionId IN :questionIds
            AND qq.quiz.deletedAt IS NULL
          """)
  Set<UUID> findActivelyLinkedQuestionIds(@Param("questionIds") Collection<UUID> questionIds);
  ```
- `QuizRepository.findTopQuizzesForDashboard` subquery `qc` → count from `quiz_question qq JOIN question qu ON qu.question_id = qq.question_id WHERE qu.deleted_at IS NULL GROUP BY qq.quiz_id`.
- `QuizAnswerDetailRepository.findTopWrongQuestionsForDashboard` → replace `JOIN quiz q ON q.quiz_id = question.quiz_id` with `JOIN quiz q ON q.quiz_id = qs.quiz_id` (move the `quiz_session qs` join above it). Answers are attributed to the quiz they were played in.

- [ ] **Step 6: `QuizServiceImpl`**

- `buildQuestion(QuestionRequest request)` — drop the `quiz` param and `.quiz(quiz)`.
- `linkQuestion(Quiz quiz, Question question, int orderIndex)` — `quizQuestionRepository.save(QuizQuestion.builder()...)`.
- `createQuiz` and `saveQuizGroup`: save each question, then `linkQuestion(quiz, saved, i)` with `i` = 0-based position. Remove the "reload to get questions populated" `findById` (mapping reads via `findActiveByQuizId` anyway) — keep the reload only if a test needs `createdAt`; otherwise map the saved quiz.
- `addQuestion`: save, then `linkQuestion(quiz, saved, quizQuestionRepository.findMaxOrderIndex(quizUuid) + 1)`.
- `updateQuestion`: replace `question.getQuiz()` check with `!quizQuestionRepository.existsByQuiz_QuizIdAndQuestion_QuestionId(quizUuid, questionUuid)` → same `InvalidRequestException("Câu hỏi không thuộc về quiz: " + quizId)`.
- `deleteQuestion`: delegate to `unlinkQuestionFromQuiz(quizId, questionId)` (question stays in the bank).
- `createBankQuestion`: `buildQuestion(request)`.
- `mapToBankResponse(Question q, boolean assignedToQuiz)`; callers:
  - `getQuestionBank`: if the page is empty skip the lookup; otherwise one call `findActivelyLinkedQuestionIds(<page question ids>)`, then `page.map(q -> mapToBankResponse(q, linked.contains(q.getQuestionId())))`.
  - `createBankQuestion`: `mapToBankResponse(saved, false)` (a new question has no links).
  - `updateBankQuestion`: `findActivelyLinkedQuestionIds(List.of(id)).contains(id)`.
- `deleteQuiz` stays `quizRepository.delete(quiz)`; with the cascade gone (Step 4) this removes only the quiz and its link rows (DB `ON DELETE CASCADE` on `quiz_question`).
- Update the import Javadoc mentioning the direct FK if any.

`StaffQuizController` `@Operation` descriptions: delete quiz → `"Permanently delete a quiz. Its questions stay in the question bank."`; delete question → `"Remove a question from this quiz. The question stays in the question bank."`

`HistoricalContextServiceImpl.softDeleteContext`: in the "Cascade to Quizzes" block keep `quiz.setDeletedAt(now)` and delete the `quiz.getQuestions().forEach(q -> q.setDeletedAt(now))` branch. Replace the comment with: `// Questions live in the bank and may be shared by other quizzes, so only the quiz is soft-deleted`. Touch nothing else in that file.

- [ ] **Step 7: Run tests to verify they pass, plus full compile**

Run: `mvn -q test`
Expected: BUILD SUCCESS (whole suite, so the untouched outside-module tests also pass).

Run: `grep -rn "getQuiz()\|\.quiz(" src/main/java/com/historytalk/service/quiz src/main/java/com/historytalk/entity/quiz/Question.java` — only `QuizSession`/`QuizQuestion` usages remain.

- [ ] **Step 8: Commit**

```bash
git add <files above>
git commit -m "refactor(java): use quiz_question as the only quiz-question membership and drop question.quiz_id"
```

---

### Task 2: Difficulty per question, drop `quiz.level`

**Files:**
- Create: `JAVA/src/main/resources/db/migration/V20261031100100__question_difficulty_drop_quiz_level.sql`
- Rename: `JAVA/src/main/java/com/historytalk/entity/enums/QuizLevel.java` → `QuestionDifficulty.java` (same values)
- Modify: `entity/quiz/Question.java`, `entity/quiz/Quiz.java` (remove `level`)
- Modify: `dto/quiz/CreateQuizRequest.java`, `UpdateQuizRequest.java`, `QuizStaffResponse.java`, `QuizCustomerResponse.java` (remove `level`)
- Modify: `dto/quiz/QuestionRequest.java`, `dto/quiz/QuestionResponse.java`, `dto/quiz/QuestionBankResponse.java` (add `difficulty` to each)
- Modify: `repository/QuestionRepository.java` (`findAllForBank` filter), `repository/QuizRepository.java` (dashboard query `level`)
- Modify: `service/quiz/QuizService.java`, `service/quiz/QuizServiceImpl.java`, `controller/quiz/StaffQuestionBankController.java`
- Modify: `controller/quiz/StaffQuizController.java` (`@Operation` text only: create quiz, update quiz metadata, CSV import description — drop "level" as a quiz attribute; import text says `level` is each question's difficulty)
- Modify: `service/quiz/csv/QuizCsvRowParser.java`
- Test: `QuizServiceImplTest.java`, `QuestionBankServiceImplTest.java`, `QuizServiceImplImportTest.java`, `csv/QuizCsvRowParserTest.java`

**Interfaces:**
- Consumes: `buildQuestion(QuestionRequest)`, `linkQuestion(...)` from Task 1.
- Produces:
  - `enum QuestionDifficulty { EASY, MEDIUM, HARD }`
  - `Question.difficulty : QuestionDifficulty` (`@Enumerated(STRING) @Column(name = "difficulty", length = 10, nullable = false)`)
  - `QuestionRequest.difficulty : QuestionDifficulty` (no `@NotNull`; null = "no change" on updates)
  - `QuestionResponse.difficulty : QuestionDifficulty` (learner and staff paths alike — difficulty is not an answer key)
  - `QuestionBankResponse.difficulty : QuestionDifficulty`
  - `QuestionRepository.findAllForBank(QuestionType questionType, QuestionDifficulty difficulty, String search, Pageable pageable)`
  - `QuizService.getQuestionBank(String questionType, String difficulty, String search, Pageable pageable)`
  - Controller: `@RequestParam(required = false) String difficulty` on `GET /staff/question-bank`

- [ ] **Step 1: Write failing tests**

`QuizCsvRowParserTest`:
```java
@Test void parsesLevelIntoQuestionDifficulty()   // level "hard" -> QuestionDifficulty.HARD
@Test void blankLevelIsRejectedWithLineNumber()  // message contains "Dòng 2" (existing lineLabel format) and "level"
@Test void invalidLevelIsRejectedWithLineNumber()// level "VERY_HARD" -> message contains "EASY, MEDIUM, HARD"
```

`QuizServiceImplImportTest`:
- Replace `mixedLevelInOneQuizIsRejected` with `mixedLevelsInOneQuizImportWithPerQuestionDifficulty`: rows EASY, HARD under one title → success 1, captured questions' `getDifficulty()` = `EASY, HARD`.
- `legacyCsvStillImports`: replace `quizCaptor.getValue().getLevel()` assertion with captured question `getDifficulty() == QuestionDifficulty.MEDIUM`.
- `invalidLevelInLaterRowIsRejected` stays (now raised by the row parser).

`QuestionBankServiceImplTest`:
```java
@Test void getQuestionBank_passesDifficultyFilter()       // "hard" -> findAllForBank(null, HARD, null, pageable)
@Test void getQuestionBank_invalidDifficulty_rejected()   // "X" -> InvalidRequestException containing "EASY, MEDIUM, HARD"
@Test void getQuestionBank_responseIncludesDifficulty()   // question.difficulty MEDIUM -> response.getDifficulty() == MEDIUM
@Test void createBankQuestion_missingDifficulty_rejected()// -> InvalidRequestException "Yêu cầu độ khó (difficulty)"
@Test void updateBankQuestion_appliesDifficulty()
```

`QuizServiceImplTest`:
```java
@Test void updateQuestion_nullDifficultyKeepsExisting()   // existing HARD, request.difficulty null -> saved HARD
@Test void quizStaffResponseJsonHasNoLevel()               // serialize QuizStaffResponse; JSON has no "level" key
@Test void startQuiz_questionsCarryDifficulty()            // findActiveByQuizId returns EASY + HARD questions ->
                                                           // response.getQuestions() difficulties == [EASY, HARD]
@Test void staffQuizDetail_questionsCarryDifficulty()      // getQuizByIdForStaff (or createQuiz response) ->
                                                           // questions[i].getDifficulty() matches the entity
```
Also remove `.level(QuizLevel.EASY)` from the existing builder (line ~164) and the `QuizLevel` imports.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest="QuizServiceImplTest,QuestionBankServiceImplTest,QuizServiceImplImportTest,QuizCsvRowParserTest"`
Expected: compilation errors (`QuestionDifficulty` undefined).

- [ ] **Step 3: Write migration `V20261031100100__question_difficulty_drop_quiz_level.sql`**

```sql
ALTER TABLE historical_schema.question ADD COLUMN IF NOT EXISTS difficulty VARCHAR(10);

-- Inherit the level of the earliest-linked quiz
UPDATE historical_schema.question q
SET difficulty = (SELECT z.level
                  FROM historical_schema.quiz_question l
                  JOIN historical_schema.quiz z ON z.quiz_id = l.quiz_id
                  WHERE l.question_id = q.question_id
                  ORDER BY l.created_at, z.quiz_id
                  LIMIT 1)
WHERE q.difficulty IS NULL;

-- Bank questions never linked to a quiz
UPDATE historical_schema.question SET difficulty = 'MEDIUM' WHERE difficulty IS NULL;

ALTER TABLE historical_schema.question ALTER COLUMN difficulty SET NOT NULL;
ALTER TABLE historical_schema.question DROP CONSTRAINT IF EXISTS chk_question_difficulty;
ALTER TABLE historical_schema.question
    ADD CONSTRAINT chk_question_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'));
CREATE INDEX IF NOT EXISTS idx_question_difficulty ON historical_schema.question (difficulty);

ALTER TABLE historical_schema.quiz DROP COLUMN IF EXISTS level;
```

- [ ] **Step 4: Entities, enum, DTOs**

- `git mv` `QuizLevel.java` → `QuestionDifficulty.java`, rename the enum.
- `Question`: add `difficulty` per Interfaces (no builder default).
- `Quiz`: delete `level` field and `QuizLevel` import.
- Remove `level` from `CreateQuizRequest`, `UpdateQuizRequest`, `QuizStaffResponse`, `QuizCustomerResponse` (and their Javadoc shape comments: `{ title, contextId, level, questions[] }` → drop `level`).
- `QuestionRequest`: add `private QuestionDifficulty difficulty;` with a one-line comment "Required on create; null = no change on update."
- `QuestionResponse` and `QuestionBankResponse`: add `private QuestionDifficulty difficulty;` (in `QuestionResponse`, place it after `questionType`).

- [ ] **Step 5: Repositories**

- `QuestionRepository.findAllForBank`: keep the existing `:search = ''` guard; add `AND (:difficulty IS NULL OR q.difficulty = :difficulty)` and `@Param("difficulty") QuestionDifficulty difficulty` (param order per Interfaces).
- `QuizRepository.findTopQuizzesForDashboard`: replace `q.level AS level` with `CAST(NULL AS varchar) AS level` and remove `q.level` from `GROUP BY`. `DashboardTopQuizProjection` / `SystemDashboardServiceImpl` stay untouched.

- [ ] **Step 6: CSV row parser**

`QuizCsvRowParser.parse` sets `.difficulty(parseDifficulty(QuizCsvColumns.value(row, QuizCsvColumns.LEVEL)))`. `parseDifficulty(String raw)`: blank → `InvalidRequestException("level không được để trống")`; invalid → `"level không hợp lệ: '<raw>'. Giá trị hợp lệ: EASY, MEDIUM, HARD"`; case-insensitive. The existing catch already prefixes the line label. CSV column name stays `level` (backward-compatible files).

- [ ] **Step 7: Service + controller**

- `QuizServiceImpl`:
  - Remove `parseLevel`, `parseRowLevel`, the same-level loop in `saveQuizGroup`, and every `.level(...)` / `setLevel` / `getLevel` use. Update the `importQuizzesFromCsv` Javadoc: rows of one quiz must share `contextId`; `level` is each question's own difficulty.
  - `buildQuestion`: if `request.getDifficulty() == null` → `InvalidRequestException("Yêu cầu độ khó (difficulty)")`; set `.difficulty(...)`.
  - `updateQuestion` / `updateBankQuestion`: `if (request.getDifficulty() != null) question.setDifficulty(...)`.
  - `getQuestionBank(String questionType, String difficulty, String search, Pageable)`: add `parseDifficulty(String)` helper (blank → null; invalid → `InvalidRequestException("Giá trị độ khó (difficulty) không hợp lệ: " + v + ". Giá trị hợp lệ: EASY, MEDIUM, HARD")`).
  - `mapToQuestionResponse`, `mapToStaffQuestionDetail`, `mapToBankResponse`: `.difficulty(q.getDifficulty())`.
- `QuizService.getQuestionBank` signature per Interfaces.
- `StaffQuestionBankController.getQuestionBank`: add `@RequestParam(required = false) String difficulty`, include it in the log line, pass through.

- [ ] **Step 8: Run tests and leftover grep**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

Run (from `JAVA`): `grep -rn "QuizLevel\|getLevel()\|\.level(\|setLevel(" src/main/java/com/historytalk/{entity,dto/quiz,service/quiz,controller/quiz,repository/Quiz*.java,repository/Question*.java} src/test/java/com/historytalk/service/quiz`
Expected: no output.

- [ ] **Step 9: Commit**

```bash
git commit -m "feat(java): move difficulty from quiz to question and add question bank difficulty filter"
```

---

### Task 3: CSV template, docs, and end-to-end verification

**Files:**
- Modify: `JAVA/src/main/java/com/historytalk/service/quiz/csv/QuizCsvTemplateBuilder.java` (sample rows)
- Modify: `JAVA/src/test/java/com/historytalk/service/quiz/csv/QuizCsvTemplateBuilderTest.java` (if it asserts sample levels)
- Modify: `docs/services/history-talk-backend/quiz-csv-import-format.md` (lines 18, 42)

- [ ] **Step 1: Template sample rows**

Sample rows use different levels to show per-question difficulty: MULTIPLE_CHOICE `EASY`, FILL_IN_BLANK `MEDIUM`, MATCHING `HARD`. Adjust `QuizCsvTemplateBuilderTest` expectations if it pins the old `EASY` values.

- [ ] **Step 2: Docs**

- Line 18: `level` → "`EASY`, `MEDIUM` hoặc `HARD` — độ khó của **từng câu hỏi**."
- Line 42: "Mọi dòng cùng `title` phải có cùng `contextId`. Mỗi câu có thể có `level` khác nhau."
- Update the example CSV block so levels differ per row.

- [ ] **Step 3: Run tests**

Run: `mvn -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Apply migrations against the dev DB and smoke-test**

Before: record `SELECT COUNT(*) FROM historical_schema.question WHERE quiz_id IS NOT NULL;` and `SELECT COUNT(*) FROM historical_schema.quiz_question;`.

Run: `mvn spring-boot:run` — Flyway applies `V20261031100000` and `V20261031100100` without error.

Check in SQL:
- `quiz_question` count ≥ old count of `question.quiz_id IS NOT NULL` rows.
- `SELECT COUNT(*) FROM historical_schema.question WHERE difficulty IS NULL;` → `0`.
- `\d historical_schema.question` has no `quiz_id`; `\d historical_schema.quiz` has no `level`.

Swagger (`http://localhost:8080/Historical-tell/api/v1/swagger-ui`):
- `GET /staff/quizzes/{id}` and customer `GET /quizzes` — no quiz-level `level`; staff detail still lists the questions of a pre-existing quiz, each with `difficulty`.
- Start a quiz as a learner — every question in the response has `difficulty`, and no answer keys leak.
- Soft-delete a historical context whose quiz shares a bank question with another quiz → the quiz is gone, the question still appears in the bank and in the other quiz.
- Question Q linked only to quiz A: bank shows `assignedToQuiz: true` → soft-delete quiz A → `false` → restore quiz A via System Trash → `true` again, and quiz A still lists Q in its original order.
- Permanently delete a quiz via System Trash → `SELECT COUNT(*) FROM historical_schema.question WHERE question_id = '<Q>'` still `1`; its `quiz_question` rows are gone.
- `GET /staff/question-bank?difficulty=HARD` — only HARD, each item has `difficulty`.
- `POST /staff/quizzes` without `difficulty` on a question → 400 "Yêu cầu độ khó (difficulty)".
- Import the template CSV → quiz created with three questions, difficulties EASY/MEDIUM/HARD.
- System/analytics dashboard endpoints still return 200 (top quiz `level` = null).

- [ ] **Step 5: Commit**

```bash
git commit -m "docs: describe per-question difficulty in quiz CSV import"
```
