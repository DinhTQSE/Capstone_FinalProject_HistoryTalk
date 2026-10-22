package com.historytalk.repository.dashboard;

public interface AssignmentSubmissionStatsProjection {
    Long getTotalSubmissions();
    Long getSubmittedCount();
    Long getLateSubmittedCount();
    Long getNotStartedCount();
    Long getInProgressCount();
    Long getMissedCount();
}
