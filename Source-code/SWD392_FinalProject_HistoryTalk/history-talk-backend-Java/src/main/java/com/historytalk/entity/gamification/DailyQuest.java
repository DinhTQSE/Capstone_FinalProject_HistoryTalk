package com.historytalk.entity.gamification;

import com.historytalk.entity.enums.QuestType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "daily_quest", schema = "historical_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyQuest {

    @Id
    @Column(name = "quest_id", length = 50)
    private String questId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private QuestType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "target", nullable = false)
    private Integer target;

    @Column(name = "reward_tokens", nullable = false)
    private Integer rewardTokens;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
