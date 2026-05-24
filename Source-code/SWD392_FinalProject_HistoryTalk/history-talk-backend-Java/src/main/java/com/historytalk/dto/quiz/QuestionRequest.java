package com.historytalk.dto.quiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for POST /staff/quizzes/:quizId/questions
 * and partial body for PUT /staff/quizzes/:quizId/questions/:questionId (all fields optional on update).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequest {

    @NotBlank(message = "Question content is required")
    private String content;

    @NotNull(message = "Options are required")
    @Size(min = 4, max = 4, message = "Exactly 4 options are required")
    private List<String> options;

    /** 0-based index of the correct option (0-3) */
    @NotNull(message = "Correct answer is required")
    private Integer correctAnswer;

    private String explanation;
}
