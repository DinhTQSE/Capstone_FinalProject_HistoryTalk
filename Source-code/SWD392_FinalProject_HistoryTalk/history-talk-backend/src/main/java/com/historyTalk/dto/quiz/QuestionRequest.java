package com.historyTalk.dto.quiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequest {

    @NotBlank(message = "Question content is required")
    private String content;

    @NotEmpty(message = "Options cannot be empty")
    private List<String> options;

    @NotNull(message = "Correct answer index is required")
    private Integer correctAnswer;

    private Integer orderIndex;

    private String explanation;

}
