package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.B2bQuoteStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateB2bQuoteStatusRequest {

    @NotNull(message = "Target status is required")
    private B2bQuoteStatus status;

    @Min(value = 0, message = "Custom discount percent must be between 0 and 100")
    @Max(value = 100, message = "Custom discount percent must be between 0 and 100")
    private Integer customDiscountPercent;

    @Size(max = 2000, message = "Admin note must not exceed 2,000 characters")
    private String adminNote;

    @Min(value = 1, message = "Academic weeks must be at least 1")
    @Max(value = 52, message = "Academic weeks cannot exceed 52")
    private Integer academicWeeks;

    @Min(value = 100, message = "Student daily token limit must be at least 100")
    @Max(value = 100000, message = "Student daily token limit cannot exceed 100,000")
    private Integer studentDailyTokenLimit;

    @Min(value = 100, message = "Teacher daily token limit must be at least 100")
    @Max(value = 100000, message = "Teacher daily token limit cannot exceed 100,000")
    private Integer teacherDailyTokenLimit;

    @Min(value = 1, message = "Student days per week must be between 1 and 7")
    @Max(value = 7, message = "Student days per week must be between 1 and 7")
    private Integer studentDaysPerWeek;

    @Min(value = 1, message = "Teacher days per week must be between 1 and 7")
    @Max(value = 7, message = "Teacher days per week must be between 1 and 7")
    private Integer teacherDaysPerWeek;

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;
}
