# Quiz Unification — Execution Log

**Plan:** `quiz-mc-unification-and-null-fixes.md`  
**Branch:** `Bao_quizUpgrade`  
**Bắt đầu:** 2026-10-16  
**Hoàn thành:** 2026-10-16

---

## Trạng thái

| Task | Mô tả | Trạng thái |
|------|-------|-----------|
| T1 | V35 Migration | ✅ |
| T2 | Fix 3 null/FK bugs | ✅ |
| T3 | Entity + DTO cleanup | ✅ |
| T4 | Service refactor | ✅ |
| T5 | Test update | ✅ |

---

## Chi tiết thực hiện

### T1 — V35 Flyway Migration

**File tạo mới:** `src/main/resources/db/migration/V35__cleanup_legacy_mc_columns.sql`

Nội dung:
```sql
ALTER TABLE historical_schema.question
    DROP COLUMN IF EXISTS options,
    DROP COLUMN IF EXISTS correct_answer;

ALTER TABLE historical_schema.quiz_answer_detail
    DROP COLUMN IF EXISTS selected_option;

ALTER TABLE historical_schema.question_report
    ALTER COLUMN quiz_id DROP NOT NULL;
```

**Thời gian:** ~5 phút  
**Ghi chú:** V34 đã tạo `question_data TEXT` và `answer_data TEXT` đúng kiểu. V35 chỉ dọn dẹp các cột legacy của MC.

---

### T2 — Fix 3 null/FK bugs

**Files thay đổi:**

1. `entity/quiz/QuestionReport.java`
   - `@JoinColumn(name = "quiz_id", nullable = false)` → `nullable = true`
   - Lý do: bank questions không có quiz, tạo report từ bank question sẽ NPE/FK violation

2. `service/quiz/QuizServiceImpl.java` — `updateQuestion` (line ~474)
   - Thêm guard: `question.getQuiz() == null ||` trước `.getQuiz().getQuizId().equals(quizUuid)`
   - Lý do: bank questions có `quiz == null`, gọi `.getQuiz().getQuizId()` sẽ NPE

3. `service/quiz/QuizServiceImpl.java` — `deleteQuestion` (line ~505)
   - Cùng guard như updateQuestion

**Thời gian:** ~10 phút

---

### T3 — Entity + DTO cleanup

**Files thay đổi:**

1. `entity/quiz/Question.java`
   - Xóa field `private String options` (`@Column(name = "options")`)
   - Xóa field `private Integer correctAnswer` (`@Column(name = "correct_answer")`)

2. `entity/quiz/QuizAnswerDetail.java`
   - Xóa field `private Integer selectedOption` (`@Column(name = "selected_option")`)
   - Xóa import `UpdateTimestamp` không dùng

3. `dto/quiz/QuestionRequest.java` — **Viết lại hoàn toàn**
   - Xóa: `options: List<String>`, `correctAnswer: Integer`, `@Size`
   - Giữ: `content` (@NotBlank), `explanation`, `questionType` (@Builder.Default MC), `questionData: JsonNode`

4. `dto/quiz/QuestionResponse.java` — **Viết lại hoàn toàn**
   - Xóa: `options: List<String>`, `correctAnswer: Integer`
   - Giữ: `questionId`, `content`, `questionType` (@Builder.Default MC), `questionData: JsonNode`, `explanation`

5. `dto/quiz/AnswerDetailRequest.java` — **Viết lại hoàn toàn**
   - Xóa: `selectedAnswer: Integer`
   - Giữ: `questionId` (@NotNull), `answerData: JsonNode`

6. `dto/quiz/QuizSessionDetailResponse.java` — **Viết lại `QuestionResultItem`**
   - Xóa: `options`, `correctAnswer`, `selectedAnswer`
   - Thêm: `questionType`, `questionData` (full reveal), `submittedAnswer`

7. `dto/quiz/QuestionBankResponse.java`
   - Xóa: `options: List<String>`, `correctAnswer: Integer`, import `java.util.List`

**Thời gian:** ~25 phút  
**Ghi chú:** Edit tool lần đầu với `QuizAnswerDetail.java` bị lỗi mismatch whitespace — phải Read file trước rồi mới edit được.

---

### T4 — Service refactor

**File thay đổi chính:** `service/quiz/QuizServiceImpl.java`

**Import changes:**
- Xóa: `import com.fasterxml.jackson.core.type.TypeReference` (không còn dùng)
- Thêm: `import com.fasterxml.jackson.databind.node.ObjectNode`
- Thêm: `MultipleChoiceAnswerData`, `MultipleChoiceQuestionData`, `QuestionOption`

**Các thay đổi theo thứ tự:**

| Sub-task | Mô tả | Dòng code |
|----------|-------|-----------|
| 4.1 | Xóa `serializeOptions()` và `deserializeOptions()` (20 dòng) | ~883–900 |
| 4.2 | Đơn giản hóa `buildQuestion()` — chỉ dùng `questionData`, không còn options/correctAnswer | ~727 |
| 4.3 | `updateQuestion()` — xóa options/correctAnswer update, thêm questionData/questionType | ~478–491 |
| 4.4 | `updateBankQuestion()` — xóa options/correctAnswer update | ~1024–1038 |
| 4.5 | `mapToQuestionResponse()` — xóa options/correctAnswer, dùng sanitize | ~800 |
| 4.6 | Thêm `mapToStaffQuestionDetail()` — full data không sanitize | mới |
| 4.7 | `mapToStaffResponse()` — dùng `mapToStaffQuestionDetail` thay `mapToQuestionResponse` | ~777 |
| 4.8 | `mapToBankResponse()` — xóa options/correctAnswer | ~838 |
| 4.9 | `sanitizeQuestionDataForLearner()` — thêm case MC: giữ options, strip correctOptionIds | ~813 |
| 4.10 | `gradeAnswer()` — thay `default` bằng explicit `MULTIPLE_CHOICE` case | ~924 |
| 4.11 | Thêm `gradeMultipleChoice()` — dùng `MultipleChoiceQuestionData`/`MultipleChoiceAnswerData` | mới |
| 4.12 | `submitQuiz()` — xóa `.selectedOption(ans.getSelectedAnswer())` từ builder | ~213 |
| 4.13 | `getSessionDetail()` — dùng shape mới: `questionType`, `questionData`, `submittedAnswer` | ~288 |
| 4.14 | `saveQuizGroup()` (CSV import) — build `MultipleChoiceQuestionData` từ CSV columns | ~683 |
| 4.15 | `getQuestionReports()` — guard null quiz (since quiz_id nullable) | ~1204 |

**Thời gian:** ~45 phút  
**Ghi chú:** Task này tốn nhiều thời gian nhất do phải đọc toàn bộ file (~1250 dòng) trước khi sửa từng phần. Compile check clean sau khi hoàn thành.

---

### T5 — Test update

**File thay đổi:** `src/test/java/com/historytalk/service/quiz/QuestionBankServiceImplTest.java`

**Import thêm:** `MultipleChoiceAnswerData`, `MultipleChoiceQuestionData`, `QuestionOption`

**Tests thêm mới (2 tests MC):**
- `submitQuiz_MC_correctAnswer_scoreOne` — chọn đúng option → score = 1
- `submitQuiz_MC_wrongAnswer_scoreZero` — chọn sai option → score = 0

**Kết quả:** `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` ✅

**Thời gian:** ~10 phút

---

## Tổng kết

| Metric | Giá trị |
|--------|---------|
| Files tạo mới | 3 (V35 migration, 2 docs) |
| Files thay đổi | 10 |
| Tests đã có (giữ pass) | 8 |
| Tests mới thêm | 2 |
| Tổng tests pass | 10/10 |
| Compile check | ✅ Clean |
| Task tốn thời gian nhất | T4 — Service refactor (~45 phút) |
| Lý do T4 chậm | File dài ~1250 dòng, nhiều sub-tasks liên quan nhau |

## Files đã thay đổi (tổng hợp)

### Tạo mới
- `src/main/resources/db/migration/V35__cleanup_legacy_mc_columns.sql`
- `history-talk-backend-Java/docs/quiz-mc-unification-and-null-fixes.md`
- `history-talk-backend-Java/docs/quiz-unification-execution-log.md` (file này)

### Thay đổi
- `entity/quiz/Question.java` — xóa options, correctAnswer fields
- `entity/quiz/QuizAnswerDetail.java` — xóa selectedOption field
- `entity/quiz/QuestionReport.java` — quiz nullable = true
- `dto/quiz/QuestionRequest.java` — rewritten
- `dto/quiz/QuestionResponse.java` — rewritten
- `dto/quiz/AnswerDetailRequest.java` — rewritten
- `dto/quiz/QuizSessionDetailResponse.java` — QuestionResultItem rewritten
- `dto/quiz/QuestionBankResponse.java` — xóa legacy fields
- `service/quiz/QuizServiceImpl.java` — major refactor (15 sub-tasks)
- `src/test/java/com/historytalk/service/quiz/QuestionBankServiceImplTest.java` — 2 tests mới
