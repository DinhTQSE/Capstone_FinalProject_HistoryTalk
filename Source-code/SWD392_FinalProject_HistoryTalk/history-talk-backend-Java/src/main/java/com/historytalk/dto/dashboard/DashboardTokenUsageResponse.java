package com.historytalk.dto.dashboard;

import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardTokenUsageResponse {

    private TokenSummary summary;
    private List<TokenTrendPoint> trend;
    private List<TokenBalanceByTier> tokenBalanceByTier;
    private List<TopTokenUser> topUsersByTokenUsage;
    private SchoolTokenManagement schoolTokenManagement;
    private List<RoleTokenUsage> tokenUsageByRole;
    private List<ClassroomTokenUsageSummaryDto> topClassroomsByTokenUsage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SchoolTokenManagement {
        private UUID schoolId;
        private String schoolName;
        private String schoolCode;
        private EnterprisePackage packageType;
        private SchoolStatus status;
        private LocalDate subscriptionStartDate;
        private LocalDate subscriptionEndDate;
        private Boolean renewedForNextYear;
        private EnterprisePackage nextPackageType;
        private int totalSchoolTokenQuota;
        private int unallocatedTokenQuota;
        private int allocatedTokens;
        private double quotaUsagePercentage;
        private long tokensUsedByStudents;
        private long tokensUsedByTeachers;
        private long tokensUsedTotal;
        private double studentTokenUsagePercentage;
        private double teacherTokenUsagePercentage;
        private int defaultDailyStudentQuota;
        private int defaultDailyTeacherQuota;
        private long pendingTokenBoostRequests;
        private long topupPaidOrders;
        private long topupTokensPurchased;
        private long topupAmountPaid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoleTokenUsage {
        private String role;
        private long users;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private double usagePercentage;
        private int defaultDailyQuota;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenSummary {
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long remainingTokens;
        private double averageRemainingTokens;
        private long usersOutOfTokens;
        private long estimatedCost;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenTrendPoint {
        private String date;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenBalanceByTier {
        private String tierId;
        private String tierTitle;
        private long users;
        private long remainingTokens;
        private double averageRemainingTokens;
        private long usersOutOfTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopTokenUser {
        private String uid;
        private String userName;
        private String email;
        private String tierId;
        private String tierTitle;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long remainingTokens;
    }
}
