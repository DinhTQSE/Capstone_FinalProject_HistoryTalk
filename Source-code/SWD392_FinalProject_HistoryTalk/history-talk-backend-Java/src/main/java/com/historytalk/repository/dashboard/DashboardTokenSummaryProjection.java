package com.historytalk.repository.dashboard;

public interface DashboardTokenSummaryProjection {

    Long getPromptTokens();

    Long getCompletionTokens();

    Long getTotalTokens();
}
