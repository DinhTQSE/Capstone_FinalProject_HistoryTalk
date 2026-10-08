# 🎯 HistoryTalk Java Backend - Quiz Gamification Specification

This specification documents the Quiz domain architecture, historical context association, question bank management, scoring, attempt session tracking, and user answer details.

---

## 1. Domain ERD & Relations

The Quiz module bridges historical education with interactive gamification quizzes:

```mermaid
erDiagram
    HISTORICAL_CONTEXT ||--o{ QUIZ : "contains"
    USER ||--o{ QUIZ : "created by"
    QUIZ ||--o{ QUESTION : "has questions"
    QUIZ ||--o{ QUIZ_SESSION : "attempted in"
    USER ||--o{ QUIZ_SESSION : "starts session"
    QUIZ_SESSION ||--o{ QUIZ_ANSWER_DETAIL : "records answers"
    QUESTION ||--o{ QUIZ_ANSWER_DETAIL : "answers question"

    QUIZ {
        uuid quiz_id PK
        uuid context_id FK
        uuid created_by FK
        string title
        text description
        int grade
        int chapter_number
        int duration_seconds
        int play_count
        double rating
    }

    QUESTION {
        uuid question_id PK
        uuid quiz_id FK
        text content
        text options
        int correct_answer
        int order_index
        text explanation
    }

    QUIZ_SESSION {
        uuid session_id PK
        uuid quiz_id FK
        uuid uid FK
        timestamp start_time
        timestamp end_time
        double score
        boolean is_submitted
    }
```

---

## 2. Quiz Attempt Lifecycle & Auto-Scoring

1. **Start Quiz Session (`POST /api/v1/quizzes/{id}/start`)**:
   - Creates a `quiz_session` with `start_time = NOW()` and `is_submitted = false`.
   - Returns randomized questions with hidden `correct_answer` fields.

2. **Submit Quiz Attempt (`POST /api/v1/quizzes/sessions/{sessionId}/submit`)**:
   - Takes array of user answers `{ questionId, selectedOption }`.
   - Validates each answer against `question.correct_answer`.
   - Computes overall score percentage: `(correct_count / total_questions) * 10.0`.
   - Records individual `quiz_answer_detail` entries with correctness boolean.
   - Increments `quiz.play_count`.

---

## 3. Quiz API Endpoints Reference

| Method | Endpoint | Access Level | Description |
|---|---|---|---|
| `GET` | `/api/v1/quizzes` | Public | List all quizzes with filtering by `contextId`, `grade`, or `era`. |
| `GET` | `/api/v1/quizzes/{id}` | Public | Get quiz info and question list. |
| `POST` | `/api/v1/quizzes` | `CONTENT_ADMIN` | Create a new quiz module linked to a historical context. |
| `PATCH` | `/api/v1/quizzes/{id}` | `CONTENT_ADMIN` | Update quiz title, grade, duration, or questions. |
| `DELETE` | `/api/v1/quizzes/{id}` | `CONTENT_ADMIN` | Soft-delete a quiz. |
| `POST` | `/api/v1/quizzes/{id}/start` | Authenticated | Start a new quiz attempt session. |
| `POST` | `/api/v1/quizzes/sessions/{sessionId}/submit` | Authenticated | Submit quiz answers and receive score report. |
| `GET` | `/api/v1/quizzes/sessions/{sessionId}` | Authenticated | Get detailed result of a completed quiz session. |
