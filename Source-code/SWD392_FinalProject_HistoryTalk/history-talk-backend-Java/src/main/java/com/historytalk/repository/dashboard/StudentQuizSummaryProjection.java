package com.historytalk.repository.dashboard;

import java.util.UUID;

public interface StudentQuizSummaryProjection {
    UUID getStudentUid();
    Long getQuizzesAttempted();
    Double getAverageScorePercentage();
}
