package com.historytalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for POST /quizzes/:quizId/start
 * Shape: { sessionId, quizId, title, questions[] }
 * Note: limitedTime is NOT returned — it is stored server-side in the session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizStartResponse {

    private String sessionId;

    private String quizId;

    private String title;

    private List<QuestionResponse> questions;
}
