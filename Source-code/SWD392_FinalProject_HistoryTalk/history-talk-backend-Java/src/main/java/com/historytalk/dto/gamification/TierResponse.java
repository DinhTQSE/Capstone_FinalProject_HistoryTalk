package com.historytalk.dto.gamification;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TierResponse {
    private UUID id;
    private String title;
    private Double amount;
    private Integer noMonth;
    private Integer limitedToken;
    private Boolean isActive;
}
