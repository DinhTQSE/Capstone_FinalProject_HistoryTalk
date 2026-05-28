package com.historytalk.repository.dashboard;

public interface DashboardTokenBalanceByTierProjection {

    String getTierId();

    String getTierTitle();

    Long getUsers();

    Long getRemainingTokens();

    Double getAverageRemainingTokens();

    Long getUsersOutOfTokens();
}
