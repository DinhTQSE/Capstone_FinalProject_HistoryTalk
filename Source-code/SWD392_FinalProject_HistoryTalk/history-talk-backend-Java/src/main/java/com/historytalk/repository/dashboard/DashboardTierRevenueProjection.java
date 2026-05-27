package com.historytalk.repository.dashboard;

public interface DashboardTierRevenueProjection {

    String getTierId();

    String getTierTitle();

    Number getRevenue();

    Long getPaidOrders();
}
