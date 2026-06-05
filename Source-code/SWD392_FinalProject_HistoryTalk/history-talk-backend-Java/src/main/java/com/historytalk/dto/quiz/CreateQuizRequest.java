package com.historytalk.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for POST /staff/quizzes
 * Shape: { title, contextId, era?, level, questions[] }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuizRequest {

    @NotBlank(message = "Yêu cầu tiêu đề")
    private String title;

    @NotNull(message = "Yêu cầu Context ID")
    private String contextId;

    /** Optional. Must match EventEra enum: ANCIENT | MEDIEVAL | MODERN | CONTEMPORARY */
//    private String era;

    @NotNull(message = "Yêu cầu cấp độ (Level)")
    private String level;

    @NotEmpty(message = "Yêu cầu ít nhất một câu hỏi")
    @Valid
    private List<QuestionRequest> questions;

    private Boolean isPublished = false;
}
