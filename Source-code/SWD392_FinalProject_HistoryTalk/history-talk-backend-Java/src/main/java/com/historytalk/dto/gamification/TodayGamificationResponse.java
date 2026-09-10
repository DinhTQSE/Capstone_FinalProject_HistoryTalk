package com.historytalk.dto.gamification;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodayGamificationResponse {

    private String date;
    private Integer streakCount;
    private Boolean studiedToday;
    private Integer claimableTokens;
    private List<QuestItemDto> quests;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestItemDto {
        private String id;
        private String title;
        private String type;
        private Integer target;
        private Integer rewardTokens;
        private Integer progress;
        private Boolean completed;
        private Boolean claimed;
    }
}
