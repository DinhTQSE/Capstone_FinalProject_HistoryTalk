package com.historytalk.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for POST /quizzes/submit
 * Shape: { sessionId, answers: [{ questionId, selectedAnswer }] }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmitRequest {

    @NotNull(message = "Yêu cầu Session ID")
    private String sessionId;

    @NotEmpty(message = "Danh sách câu trả lời không được để trống")
    @Valid
    private List<AnswerDetailRequest> answers;
}
