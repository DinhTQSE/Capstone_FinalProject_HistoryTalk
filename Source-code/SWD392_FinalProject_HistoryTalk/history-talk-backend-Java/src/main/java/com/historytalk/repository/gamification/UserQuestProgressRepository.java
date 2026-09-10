package com.historytalk.repository.gamification;

import com.historytalk.entity.gamification.UserQuestProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserQuestProgressRepository extends JpaRepository<UserQuestProgress, UUID> {
    List<UserQuestProgress> findByUserUidAndDate(UUID userUid, LocalDate date);
    Optional<UserQuestProgress> findByUserUidAndDateAndQuestId(UUID userUid, LocalDate date, String questId);
}
