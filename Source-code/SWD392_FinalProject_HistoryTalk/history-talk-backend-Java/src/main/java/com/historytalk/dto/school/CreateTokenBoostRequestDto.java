package com.historytalk.dto.school;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class CreateTokenBoostRequestDto {

    @NotNull(message = "Requested tokens must not be null")
    @Min(value = 100, message = "Requested tokens must be at least 100")
    @Max(value = 10000, message = "Requested tokens cannot exceed 10,000 per request")
    private Integer requestedTokens;

    @NotBlank(message = "Reason must not be blank")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
