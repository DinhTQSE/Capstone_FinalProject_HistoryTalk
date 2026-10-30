package com.historytalk.dto.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.historytalk.entity.enums.QuestionDifficulty;
import com.historytalk.entity.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private QuestionDifficulty difficulty;

    /** Full typed payload including correct answers (staff-only). */
    private JsonNode questionData;

    /** Whether this question is currently assigned to at least one quiz. */
    private boolean assignedToQuiz;
}
