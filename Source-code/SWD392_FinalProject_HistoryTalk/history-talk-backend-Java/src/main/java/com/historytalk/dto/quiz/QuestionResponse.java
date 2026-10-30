package com.historytalk.dto.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.historytalk.entity.enums.QuestionDifficulty;
import com.historytalk.entity.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private String questionId;

    private String content;

    @Builder.Default
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    private QuestionDifficulty difficulty;

    /**
     * Typed payload.
     * Learner paths: correct answer keys stripped (no correctOptionIds / correctPairs / acceptedAnswers).
     * Staff paths: full data including correct answers.
     */
    private JsonNode questionData;

    private String explanation;
}
