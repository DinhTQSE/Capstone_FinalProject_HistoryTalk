package com.historytalk.repository.dashboard;

public interface DashboardTokenBalanceSummaryProjection {

    Long getRemainingTokens();

    Double getAverageRemainingTokens();

    Long getUsersOutOfTokens();
}
