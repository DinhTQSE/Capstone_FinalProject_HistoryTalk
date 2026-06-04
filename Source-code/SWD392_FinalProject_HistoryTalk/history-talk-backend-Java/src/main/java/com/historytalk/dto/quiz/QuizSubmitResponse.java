package com.historytalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for POST /quizzes/submit
 * Shape: { resultId, score, totalQuestions, percentage, correctAnswers[], wrongAnswers[] }
 * correctAnswers/wrongAnswers are 0-based index positions in the questions list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmitResponse {

    /** Same value as sessionId */
    private String resultId;

    /** Number of correct answers */
    private int score;

    private int totalQuestions;

    /** score / totalQuestions * 100 */
    private double percentage;

    //** start time */
    private LocalDateTime startTime;

    //** end time */
    private LocalDateTime endTime;

    /** 0-based index positions of correctly answered questions */
    private List<Integer> correctAnswers;

    /** 0-based index positions of incorrectly answered questions */
    private List<Integer> wrongAnswers;
}
