package com.historytalk.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierResponse {
    private String tierId;

    private String title;

    private Integer amount;

    private Integer noMonth;

    private Integer limitedToken;

    private Boolean isActive;
}
