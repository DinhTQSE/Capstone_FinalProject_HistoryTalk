package com.historytalk.service.gamification;

import com.historytalk.dto.gamification.*;
import com.historytalk.entity.enums.QuestType;
import com.historytalk.entity.gamification.DailyQuest;

import java.util.List;

public interface GamificationService {
    TodayGamificationResponse getTodayState(String userId);
    ClaimQuestResponse claimQuestReward(String userId, String questId);
    DailyCheckInResponse dailyCheckIn(String userId);
    void recordProgress(String userId, QuestType questType);

    /** Trả về danh sách các ngày đã học (ISO string yyyy-MM-dd) trong tháng/năm chỉ định. */
    List<String> getStudyDays(String userId, int year, int month);

    // Staff/Admin methods
    List<DailyQuest> staffListQuests();
    DailyQuest staffGetQuest(String questId);
    DailyQuest staffUpdateQuest(String questId, UpdateQuestRequest request);
}
