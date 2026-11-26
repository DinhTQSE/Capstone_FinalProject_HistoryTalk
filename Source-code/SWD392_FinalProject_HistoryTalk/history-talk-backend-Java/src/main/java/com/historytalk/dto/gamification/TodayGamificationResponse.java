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
    private Integer longestStreak;
    private Integer totalStudyDays;
    private Boolean studiedToday;
    private Integer claimableTokens;
    /** Monday to Sunday of the current week. */
    private List<WeekDayDto> week;
    private List<QuestItemDto> quests;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WeekDayDto {
        private String date;
        /** 0 = Monday ... 6 = Sunday. */
        private Integer weekday;
        private Boolean studied;
        private Boolean isToday;
    }

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
