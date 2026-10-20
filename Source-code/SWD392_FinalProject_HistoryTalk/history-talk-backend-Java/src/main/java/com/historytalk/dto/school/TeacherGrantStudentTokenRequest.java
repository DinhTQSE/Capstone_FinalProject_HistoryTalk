package com.historytalk.dto.school;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherGrantStudentTokenRequest {

    @NotNull(message = "Tokens to grant must not be null")
    @Min(value = 50, message = "Tokens to grant must be at least 50")
    @Max(value = 50000, message = "Tokens to grant cannot exceed 50,000")
    private Integer tokens;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
