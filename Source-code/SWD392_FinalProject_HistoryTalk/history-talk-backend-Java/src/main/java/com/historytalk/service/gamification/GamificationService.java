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
    
    // Staff/Admin methods
    List<DailyQuest> staffListQuests();
    DailyQuest staffGetQuest(String questId);
    DailyQuest staffUpdateQuest(String questId, UpdateQuestRequest request);
}
