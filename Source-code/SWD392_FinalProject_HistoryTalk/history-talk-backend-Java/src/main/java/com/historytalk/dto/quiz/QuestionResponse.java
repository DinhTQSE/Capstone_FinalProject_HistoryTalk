package com.historytalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for GET /quizzes/:id (full detail) and embedded in QuizStartResponse / QuizStaffResponse.
 * Shape matches contract QuizQuestion: { questionId, content, options[], correctAnswer, explanation? }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private String questionId;

    private String content;

    /** 4 options, deserialized from JSON stored in Question.options */
    private List<String> options;

    /** 0-based index of the correct option */
    private Integer correctAnswer;

    private String explanation;
}
