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
public class DashboardTokenUsageResponse {

    private TokenSummary summary;
    private List<TokenTrendPoint> trend;
    private List<TokenBalanceByTier> tokenBalanceByTier;
    private List<TopTokenUser> topUsersByTokenUsage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenSummary {
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long remainingTokens;
        private double averageRemainingTokens;
        private long usersOutOfTokens;
        private long estimatedCost;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenTrendPoint {
        private String date;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenBalanceByTier {
        private String tierId;
        private String tierTitle;
        private long users;
        private long remainingTokens;
        private double averageRemainingTokens;
        private long usersOutOfTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopTokenUser {
        private String uid;
        private String userName;
        private String email;
        private String tierId;
        private String tierTitle;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long remainingTokens;
    }
}
