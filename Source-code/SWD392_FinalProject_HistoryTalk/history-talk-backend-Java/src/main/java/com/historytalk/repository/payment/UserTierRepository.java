package com.historytalk.repository.payment;

import com.historytalk.entity.payment.UserTier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserTierRepository extends JpaRepository<UserTier, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ut
            FROM UserTier ut
            WHERE ut.user.uid = :uid
              AND ut.isActive = true
              AND ut.deletedAt IS NULL
            ORDER BY ut.endTime DESC
            LIMIT 1
        """)
    Optional<UserTier> findActiveByUidForUpdate(@Param("uid") UUID uid);

    @Query("""
            SELECT COUNT(ut)
            FROM UserTier ut
            WHERE ut.isActive = true
              AND ut.deletedAt IS NULL
              AND ut.endTime >= :now
        """)
    long countActiveSubscriptions(@Param("now") LocalDateTime now);

    @Query("""
            SELECT COUNT(ut)
            FROM UserTier ut
            WHERE ut.isActive = true
              AND ut.deletedAt IS NULL
              AND ut.endTime >= :now
              AND ut.endTime < :until
        """)
    long countExpiringSoonSubscriptions(
            @Param("now") LocalDateTime now,
            @Param("until") LocalDateTime until);
}
