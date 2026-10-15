package com.historytalk.dto.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.historytalk.entity.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for create/update question (staff).
 * Legacy MC fields (options, correctAnswer) are kept for backward compatibility.
 * New types use questionType + questionData.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequest {

    @NotBlank(message = "Yêu cầu nội dung câu hỏi")
    private String content;

    // --- Legacy MULTIPLE_CHOICE fields (kept for backward compat) ---

    @Size(min = 4, max = 4, message = "Bắt buộc phải có đúng 4 tùy chọn")
    private List<String> options;

    /** 0-based index of the correct option (0-3). Legacy MC only. */
    private Integer correctAnswer;

    private String explanation;

    // --- New typed fields ---

    /** Defaults to MULTIPLE_CHOICE when absent. */
    @Builder.Default
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    /**
     * Typed payload per questionType.
     * MC:   MultipleChoiceQuestionData
     * FILL: FillInBlankQuestionData
     * MATCH: MatchingQuestionData
     */
    private JsonNode questionData;
}
