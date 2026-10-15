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
public class SystemAdminComprehensiveDashboardResponse {
    private JvmHealthMetrics jvmHealth;
    private TokenConsumptionSummary tokenSummary;
    private List<TopWrongQuestionItem> topWrongQuestions;
    private PlatformOverview platformOverview;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JvmHealthMetrics {
        private double cpuUsagePercentage;
        private long usedMemoryMb;
        private long totalMemoryMb;
        private long freeMemoryMb;
        private long maxMemoryMb;
        private double memoryUsagePercentage;
        private int activeThreadCount;
        private long uptimeSeconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenConsumptionSummary {
        private long totalTokensB2bAllocated;
        private long totalTokensB2cConsumed;
        private long totalTokensSystemConsumed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopWrongQuestionItem {
        private String questionId;
        private String quizTitle;
        private long wrongAnswers;
        private long totalAnswers;
        private double wrongRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlatformOverview {
        private long totalSchools;
        private long totalUsers;
        private long totalQuizzes;
        private long totalRevenue;
    }
}
