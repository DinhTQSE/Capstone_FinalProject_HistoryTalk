package com.historytalk.dto.school;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTokenBoostRequestDto {

    @NotBlank(message = "Action must be APPROVE or REJECT")
    private String action;

    @Min(value = 100, message = "Approved tokens must be at least 100")
    private Integer approvedTokens;

    @Size(max = 500, message = "Review note must not exceed 500 characters")
    private String reviewNote;
}
