package com.historytalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One item in the GET /quizzes/results/me paginated response.
 * Shape: { sessionId, quizId, quizTitle, score, totalQuestions, percentage, completedAt }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizHistoryResponse {

    private String sessionId;

    private String quizId;

    private String quizTitle;

    /** Number of correct answers */
    private int score;

    private int totalQuestions;

    /** score / totalQuestions * 100 */
    private double percentage;

    /** ISO8601 datetime — mapped from session.endTime */
    private String completedAt;
}
