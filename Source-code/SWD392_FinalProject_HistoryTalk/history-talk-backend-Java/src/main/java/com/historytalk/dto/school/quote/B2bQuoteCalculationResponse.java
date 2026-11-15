package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.EnterprisePackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class B2bQuoteCalculationResponse {

    private EnterprisePackage selectedPackage;
    private Integer numberOfStudents;
    private Integer numberOfTeachers;
    private Integer academicWeeks;
    private Integer studentDaysPerWeek;
    private Integer teacherDaysPerWeek;
    private Integer studentDailyTokenLimit;
    private Integer teacherDailyTokenLimit;
    private Long studentTokensPerYear;
    private Long teacherTokensPerYear;
    private Long totalStudentTokens;
    private Long totalTeacherTokens;
    private Long totalTokenPool;
    private Double tokenUnitPriceVnd;
    private Long aiTokenCostVnd;
    private Long studentDbAndQuizFeeVnd;
    private Long teacherLmsAndStorageFeeVnd;
    private Long dbAndLmsCostVnd;
    private Long subtotalVnd;
    private Integer discountPercent;
    private Long discountAmountVnd;
    private Long finalPriceVnd;
    private Long averageMonthlyCostPerStudentVnd;
    private String promoCodeApplied;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
}
