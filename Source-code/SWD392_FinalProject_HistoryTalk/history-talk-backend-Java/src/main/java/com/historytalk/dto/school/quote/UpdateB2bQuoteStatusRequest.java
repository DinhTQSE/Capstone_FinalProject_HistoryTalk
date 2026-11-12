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

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;
}
