package com.historytalk.dto.school;

import com.historytalk.entity.enums.EnterprisePackage;
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
     * Optional reference date for testing/simulation; defaults to LocalDate.now().
     * Must be in May (month == 5) per academic subscription policy.
     */
    private LocalDate referenceDate;
}
