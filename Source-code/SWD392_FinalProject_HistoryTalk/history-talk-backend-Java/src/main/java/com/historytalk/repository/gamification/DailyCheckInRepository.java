package com.historytalk.repository.gamification;

import com.historytalk.entity.gamification.DailyCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyCheckInRepository extends JpaRepository<DailyCheckIn, UUID> {
    Optional<DailyCheckIn> findByUserUidAndDate(UUID userUid, LocalDate date);
    Optional<DailyCheckIn> findFirstByUserUidOrderByDateDesc(UUID userUid);
}
