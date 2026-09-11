package com.historytalk.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDashboardResponse {
    private LearningAnalytics learning;
    private AiUsageAnalytics aiUsage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LearningAnalytics {
        private long totalQuizzesAttempted;
        private double averageScorePercentage;
        private Map<String, Long> eraDistribution;
        private List<RecentQuizItem> recentQuizzes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentQuizItem {
        private String sessionId;
        private String quizTitle;
        private double percentage;
        private LocalDateTime completedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiUsageAnalytics {
        private int currentBalance;
        private String tier;
        private long totalTokensUsed;
        private long promptTokens;
        private long completionTokens;
        private List<TopCharacterItem> topCharacters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopCharacterItem {
        private String characterId;
        private String name;
        private long messageCount;
        private long tokenUsed;
    }
}
