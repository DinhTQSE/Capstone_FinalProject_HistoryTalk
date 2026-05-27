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

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Context ID is required")
    private String contextId;

    /** Optional. Must match EventEra enum: ANCIENT | MEDIEVAL | MODERN | CONTEMPORARY */
//    private String era;

    @NotNull(message = "Level is required")
    private String level;

    @NotEmpty(message = "At least one question is required")
    @Valid
    private List<QuestionRequest> questions;

    private Boolean isPublished = false;
}
