package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentPersonalDashboardResponse {
    private UUID studentId;
    private String studentName;
    private String studentCode;
    private String schoolName;

    // Hạn mức Chi tiêu Token theo Ngày (Pure Shared Pool + Daily Limit)
    private int dailyTokenLimit;
    private int dailyBonusTokens;
    private int currentTokens;     // Hạn mức còn lại được phép tiêu hôm nay
    private long tokensUsedToday;  // Số token đã tiêu hôm nay

    // Học tập Quiz
    private long totalQuizzesAttempted;
    private double averageScorePercentage;
    private List<RecentQuizItem> recentQuizzes;

    // Danh sách lớp học (ACTIVE hiện tại & ARCHIVED các năm trước)
    private List<EnrolledClassroomItem> enrolledClassrooms;

    // Bài tập sắp đến hạn (Upcoming Deadlines)
    private List<UpcomingAssignmentItem> upcomingAssignments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentQuizItem {
        private UUID sessionId;
        private String quizTitle;
        private double percentage;
        private LocalDateTime completedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrolledClassroomItem {
        private UUID classId;
        private String className;
        private String classCode;
        private Integer academicYear;
        private String status;
        private String teacherName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpcomingAssignmentItem {
        private UUID assignmentId;
        private String title;
        private String assignmentType;
        private LocalDateTime deadline;
    }
}
