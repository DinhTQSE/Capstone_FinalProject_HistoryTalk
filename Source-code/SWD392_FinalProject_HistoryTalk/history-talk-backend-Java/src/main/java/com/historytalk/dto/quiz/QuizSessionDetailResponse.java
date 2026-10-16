package com.historytalk.dto.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.historytalk.entity.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSessionDetailResponse {

    private String sessionId;

    private String quizId;

    private String quizTitle;

    private int score;

    private int totalQuestions;

    private double percentage;

    private Integer limitedTime;

    private String startedAt;

    private String completedAt;

    private List<QuestionResultItem> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResultItem {

        private String questionId;

        private String content;

        private QuestionType questionType;

        /** Full question payload — correct answers revealed (session is completed). */
        private JsonNode questionData;

        /** What the learner submitted. */
        private JsonNode submittedAnswer;

        private boolean isCorrect;

        private String explanation;
    }
}
