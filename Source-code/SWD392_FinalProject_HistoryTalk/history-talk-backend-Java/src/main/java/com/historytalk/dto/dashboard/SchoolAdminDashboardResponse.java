package com.historytalk.dto.dashboard;

import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolAdminDashboardResponse {
    private UUID schoolId;
    private String schoolName;
    private String schoolCode;

    // Chu kỳ Gói Trường học (01/09 - 31/05) & Trạng thái Gia hạn
    private EnterprisePackage packageType;
    private Integer recommendedStudents;
    private SchoolStatus status;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private Boolean renewedForNextYear;
    private EnterprisePackage nextPackageType;

    // Quy mô trường học
    private long totalClassrooms;
    private long totalTeachers;
    private long totalStudents;

    // Thống kê Quỹ Token Chung (Pure Shared Pool) & Định mức Ngày
    private int totalSchoolTokenQuota;
    private int unallocatedTokenQuota; // Quỹ chung còn lại của trường
    private int allocatedTokens;       // Tổng token đã tiêu thụ khỏi Quỹ chung
    private long tokensUsedByStudents;
    private int defaultDailyStudentQuota;
    private long pendingTokenBoostRequests;

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
