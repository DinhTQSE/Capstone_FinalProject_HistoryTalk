package com.historytalk.repository.dashboard;

public interface DashboardTransactionTrendProjection {

    String getPeriod();

    Long getSuccess();

    Long getFailed();
}
