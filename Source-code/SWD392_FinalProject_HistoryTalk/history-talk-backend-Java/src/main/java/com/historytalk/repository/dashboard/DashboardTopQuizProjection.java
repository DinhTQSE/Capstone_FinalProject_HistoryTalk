package com.historytalk.repository.dashboard;

public interface DashboardTopQuizProjection {

    String getQuizId();

    String getTitle();

    String getLevel();

    Long getStartedSessions();

    Long getCompletedSessions();

    Double getAverageScorePercentage();
}
