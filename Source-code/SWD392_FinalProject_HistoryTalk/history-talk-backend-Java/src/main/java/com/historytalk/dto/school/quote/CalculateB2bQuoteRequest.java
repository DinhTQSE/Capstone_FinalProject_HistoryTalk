package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.EnterprisePackage;
import jakarta.validation.constraints.Max;
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
