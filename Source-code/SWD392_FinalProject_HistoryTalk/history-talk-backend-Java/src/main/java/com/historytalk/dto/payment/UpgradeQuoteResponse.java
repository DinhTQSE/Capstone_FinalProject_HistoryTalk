package com.historytalk.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Price preview for buying a tier. When {@code upgrade} is true the user's active paid plan
 * is replaced and its unused part is deducted as {@code creditAmount}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeQuoteResponse {
    private String currentTierId;

    private String currentTierTitle;

    private Integer currentTierNoMonth;

    private String currentEndTime;

    private String targetTierId;

    private String targetTierTitle;

    private Integer targetTierNoMonth;

    private Integer originalAmount;

    private Integer creditAmount;

    private Integer amountToPay;

    private Boolean upgrade;
}
