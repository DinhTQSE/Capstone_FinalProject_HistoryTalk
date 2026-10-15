package com.historytalk.dto.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One answer entry within QuizSubmitRequest.
 * MC legacy: use selectedAnswer (Integer).
 * FILL_IN_BLANK / MATCHING: use answerData (typed JSON payload).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerDetailRequest {

    @NotNull(message = "Yêu cầu Question ID")
    private String questionId;

    /** Legacy MC: 0-based option index. Null for non-MC types. */
    private Integer selectedAnswer;

    /**
     * Typed answer payload for FILL_IN_BLANK / MATCHING.
     * FILL: FillInBlankAnswerData  { answers: ["..."] }
     * MATCH: MatchingAnswerData    { pairs: [{leftId, rightId}, ...] }
     */
    private JsonNode answerData;
}
