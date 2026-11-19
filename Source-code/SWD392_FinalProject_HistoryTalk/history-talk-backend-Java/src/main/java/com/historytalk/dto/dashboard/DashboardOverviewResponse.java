package com.historytalk.dto.dashboard;

import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardOverviewResponse {

    private UserOverview users;
    private RoleOverview roles;
    private ContentOverview content;
    private ChatOverview chat;
    private List<TopCharacterOverview> topCharacters;
    private List<TopCharacterOverview> topGlobalCharacters;
    private List<TopCharacterOverview> topLocalCharacters;
    private SystemHealthOverview systemHealth;
    private SchoolTokenOverview schoolTokenOverview;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SchoolTokenOverview {
        private UUID schoolId;
        private String schoolName;
        private String schoolCode;
        private EnterprisePackage packageType;
        private SchoolStatus status;
        private LocalDate subscriptionStartDate;
        private LocalDate subscriptionEndDate;
        private Boolean renewedForNextYear;
        private EnterprisePackage nextPackageType;
        private long totalClassrooms;
        private long totalTeachers;
        private long totalStudents;
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
        private SchoolAdminDashboardResponse.AssignmentStats assignmentStats;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopCharacterOverview {
        private String characterId;
        private String name;
        private String title;
        private String imageUrl;
        private long totalMessages;
        private long userMessages;
        private long aiMessages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserOverview {
        private long total;
        private long active;
        private long inactive;
        private long deleted;
        private long newToday;
        private long newThisMonth;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoleOverview {
        private long customers;
        private long contentAdmins;
        private long systemAdmins;
        private long schoolAdmins;
        private long teachers;
        private long schoolStudents;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContentOverview {
        private long historicalContexts;
        private long publishedHistoricalContexts;
        private long characters;
        private long publishedCharacters;
        private long documents;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatOverview {
        private long sessions;
        private long messages;
        private long messagesToday;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SystemHealthOverview {
        private String status;
        private LocalDateTime lastCheckedAt;
    }
}
