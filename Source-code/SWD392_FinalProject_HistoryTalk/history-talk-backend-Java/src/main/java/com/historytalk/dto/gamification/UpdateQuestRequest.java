package com.historytalk.dto.gamification;

import com.historytalk.entity.enums.QuestType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuestRequest {
    private QuestType type;
    private String title;
    private Integer target;
    private Integer rewardTokens;
    private Integer orderIndex;
    private Boolean isActive;
}
