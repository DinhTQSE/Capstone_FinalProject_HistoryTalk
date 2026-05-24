package com.historytalk.mapper.payment;

import com.historytalk.dto.payment.TierResponse;
import com.historytalk.entity.payment.Tier;
public class TierMapper {
    public static TierResponse toTierResponse(Tier tier) {
        return TierResponse.builder()
                .tierId(tier.getTierId().toString())
                .title(tier.getTitle())
                .amount(tier.getAmount())
                .noMonth(tier.getNoMonth())
                .limitedToken(tier.getLimitedToken())
                .isActive(tier.getIsActive())
                .build();
    }
}
