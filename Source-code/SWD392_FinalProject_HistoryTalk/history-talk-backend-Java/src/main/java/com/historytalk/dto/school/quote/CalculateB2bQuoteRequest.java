package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.EnterprisePackage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculateB2bQuoteRequest {

    private EnterprisePackage selectedPackage;

    @NotNull(message = "Number of students is required")
    @Min(value = 1, message = "Number of students must be at least 1")
    @Max(value = 50000, message = "Number of students must not exceed 50,000")
    private Integer numberOfStudents;

    @NotNull(message = "Number of teachers is required")
    @Min(value = 1, message = "Number of teachers must be at least 1")
    @Max(value = 5000, message = "Number of teachers must not exceed 5,000")
    private Integer numberOfTeachers;

    private String promoCode;
}
