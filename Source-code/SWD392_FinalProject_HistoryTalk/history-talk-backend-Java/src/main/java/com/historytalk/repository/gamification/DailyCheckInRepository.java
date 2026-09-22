package com.historytalk.repository.gamification;

import com.historytalk.entity.gamification.DailyCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyCheckInRepository extends JpaRepository<DailyCheckIn, UUID> {
    Optional<DailyCheckIn> findByUserUidAndDate(UUID userUid, LocalDate date);
    Optional<DailyCheckIn> findFirstByUserUidOrderByDateDesc(UUID userUid);

    /** Trả về danh sách ngày đã học của user trong khoảng [from, to] (dùng cho lịch tháng). */
    @Query("SELECT c.date FROM DailyCheckIn c WHERE c.user.uid = :userUid AND c.date BETWEEN :from AND :to ORDER BY c.date ASC")
    List<LocalDate> findAllDatesInRange(@Param("userUid") UUID userUid,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);
}
