package com.historytalk.dto.gamification;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyCheckInResponse {
    private String date;
    private Integer streakCount;
    private Integer rewardTokens;
    private Integer tokenBalance;
    private Boolean alreadyCheckedInToday;
}
