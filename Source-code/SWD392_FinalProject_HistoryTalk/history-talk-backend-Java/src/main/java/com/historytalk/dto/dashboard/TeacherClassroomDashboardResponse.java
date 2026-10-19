package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherClassroomDashboardResponse {
    private UUID classId;
    private String className;
    private String classCode;
    private int gradeLevel;
    private int academicYear;
    private String status;
    private int dailyTokenLimit;
    private long pendingTokenBoostRequests;
    private long studentCount;

    // Tương tác AI (Engagement Analytics)
    private long totalAiMessages;
    private double avgAiMessagesPerStudent;

    // Tỷ lệ bài tập
    private AssignmentRates assignmentRates;

    // Phân tích từng học sinh trong lớp
    private List<StudentAnalyticsItem> students;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignmentRates {
        private double onTimeRate;
        private double lateRate;
        private double pendingRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentAnalyticsItem {
        private UUID studentId;
        private String studentCode;
        private String fullName;
        private int dailyTokenLimit;
        private int dailyBonusTokens;
        private int dailyTokensUsed;
        private int currentTokens; // Hạn mức còn được tiêu trong ngày hôm nay
        private long quizzesAttempted;
        private double averageScorePercentage;
        private long aiMessagesCount;
    }
}
