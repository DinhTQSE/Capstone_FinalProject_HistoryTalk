# 🔄 SPRINT 7 — BUSINESS FLOWS
> **Chủ đề:** Quiz Module — Luồng làm bài, Quản lý nội dung, Question Bank  
> **Áp dụng cho:** Sprint 7 (16/10/2026 – 30/10/2026)

---

## 1. LUỒNG LÀM BÀI CỦA LEARNER

```mermaid
sequenceDiagram
    autonumber
    actor L as Learner (CUSTOMER)
    participant API as HistoryTalk API
    participant DB as Database

    L->>API: GET /api/v1/quizzes?search=&contextId=
    API-->>L: [ { quizId, title, level, era, playCount, contextTitle } ]

    L->>API: POST /api/v1/quizzes/{quizId}/start
    Note over API: Tạo QuizSession (endTime = null)
    API->>DB: INSERT quiz_session
    API-->>L: { sessionId, quizId, title, questions[] }
    Note over L: questions[].questionData đã sanitized<br/>(không có correctOptionIds/correctPairs/acceptedAnswers)

    loop Mỗi câu hỏi
        L->>L: Learner chọn đáp án (answerData theo từng loại)
    end

    L->>API: POST /api/v1/quizzes/submit
    Note over API: Grade từng câu theo questionType<br/>MC: so khớp selectedOptionIds vs correctOptionIds<br/>FILL: so khớp text (case/trim config)<br/>MATCHING: all-or-nothing pairs
    API->>DB: INSERT quiz_answer_detail (mỗi câu)<br/>UPDATE quiz_session (score, endTime)
    API-->>L: { resultId, score, totalQuestions, percentage, correctAnswers[], wrongAnswers[] }

    L->>API: GET /api/v1/quizzes/results/me/{sessionId}
    API-->>L: { sessionId, quizId, questions[{ questionData (full), submittedAnswer, isCorrect }] }
    Note over L: Lần này questionData trả về FULL<br/>(bao gồm correctOptionIds/correctPairs)
```

---

## 2. QUY TRÌNH XỬ LÝ SUBMIT & CHẤM ĐIỂM

```mermaid
flowchart TD
    Start["POST /quizzes/submit\n{ sessionId, answers[] }"] --> CheckSession{"Session tồn tại\nvà thuộc về user?"}
    CheckSession -- "Không" --> E1["403 / 404"]
    CheckSession -- "Có" --> CheckSubmitted{"endTime IS NULL?"}
    CheckSubmitted -- "Đã nộp" --> E2["400: Quiz đã được nộp"]
    CheckSubmitted -- "Chưa nộp" --> LoadQuestions["Load questions theo quizId\n(bao gồm bank questions qua quiz_question)"]
    LoadQuestions --> GradeLoop["For each question"]
    GradeLoop --> CheckType{"questionType?"}
    CheckType -- "MULTIPLE_CHOICE" --> GradeMC["So khớp selectedOptionIds\nvs correctOptionIds"]
    CheckType -- "FILL_IN_BLANK" --> GradeFill["So khớp text với acceptedAnswers\n(caseSensitive, trimWhitespace)"]
    CheckType -- "MATCHING" --> GradeMatch["All-or-nothing:\nmọi pair phải đúng"]
    GradeMC & GradeFill & GradeMatch --> SaveDetail["INSERT quiz_answer_detail\n(answerData JSON, isCorrect, scoreEarned)"]
    SaveDetail --> NextQ{"Còn câu hỏi?"}
    NextQ -- "Có" --> GradeLoop
    NextQ -- "Không" --> SaveSession["UPDATE quiz_session\n(score, endTime = NOW())"]
    SaveSession --> Gamification["recordProgress(userId, QUIZ)"]
    Gamification --> Return["200: { score, percentage, correctAnswers[], wrongAnswers[] }"]
```

---

## 3. LUỒNG QUẢN LÝ QUIZ CỦA STAFF

```mermaid
sequenceDiagram
    autonumber
    actor S as Staff (CONTENT_ADMIN / SYSTEM_ADMIN)
    participant API as HistoryTalk API

    S->>API: GET /api/v1/staff/quizzes?search=&era=&page=0&size=10
    API-->>S: PaginatedResponse<QuizStaffResponse>

    S->>API: POST /api/v1/staff/quizzes
    Note over S: Body: { title, contextId, level, isPublished, questions[] }
    API-->>S: QuizStaffResponse (questions[].questionData = full)

    S->>API: POST /api/v1/staff/quizzes/{quizId}/questions
    Note over S: Body: { content, questionType, questionData, explanation }
    API-->>S: QuestionResponse (full)

    S->>API: PUT /api/v1/staff/quizzes/{quizId}/questions/{questionId}
    API-->>S: 200 (void)

    S->>API: DELETE /api/v1/staff/quizzes/{quizId}/questions/{questionId}
    API-->>S: 200 (void)

    S->>API: POST /api/v1/staff/quizzes/import (multipart/form-data, file=*.csv)
    Note over API: Group rows by title → 1 quiz / group<br/>Build MultipleChoiceQuestionData nội bộ<br/>Skip trùng title hoặc lỗi validation
    API-->>S: { totalQuizzesAttempted, successCount, skippedCount, errors[], imported[] }
```

---

## 4. LUỒNG QUESTION BANK

```mermaid
sequenceDiagram
    autonumber
    actor S as Staff (CONTENT_ADMIN / SYSTEM_ADMIN)
    participant API as HistoryTalk API

    S->>API: POST /api/v1/staff/question-bank
    Note over S: Tạo câu hỏi không gắn quiz (quiz_id = null)
    API-->>S: QuestionBankResponse (201 Created)

    S->>API: GET /api/v1/staff/question-bank?questionType=FILL_IN_BLANK&search=
    API-->>S: PaginatedResponse<QuestionBankResponse>

    S->>API: POST /api/v1/staff/question-bank/{questionId}/attach/{quizId}
    Note over API: INSERT quiz_question (link table)<br/>orderIndex = max + 1
    API-->>S: 200 (void)

    Note over S: Câu hỏi từ Bank xuất hiện trong quiz khi learner chơi

    S->>API: DELETE /api/v1/staff/question-bank/{questionId}/detach/{quizId}
    Note over API: Xóa row trong quiz_question<br/>Câu hỏi vẫn còn trong bank
    API-->>S: 200 (void)

    S->>API: PATCH /api/v1/staff/question-bank/{questionId}/soft-delete
    Note over API: SET deleted_at = NOW()
    API-->>S: 200 (void)
```

---

## 5. QUY TRÌNH RẼ NHÁNH — QUESTION DATA SANITIZATION

```mermaid
flowchart TD
    Start["Learner nhận QuestionResponse"] --> CheckType{"questionType?"}
    CheckType -- "MULTIPLE_CHOICE" --> MC["questionData = { options: [...] }\ncorrectOptionIds bị XÓA"]
    CheckType -- "FILL_IN_BLANK" --> Fill["questionData = null\nacceptedAnswers bị XÓA hoàn toàn"]
    CheckType -- "MATCHING" --> Match["questionData = { leftItems, rightItems }\ncorrectPairs bị XÓA"]
    MC & Fill & Match --> Display["Learner thấy câu hỏi, KHÔNG biết đáp án đúng"]
    Display --> Submit["Learner nộp answerData theo đúng format loại câu hỏi"]
    Submit --> SessionDetail["Sau khi nộp: getSessionDetail trả về\nquestionData FULL (kể cả đáp án đúng)"]
```
