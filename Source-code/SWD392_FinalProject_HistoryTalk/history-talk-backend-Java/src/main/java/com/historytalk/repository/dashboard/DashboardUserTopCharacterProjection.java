package com.historytalk.repository.dashboard;

public interface DashboardUserTopCharacterProjection {
    String getCharacterId();
    String getName();
    Long getMessageCount();
    Long getTokenUsed();
}
