package com.historytalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full detail of one completed quiz session.
 * Returned by:
 *   GET /api/v1/quizzes/results/me/{sessionId}          (CUSTOMER — own sessions only)
 *   GET /api/v1/staff/quizzes/sessions/{sessionId}      (SYSTEM_ADMIN — any session)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSessionDetailResponse {

    private String sessionId;

    private String quizId;

    private String quizTitle;

    /** Number of correct answers */
    private int score;

    private int totalQuestions;

    /** score / totalQuestions * 100 */
    private double percentage;

    /** Time limit in seconds set by the user at start. Null if no time limit was applied. */
    private Integer limitedTime;

    /** ISO-8601 — session.startTime */
    private String startedAt;

    /** ISO-8601 — session.endTime */
    private String completedAt;

    /** Per-question answer breakdown ordered by question creation order. */
    private List<QuestionResultItem> questions;

    // ── Inner DTO ────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResultItem {

        private String questionId;

        /** Full question text */
        private String content;

        /** All answer options (deserialized from JSON) */
        private List<String> options;

        /** 0-based index of the correct answer */
        private int correctAnswer;

        /**
         * 0-based index the user selected.
         * Null when the user did not answer this question before submitting.
         */
        private Integer selectedAnswer;

        private boolean isCorrect;

        /** Explanation of the correct answer — may be null */
        private String explanation;
    }
}
