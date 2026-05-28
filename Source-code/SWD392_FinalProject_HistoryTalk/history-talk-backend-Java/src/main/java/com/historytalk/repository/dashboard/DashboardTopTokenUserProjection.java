package com.historytalk.repository.dashboard;

public interface DashboardTopTokenUserProjection {

    String getUid();

    String getUserName();

    String getEmail();

    String getTierId();

    String getTierTitle();

    Long getPromptTokens();

    Long getCompletionTokens();

    Long getTotalTokens();

    Long getRemainingTokens();
}
