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
public class DashboardTierAnalyticsResponse {

    private TierSummary summary;
    private List<UsersByTier> usersByTier;
    private List<PurchasesByTier> purchasesByTier;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TierSummary {
        private long activeTiers;
        private long currentPaidUsers;
        private long currentFreeUsers;
        private long activeSubscriptions;
        private long expiringSoonSubscriptions;
        private double freeToPaidConversionRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UsersByTier {
        private String tierId;
        private String tierTitle;
        private long users;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PurchasesByTier {
        private String tierId;
        private String tierTitle;
        private long paidOrders;
        private long revenue;
    }
}
