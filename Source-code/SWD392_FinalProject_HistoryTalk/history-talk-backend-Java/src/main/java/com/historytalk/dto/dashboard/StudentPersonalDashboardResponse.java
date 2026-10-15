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

    // Số dư & Tiêu thụ Token
    private int currentTokens;
    private long tokensUsedToday;

    // Học tập Quiz
    private long totalQuizzesAttempted;
    private double averageScorePercentage;
    private List<RecentQuizItem> recentQuizzes;

    // Danh sách lớp học đang theo
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
