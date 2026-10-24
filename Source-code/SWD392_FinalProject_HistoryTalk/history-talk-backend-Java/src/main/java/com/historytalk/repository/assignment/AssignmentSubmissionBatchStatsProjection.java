package com.historytalk.repository.assignment;

import java.util.UUID;

public interface AssignmentSubmissionBatchStatsProjection {
    UUID getAssignmentId();
    Long getTotalSubmissions();
    Long getSubmittedCount();
    Long getLateSubmittedCount();
    Long getNotStartedCount();
    Long getInProgressCount();
    Long getMissedCount();
}
