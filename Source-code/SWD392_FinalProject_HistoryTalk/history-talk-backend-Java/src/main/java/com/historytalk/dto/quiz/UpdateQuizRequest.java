package com.historytalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for PUT /staff/quizzes/:quizId
 * All fields are optional (partial update of quiz metadata, NOT questions).
 * era is NOT updatable directly — it comes from the historicalContext.
 * To change era, change the contextId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuizRequest {

    /** null = no change */
    private String title;

    /** null = no change. Changing context also changes era. */
    private String contextId;

    /** null = no change. Must match QuizLevel enum: EASY | MEDIUM | HARD */
    private String level;

    /** null = no change */
    private Boolean isPublished;
}
