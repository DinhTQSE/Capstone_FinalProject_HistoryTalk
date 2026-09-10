package com.historytalk.dto.gamification;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimQuestResponse {
    private String questId;
    private Integer rewardTokens;
    private Integer tokenBalance;
}
