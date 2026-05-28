package com.historytalk.repository.dashboard;

public interface DashboardTokenTrendProjection {

    String getPeriod();

    Long getPromptTokens();

    Long getCompletionTokens();

    Long getTotalTokens();
}
