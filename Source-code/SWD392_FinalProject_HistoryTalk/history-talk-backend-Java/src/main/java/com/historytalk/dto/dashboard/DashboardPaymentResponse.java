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
public class DashboardPaymentResponse {

    private PaymentSummary summary;
    private List<TransactionTrendPoint> transactionTrend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentSummary {
        private long totalOrders;
        private long pendingOrders;
        private long paidOrders;
        private long cancelledOrders;
        private long expiredOrders;
        private long failedOrders;
        private long successfulTransactions;
        private long failedTransactions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionTrendPoint {
        private String date;
        private long success;
        private long failed;
    }
}
