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
public class DashboardRevenueResponse {

    private RevenueSummary summary;
    private List<StatusCount> ordersByStatus;
    private List<RevenueByTier> revenueByTier;
    private List<RevenueTrendPoint> trend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RevenueSummary {
        private long totalRevenue;
        private long revenueToday;
        private long revenueThisMonth;
        private long paidOrders;
        private long averageOrderValue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatusCount {
        private String status;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RevenueByTier {
        private String tierId;
        private String tierTitle;
        private long revenue;
        private long paidOrders;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RevenueTrendPoint {
        private String date;
        private long revenue;
        private long paidOrders;
    }
}
