package com.historytalk.dto.school;

import com.historytalk.entity.enums.EnterprisePackage;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewSchoolSubscriptionRequest {

    @NotNull(message = "Package type must not be null")
    private EnterprisePackage packageType;

    /**
     * Optional custom token quota when renewing with ENTERPRISE_CUSTOM.
     * If not provided and the current school is already ENTERPRISE_CUSTOM,
     * the system will retain the school's base contracted quota.
     */
    @Min(value = 1, message = "Custom token quota must be greater than 0")
    private Integer customTokenQuota;

    @Min(value = 1, message = "Maximum students must be greater than 0")
    private Integer maxStudents;

    @Min(value = 1, message = "Maximum teachers must be greater than 0")
    private Integer maxTeachers;

    @Min(value = 1, message = "Default daily student quota must be greater than 0")
    private Integer defaultDailyStudentQuota;

    @Min(value = 1, message = "Default daily teacher quota must be greater than 0")
    private Integer defaultDailyTeacherQuota;

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;

    /**
     * Optional reference date for testing/simulation; defaults to LocalDate.now().
     * Must be in May (month == 5) per academic subscription policy.
     */
    private LocalDate referenceDate;
}
