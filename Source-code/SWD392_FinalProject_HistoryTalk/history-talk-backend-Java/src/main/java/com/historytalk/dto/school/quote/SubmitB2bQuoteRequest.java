package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.EnterprisePackage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class SubmitB2bQuoteRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email is invalid")
    @Size(max = 150, message = "Contact email must not exceed 150 characters")
    private String contactEmail;

    @NotBlank(message = "Phone number is required")
    @Size(max = 30, message = "Phone number must not exceed 30 characters")
    private String contactPhone;

    @NotBlank(message = "Job title is required")
    @Size(max = 150, message = "Job title must not exceed 150 characters")
    private String jobTitle;

    @NotBlank(message = "School name is required")
    @Size(max = 255, message = "School name must not exceed 255 characters")
    private String schoolName;

    @Size(max = 50, message = "School code must not exceed 50 characters")
    private String schoolCode;

    @Size(max = 500, message = "School address must not exceed 500 characters")
    private String schoolAddress;

    @Builder.Default
    private EnterprisePackage selectedPackage = EnterprisePackage.ENTERPRISE_CUSTOM;

    @NotNull(message = "Number of students is required")
    @Min(value = 1, message = "Number of students must be at least 1")
    @Max(value = 50000, message = "Number of students must not exceed 50,000")
    private Integer numberOfStudents;

    @NotNull(message = "Number of teachers is required")
    @Min(value = 1, message = "Number of teachers must be at least 1")
    @Max(value = 5000, message = "Number of teachers must not exceed 5,000")
    private Integer numberOfTeachers;

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

    @Size(max = 2000, message = "Message must not exceed 2,000 characters")
    private String message;

    @Size(max = 50, message = "Promo code must not exceed 50 characters")
    private String promoCode;

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;
}
