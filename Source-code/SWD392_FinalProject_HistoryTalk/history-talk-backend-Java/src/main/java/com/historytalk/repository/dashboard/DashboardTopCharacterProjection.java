package com.historytalk.repository.dashboard;

public interface DashboardTopCharacterProjection {
    String getCharacterId();
    String getName();
    String getTitle();
    String getImageUrl();
    Long getTotalMessages();
    Long getUserMessages();
    Long getAiMessages();
}
