package com.historytalk.dto.quiz;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One answer entry within QuizSubmitRequest.
 * Shape: { questionId, selectedAnswer }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerDetailRequest {

    @NotNull(message = "Yêu cầu Question ID")
    private String questionId;

    @NotNull(message = "Yêu cầu chọn câu trả lời")
    private Integer selectedAnswer;
}
