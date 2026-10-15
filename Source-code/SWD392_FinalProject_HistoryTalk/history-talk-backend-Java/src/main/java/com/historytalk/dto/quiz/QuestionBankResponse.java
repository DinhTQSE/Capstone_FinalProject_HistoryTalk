package com.historytalk.dto.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.historytalk.entity.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Response for Question Bank list/detail endpoints (staff only). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionBankResponse {

    private String questionId;
    private String content;
    private String explanation;
    private QuestionType questionType;

    /** Full typed payload including correct answers (staff-only). */
    private JsonNode questionData;

    // Legacy MC fields for backward compat
    private List<String> options;
    private Integer correctAnswer;

    /** Whether this question is currently assigned to at least one quiz. */
    private boolean assignedToQuiz;
}
