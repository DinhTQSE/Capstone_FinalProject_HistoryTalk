package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardQuizAnalyticsResponse {

    private QuizSummary summary;
    private List<QuizSessionTrendPoint> sessionsTrend;
    private List<TopQuiz> topQuizzes;
    private List<TopWrongQuestion> topWrongQuestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizSummary {
        private long totalQuizzes;
        private long publishedQuizzes;
        private long draftQuizzes;
        private long deletedQuizzes;
        private long startedSessions;
        private long completedSessions;
        private double completionRate;
        private double averageScorePercentage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizSessionTrendPoint {
        private String date;
        private long started;
        private long completed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopQuiz {
        private String quizId;
        private String title;
        private String level;
        private long startedSessions;
        private long completedSessions;
        private double averageScorePercentage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopWrongQuestion {
        private String questionId;
        private String quizId;
        private String quizTitle;
        private long wrongAnswers;
        private long totalAnswers;
        private double wrongRate;
    }
}
