package com.historytalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /quizzes and GET /quizzes/:quizId (Customer).
 * Shape matches contract QuizSet: { quizId, title, level, era, playCount, contextTitle? }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizCustomerResponse {

    private String quizId;

    private String title;

    /** EASY | MEDIUM | HARD */
    private String level;

    /** ANCIENT | MEDIEVAL | MODERN | CONTEMPORARY — sourced from historicalContext.era */
    private String era;

    /** Number of times this user has completed this quiz (endTime IS NOT NULL) */
    private int playCount;

    private String contextTitle;
}
