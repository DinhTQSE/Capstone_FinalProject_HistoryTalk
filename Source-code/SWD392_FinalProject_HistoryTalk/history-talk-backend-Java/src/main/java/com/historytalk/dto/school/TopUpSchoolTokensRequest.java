package com.historytalk.dto.school;

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
public class TopUpSchoolTokensRequest {

    @NotNull(message = "Additional tokens must not be null")
    @Min(value = 1000, message = "Top-up amount must be at least 1,000 tokens")
    private Integer additionalTokens;

    private String note;
}
