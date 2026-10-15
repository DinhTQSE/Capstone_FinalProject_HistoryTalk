package com.historytalk.dto.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.historytalk.entity.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for questions. Legacy MC fields retained for backward compat.
 * New types additionally return questionType + questionData (sanitized for learners).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private String questionId;

    private String content;

    // --- Legacy MC fields ---

    /** 4 options (deserialized from Question.options). Present for MC questions. */
    private List<String> options;

    /** 0-based index of the correct option. Present for MC questions. */
    private Integer correctAnswer;

    private String explanation;

    // --- New typed fields ---

    /** Defaults to MULTIPLE_CHOICE for legacy questions. */
    @Builder.Default
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    /**
     * Typed payload. Learner-safe: correct answer keys removed by service before returning.
     * Staff view includes full payload.
     */
    private JsonNode questionData;
}
