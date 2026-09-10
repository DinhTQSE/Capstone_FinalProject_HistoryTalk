package com.historytalk.repository.gamification;

import com.historytalk.entity.gamification.DailyQuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DailyQuestRepository extends JpaRepository<DailyQuest, String> {
    List<DailyQuest> findByIsActiveTrueOrderByOrderIndexAsc();
    List<DailyQuest> findAllByOrderByOrderIndexAsc();
}
