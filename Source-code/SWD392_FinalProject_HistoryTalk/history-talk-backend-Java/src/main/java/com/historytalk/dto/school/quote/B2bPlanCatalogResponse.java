package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.EnterprisePackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class B2bPlanCatalogResponse {

    private Double tokenUnitPriceVnd;
    private Integer academicWeeksPerYear;
    private Integer studentDailyTokenLimit;
    private Integer studentActiveDaysPerWeek;
    private Long studentTokensPerYear;
    private Long studentAiTokenCostVnd;
    private Long studentDbAndQuizFeeVnd;
    private Long studentTotalAnnualCostVnd;

    private Integer teacherDailyTokenLimit;
    private Integer teacherActiveDaysPerWeek;
    private Long teacherTokensPerYear;
    private Long teacherAiTokenCostVnd;
    private Long teacherLmsAndStorageFeeVnd;
    private Long teacherTotalAnnualCostVnd;

    private List<DiscountTierItem> discountTiers;
    private List<FixedPlanItem> plans;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountTierItem {
        private Integer minStudents;
        private Integer maxStudents;
        private Integer discountPercent;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixedPlanItem {
        private EnterprisePackage packageType;
        private String displayName;
        private Integer maxStudents;
        private Integer maxTeachers;
        private Integer studentDailyTokenLimit;
        private Integer teacherDailyTokenLimit;
        private Long totalTokenPool;
        private Long aiTokenCostVnd;
        private Long dbAndLmsCostVnd;
        private Long subtotalVnd;
        private Integer discountPercent;
        private Long discountAmountVnd;
        private Long finalPriceVnd;
        private Long averageMonthlyCostPerStudentVnd;
        private Boolean popular;
        private List<String> highlights;
    }
}
