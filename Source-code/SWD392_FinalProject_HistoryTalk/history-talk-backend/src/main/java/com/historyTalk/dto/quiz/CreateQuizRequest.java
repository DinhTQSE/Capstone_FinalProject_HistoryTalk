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
public class CreateQuizRequest {

    @NotBlank(message = "Quiz title is required")
    private String title;

    private String description;

    @NotNull(message = "Context ID is required")
    private String contextId;

    private Integer grade;

    private Integer chapterNumber;

    private String chapterTitle;

    private String era;

    private Integer durationSeconds;

    @NotEmpty(message = "Questions cannot be empty")
    private List<QuestionRequest> questions;

}
