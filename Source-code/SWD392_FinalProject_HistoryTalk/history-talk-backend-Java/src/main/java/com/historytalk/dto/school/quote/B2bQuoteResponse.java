package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.B2bQuoteStatus;
import com.historytalk.entity.enums.EnterprisePackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class B2bQuoteResponse {

    private UUID id;
    private String quoteCode;
    private String firstName;
    private String lastName;
    private String fullName;
    private String contactEmail;
    private String contactPhone;
    private String jobTitle;
    private String schoolName;
    private String schoolCode;
    private String schoolAddress;
    private EnterprisePackage selectedPackage;
    private Integer numberOfStudents;
    private Integer numberOfTeachers;
    private Integer academicWeeks;
    private Integer studentDaysPerWeek;
    private Integer teacherDaysPerWeek;
    private Integer studentDailyTokenLimit;
    private Integer teacherDailyTokenLimit;
    private Long totalTokenPool;
    private Long aiTokenCostVnd;
    private Long dbAndLmsCostVnd;
    private Long subtotalVnd;
    private Integer discountPercent;
    private Long discountAmountVnd;
    private Long finalPriceVnd;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private String promoCode;
    private String message;
    private B2bQuoteStatus status;
    private String adminNote;
    private UUID reviewedByUid;
    private LocalDateTime reviewedAt;
    private UUID createdSchoolId;
    private UUID createdSchoolAdminUid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
