package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolAdminDashboardResponse {
    private UUID schoolId;
    private String schoolName;
    private String schoolCode;

    // Quy mô trường học
    private long totalClassrooms;
    private long totalTeachers;
    private long totalStudents;

    // Thống kê Token
    private int totalSchoolTokenQuota;
    private int unallocatedTokenQuota;
    private int allocatedTokens;
    private long tokensUsedByStudents;

    // Tỷ lệ hoàn thành bài tập toàn trường
    private AssignmentStats assignmentStats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignmentStats {
        private long totalSubmissions;
        private double completedRate;
        private double lateSubmittedRate;
        private double notStartedRate;
    }
}
