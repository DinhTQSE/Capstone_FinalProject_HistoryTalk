# Plan: Quiz Full Unification — MC + New Types, Clean Architecture

**Branch:** `Bao_quizUpgrade`  
**Base migration:** V34  
**Tiền đề:** FE migrate đồng bộ, data được import lại thủ công → không cần backward compat.

---

## Mục tiêu

Toàn bộ quiz (kể cả MC) follow một pipeline duy nhất:  
`questionData (JSON)` → grading → `answerData (JSON)` → session detail.

Xóa hoàn toàn các cột legacy: `question.options`, `question.correct_answer`, `quiz_answer_detail.selected_option`.  
Đồng thời fix 3 lỗi null/FK từ lần review trước.

---

## Các file thay đổi

| File | Loại |
|------|------|
| `db/migration/V35__cleanup_legacy_mc_columns.sql` | NEW |
| `entity/quiz/Question.java` | Sửa — xóa `options`, `correctAnswer` |
| `entity/quiz/QuizAnswerDetail.java` | Sửa — xóa `selectedOption` |
| `entity/quiz/QuestionReport.java` | Sửa — nullable quiz |
| `dto/quiz/QuestionRequest.java` | Sửa — xóa `options`, `correctAnswer` |
| `dto/quiz/QuestionResponse.java` | Sửa — xóa `options`, `correctAnswer` |
| `dto/quiz/AnswerDetailRequest.java` | Sửa — xóa `selectedAnswer` |
| `dto/quiz/QuizSessionDetailResponse.java` | Sửa — `QuestionResultItem` shape mới |
| `service/quiz/QuizServiceImpl.java` | Sửa lớn — xóa toàn bộ legacy code |
| `test/.../QuestionBankServiceImplTest.java` | Sửa — update test dùng format mới |

---

## Shape mới sau khi xong plan

### QuestionRequest (tạo/sửa câu hỏi)
```
content          String    — bắt buộc
explanation      String    — tuỳ chọn
questionType     enum      — mặc định MULTIPLE_CHOICE
questionData     JsonNode  — bắt buộc với mọi loại:
                             MC:    MultipleChoiceQuestionData
                             FILL:  FillInBlankQuestionData
                             MATCH: MatchingQuestionData
```

### QuestionResponse (learner start + staff view)
```
questionId       String
content          String
questionType     enum
questionData     JsonNode  — sanitized (không có đáp án đúng) cho learner
                            full (có đáp án) cho staff
explanation      String
```

### AnswerDetailRequest (nộp bài)
```
questionId       String    — bắt buộc
answerData       JsonNode  — bắt buộc:
                             MC:    MultipleChoiceAnswerData { selectedOptionIds: ["2"] }
                             FILL:  FillInBlankAnswerData   { answers: ["938"] }
                             MATCH: MatchingAnswerData      { pairs: [{leftId, rightId}] }
```

### QuizSessionDetailResponse.QuestionResultItem (kết quả sau nộp)
```
questionId       String
content          String
questionType     enum
questionData     JsonNode  — FULL (có đáp án, quiz đã hoàn thành)
submittedAnswer  JsonNode  — answerData learner đã nộp
isCorrect        boolean
explanation      String
```

---

## Task 1 — V35 Migration

**File:** `src/main/resources/db/migration/V35__cleanup_legacy_mc_columns.sql`

```sql
-- Xóa các cột legacy MC
ALTER TABLE historical_schema.question
    DROP COLUMN IF EXISTS options,
    DROP COLUMN IF EXISTS correct_answer;

ALTER TABLE historical_schema.quiz_answer_detail
    DROP COLUMN IF EXISTS selected_option;

-- Fix bug: cho phép question_report.quiz_id = NULL (bank question không có quiz)
ALTER TABLE historical_schema.question_report
    ALTER COLUMN quiz_id DROP NOT NULL;
```

**Commit:** `feat(java): V35 — drop legacy MC columns; nullable quiz_id in question_report`

---

## Task 2 — Fix 3 Lỗi Null/FK (Entity + Service)

**Files:** `QuestionReport.java`, `QuizServiceImpl.java`

### RED tests

```
test: updateQuestion_bankQuestion_throwsInvalidRequest
  question.quiz = null → updateQuestion() → expect InvalidRequestException

test: deleteQuestion_bankQuestion_throwsInvalidRequest
  question.quiz = null → deleteQuestion() → expect InvalidRequestException

test: reportQuestion_bankQuestion_savesWithNullQuiz
  question.quiz = null → reportQuestion() → verify save called, no exception
```

### GREEN

**QuestionReport.java:**
```java
@JoinColumn(name = "quiz_id", nullable = true)  // was: nullable = false
```

**QuizServiceImpl.java** — cả `updateQuestion` (dòng 474) lẫn `deleteQuestion` (dòng 505):
```java
// Cũ:
if (!question.getQuiz().getQuizId().equals(quizUuid)) {
// Mới:
if (question.getQuiz() == null || !question.getQuiz().getQuizId().equals(quizUuid)) {
```

**Commit:** `fix(java): null-guard quiz ref in updateQuestion/deleteQuestion; nullable quiz in QuestionReport`

---

## Task 3 — Entity + DTO Cleanup

**Thứ tự thực hiện:** Entity trước → sau đó DTO → compile sẽ báo lỗi → sửa service ở Task 4.

### Question.java — xóa 2 field
```java
// Xóa toàn bộ:
@Column(name = "options", ...)
private String options;

@Column(name = "correct_answer", ...)
private Integer correctAnswer;
```

### QuizAnswerDetail.java — xóa 1 field
```java
// Xóa:
@Column(name = "selected_option", ...)
private Integer selectedOption;
```

### QuestionRequest.java — xóa legacy fields, giữ lại
```java
// Xóa: options: List<String>, correctAnswer: Integer, @Size annotation
// Giữ: content, explanation, questionType, questionData
// Validation MC: kiểm tra trong service (questionData không null)
```

### QuestionResponse.java — xóa legacy fields
```java
// Xóa: options: List<String>, correctAnswer: Integer
// Giữ: questionId, content, questionType, questionData, explanation
```

### AnswerDetailRequest.java — xóa selectedAnswer
```java
// Xóa: selectedAnswer: Integer, comment legacy
// Giữ: questionId, answerData: JsonNode
```

### QuizSessionDetailResponse.QuestionResultItem — thiết kế lại
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public static class QuestionResultItem {
    private String questionId;
    private String content;
    private QuestionType questionType;
    private JsonNode questionData;       // full — quiz đã xong, tiết lộ đáp án
    private JsonNode submittedAnswer;    // answerData learner nộp
    private boolean isCorrect;
    private String explanation;
}
```

**Commit:** `refactor(java): remove legacy MC fields from entity and DTOs`

---

## Task 4 — Service Refactor: Xóa Legacy Code, Cập nhật Tất cả Paths

**File:** `QuizServiceImpl.java`

### 4.1 — Xóa helpers không còn dùng

Xóa hoàn toàn:
- `serializeOptions(List<String> options)`
- `deserializeOptions(String json)`

### 4.2 — buildQuestion — đơn giản hóa

```java
private Question buildQuestion(QuestionRequest request, Quiz quiz) {
    QuestionType type = request.getQuestionType() != null
            ? request.getQuestionType() : QuestionType.MULTIPLE_CHOICE;
    if (request.getQuestionData() == null) {
        throw new InvalidRequestException("questionData là bắt buộc với mọi loại câu hỏi");
    }
    return Question.builder()
            .quiz(quiz)
            .content(request.getContent())
            .explanation(request.getExplanation())
            .questionType(type)
            .questionData(serializeJsonNode(request.getQuestionData()))
            .build();
}
```

### 4.3 — updateQuestion và updateBankQuestion — xóa logic options/correctAnswer

```java
// Xóa các block:
if (request.getOptions() != null && ...) { question.setOptions(...); }
if (request.getCorrectAnswer() != null) { question.setCorrectAnswer(...); }

// Giữ:
if (request.getContent() != null && ...) { question.setContent(...); }
if (request.getExplanation() != null)    { question.setExplanation(...); }
if (request.getQuestionType() != null)   { question.setQuestionType(...); }
if (request.getQuestionData() != null)   { question.setQuestionData(serializeJsonNode(...)); }
```

### 4.4 — mapToQuestionResponse (learner) + mapToStaffQuestionDetail (staff)

**mapToQuestionResponse** — sanitized cho learner:
```java
private QuestionResponse mapToQuestionResponse(Question q) {
    return QuestionResponse.builder()
            .questionId(q.getQuestionId().toString())
            .content(q.getContent())
            .questionType(q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MULTIPLE_CHOICE)
            .questionData(sanitizeQuestionDataForLearner(q))
            .explanation(q.getExplanation())
            .build();
}
```

**mapToStaffQuestionDetail** (mới) — full data cho staff:
```java
private QuestionResponse mapToStaffQuestionDetail(Question q) {
    return QuestionResponse.builder()
            .questionId(q.getQuestionId().toString())
            .content(q.getContent())
            .questionType(q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MULTIPLE_CHOICE)
            .questionData(deserializeJsonNode(q.getQuestionData()))
            .explanation(q.getExplanation())
            .build();
}
```

`mapToStaffResponse` và `mapToBankResponse` dùng `mapToStaffQuestionDetail` (full data).  
`startQuiz`, `addQuestion` dùng `mapToQuestionResponse` (sanitized).

### 4.5 — sanitizeQuestionDataForLearner — thêm MC case

```java
private JsonNode sanitizeQuestionDataForLearner(Question q) {
    if (q.getQuestionData() == null) return null;
    QuestionType type = q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MULTIPLE_CHOICE;
    try {
        return switch (type) {
            case MULTIPLE_CHOICE -> {
                MultipleChoiceQuestionData d = objectMapper.readValue(
                        q.getQuestionData(), MultipleChoiceQuestionData.class);
                yield objectMapper.valueToTree(
                        MultipleChoiceQuestionData.builder().options(d.getOptions()).build());
            }
            case MATCHING -> {
                MatchingQuestionData d = objectMapper.readValue(
                        q.getQuestionData(), MatchingQuestionData.class);
                yield objectMapper.valueToTree(
                        MatchingQuestionData.builder()
                                .leftItems(d.getLeftItems()).rightItems(d.getRightItems()).build());
            }
            case FILL_IN_BLANK -> null; // không có extra data cho learner
        };
    } catch (Exception e) {
        log.warn("Failed to sanitize questionData for question {}", q.getQuestionId());
        return null;
    }
}
```

### 4.6 — gradeAnswer — MC unified, không fallback

Thay `default ->` bằng `case MULTIPLE_CHOICE ->`:
```java
private boolean gradeMultipleChoice(Question question, AnswerDetailRequest answer) {
    if (question.getQuestionData() == null || answer.getAnswerData() == null) return false;
    try {
        MultipleChoiceQuestionData qData = objectMapper.readValue(
                question.getQuestionData(), MultipleChoiceQuestionData.class);
        MultipleChoiceAnswerData aData = objectMapper.treeToValue(
                answer.getAnswerData(), MultipleChoiceAnswerData.class);
        if (qData.getCorrectOptionIds() == null || aData.getSelectedOptionIds() == null) return false;
        return new HashSet<>(qData.getCorrectOptionIds())
                .equals(new HashSet<>(aData.getSelectedOptionIds()));
    } catch (Exception e) {
        log.warn("Failed to grade MC for question {}", question.getQuestionId(), e);
        return false;
    }
}
```

### 4.7 — submitQuiz — xóa selectedOption, chỉ ghi answerData

```java
// Xóa dòng: .selectedOption(ans.getSelectedAnswer())
// Giữ và đủ:
answerDetails.add(QuizAnswerDetail.builder()
        .question(q)
        .quizSession(session)
        .answerData(answerDataJson)
        .isCorrect(isCorrect)
        .scoreEarned(isCorrect ? 1.0f : 0.0f)
        .build());
```

### 4.8 — getSessionDetail — dùng shape mới

```java
List<QuizSessionDetailResponse.QuestionResultItem> items = questions.stream()
    .map(q -> {
        QuizAnswerDetail detail = detailByQuestion.get(q.getQuestionId());
        return QuizSessionDetailResponse.QuestionResultItem.builder()
                .questionId(q.getQuestionId().toString())
                .content(q.getContent())
                .questionType(q.getQuestionType() != null
                        ? q.getQuestionType() : QuestionType.MULTIPLE_CHOICE)
                .questionData(deserializeJsonNode(q.getQuestionData())) // full — quiz đã xong
                .submittedAnswer(detail != null ? deserializeJsonNode(detail.getAnswerData()) : null)
                .isCorrect(detail != null && Boolean.TRUE.equals(detail.getIsCorrect()))
                .explanation(q.getExplanation())
                .build();
    })
    .collect(Collectors.toList());
```

Cũng xóa `score`, `percentage` derivation dựa trên `questions.size()` trong `mapToHistoryResponse`
— giữ nguyên logic đó, chỉ bỏ `deserializeOptions`.

### 4.9 — CSV import saveQuizGroup — build MultipleChoiceQuestionData nội bộ

Thay block `options`/`correctAnswer` → build `QuestionRequest` với `questionData`:

```java
List<QuestionOption> optObjs = new ArrayList<>();
for (int j = 0; j < 4; j++) {
    String opt = row.get("option" + (j + 1)).trim();
    if (opt.isBlank()) throw new InvalidRequestException(
            "Hàng " + rowNum + ": option" + (j + 1) + " phải có nội dung");
    optObjs.add(new QuestionOption(String.valueOf(j), opt));
}
// correctAnswer vẫn đọc từ CSV để biết index, nhưng giờ ghi vào questionData
int correctIdx = Integer.parseInt(row.get("correctAnswer").trim()); // validate range 0-3
MultipleChoiceQuestionData mcData = MultipleChoiceQuestionData.builder()
        .options(optObjs)
        .correctOptionIds(List.of(String.valueOf(correctIdx)))
        .build();

QuestionRequest qr = QuestionRequest.builder()
        .content(questionContent)
        .questionType(QuestionType.MULTIPLE_CHOICE)
        .questionData(objectMapper.valueToTree(mcData))
        .explanation(explanation.isBlank() ? null : explanation)
        .build();
```

**Commit:** `refactor(java): unify all quiz types through question_data pipeline; remove legacy MC code`

---

## Task 5 — Cập nhật Tests

**File:** `QuestionBankServiceImplTest.java`

Các test hiện tại dùng `selectedAnswer` Integer cần đổi sang `answerData` với `MultipleChoiceAnswerData`:

```java
// Cũ (test submitQuiz MC legacy):
AnswerDetailRequest.builder()
    .questionId(questionId.toString())
    .selectedAnswer(2)
    .build()

// Mới:
MultipleChoiceAnswerData ansData = new MultipleChoiceAnswerData(List.of("2"));
AnswerDetailRequest.builder()
    .questionId(questionId.toString())
    .answerData(objectMapper.valueToTree(ansData))
    .build()
```

Thêm test mới:
```
test: submitQuiz_MC_correctAnswer_scoreOne
  question_data = MultipleChoiceQuestionData{options=[...], correctOptionIds=["1"]}
  answerData = MultipleChoiceAnswerData{selectedOptionIds=["1"]}
  → score = 1

test: submitQuiz_MC_wrongAnswer_scoreZero
  answerData = MultipleChoiceAnswerData{selectedOptionIds=["0"]}
  → score = 0
```

**Commit:** `test(java): update quiz tests for unified question_data pipeline`

---

## Review Focus

1. `mapToStaffResponse` gọi đúng `mapToStaffQuestionDetail` (full) thay vì `mapToQuestionResponse` (sanitized) chưa?
2. `addQuestion` (staff thêm câu hỏi vào quiz) trả về `QuestionResponse` sanitized hay full? — Nên trả full data cho staff.
3. Tất cả nơi gọi `serializeOptions`/`deserializeOptions` đã bị xóa chưa — compile sẽ báo nếu còn sót.
4. `gradeAnswer` `switch` còn trường hợp `default` nào không? — Sau khi đổi `default ->` thành `case MULTIPLE_CHOICE ->`, switch phải cover đủ 3 enum values hoặc giữ `default` throw exception.
5. CSV import: validate `correctAnswer` range 0-3 vẫn còn sau refactor.
