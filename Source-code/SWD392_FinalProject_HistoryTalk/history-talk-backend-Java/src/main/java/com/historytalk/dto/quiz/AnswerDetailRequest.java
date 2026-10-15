package com.historytalk.dto.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One answer entry within QuizSubmitRequest.
 * All types (MC, FILL_IN_BLANK, MATCHING) use answerData.
 *   MC:    MultipleChoiceAnswerData { selectedOptionIds: ["2"] }
 *   FILL:  FillInBlankAnswerData   { answers: ["938"] }
 *   MATCH: MatchingAnswerData      { pairs: [{leftId, rightId}] }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerDetailRequest {

    @NotNull(message = "Yêu cầu Question ID")
    private String questionId;

    private JsonNode answerData;
}
