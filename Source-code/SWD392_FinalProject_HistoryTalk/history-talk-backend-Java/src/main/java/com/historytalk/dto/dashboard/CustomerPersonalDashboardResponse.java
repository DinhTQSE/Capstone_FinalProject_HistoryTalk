package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPersonalDashboardResponse {
    private UUID uid;
    private String userName;
    private String email;

    // Gói cước & Token
    private String currentTier;
    private int currentTokens;
    private LocalDateTime tierExpiresAt;

    // Phân tích Học tập
    private long totalQuizzesAttempted;
    private double averageScorePercentage;
    private Map<String, Long> eraDistribution;
    private List<RecentQuizItem> recentQuizzes;

    // Tương tác AI
    private long totalAiMessages;
    private List<TopCharacterItem> topCharacters;

    // Lịch sử giao dịch PayOS
    private List<PaymentTransactionItem> paymentHistory;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentQuizItem {
        private UUID sessionId;
        private String quizTitle;
        private double percentage;
        private LocalDateTime completedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopCharacterItem {
        private String characterId;
        private String characterName;
        private long messageCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentTransactionItem {
        private Long orderCode;
        private Integer amount;
        private String tierName;
        private String status;
        private LocalDateTime createdAt;
    }
}
