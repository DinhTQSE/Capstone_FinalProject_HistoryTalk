package com.historytalk.dto.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.historytalk.entity.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequest {

    @NotBlank(message = "Yêu cầu nội dung câu hỏi")
    private String content;

    private String explanation;

    @Builder.Default
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    /**
     * Typed payload — required for all question types.
     * MC:    MultipleChoiceQuestionData { options: [{id, text}], correctOptionIds: ["2"] }
     * FILL:  FillInBlankQuestionData   { acceptedAnswers: ["938"], caseSensitive, trimWhitespace }
     * MATCH: MatchingQuestionData      { leftItems, rightItems, correctPairs }
     */
    private JsonNode questionData;
}
