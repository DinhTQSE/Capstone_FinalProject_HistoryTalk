package com.historyTalk.dto.quiz;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerDetailRequest {

    @NotNull(message = "Question ID is required")
    private String questionId;

    @NotNull(message = "Selected answer is required")
    private Integer selectedAnswer;

}
