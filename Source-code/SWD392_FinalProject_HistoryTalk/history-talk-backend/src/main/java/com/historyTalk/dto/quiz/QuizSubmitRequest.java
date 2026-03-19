package com.historyTalk.dto.quiz;

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
public class QuizSubmitRequest {

    @NotNull(message = "Session ID is required")
    private String sessionId;

    @NotEmpty(message = "Answers cannot be empty")
    private List<AnswerDetailRequest> answers;

    @NotNull(message = "Duration is required")
    private Integer durationSeconds;

}
