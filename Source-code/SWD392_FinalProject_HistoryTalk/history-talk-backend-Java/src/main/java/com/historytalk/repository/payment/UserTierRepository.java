package com.historytalk.repository.payment;

import com.historytalk.entity.payment.UserTier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTierRepository extends JpaRepository<UserTier, UUID> {

    /**
     * Returns the highest-priority active and non-expired subscription for a user.
     * Paid tiers (amount > 0) are preferred over free (amount = 0) via ORDER BY tier.amount DESC.
     * Use this for read-only profile mapping and subscription guard checks (no lock needed).
     */
    @Query("""
            SELECT ut FROM UserTier ut
            JOIN FETCH ut.tier t
            WHERE ut.user.uid = :uid
              AND ut.isActive = true
              AND ut.deletedAt IS NULL
              AND ut.endTime > :now
            ORDER BY t.amount DESC
            LIMIT 1
        """)
    Optional<UserTier> findCurrentActiveByUid(@Param("uid") UUID uid,
                                               @Param("now") LocalDateTime now);

    /**
     * Pessimistic-write version of findCurrentActiveByUid.
     * Used exclusively in PaymentWebhookService to prevent concurrent double-activation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ut FROM UserTier ut
            JOIN FETCH ut.tier t
            WHERE ut.user.uid = :uid
              AND ut.isActive = true
              AND ut.deletedAt IS NULL
              AND ut.endTime > :now
            ORDER BY t.amount DESC
            LIMIT 1
        """)
    Optional<UserTier> findCurrentActiveByUidForUpdate(@Param("uid") UUID uid,
                                                        @Param("now") LocalDateTime now);

    /**
     * Returns all PAID (tier.amount > 0) UserTier rows that are still flagged isActive
     * but whose endTime has already passed.
     * Used by PaymentExpiryScheduler to flip isActive = false after the period ends.
     * Free-tier rows (amount = 0) are intentionally excluded — they are never expired.
     */
    @Query("""
            SELECT ut FROM UserTier ut
            JOIN ut.tier t
            WHERE ut.isActive = true
              AND ut.deletedAt IS NULL
              AND ut.endTime <= :now
              AND t.amount > 0
        """)
    List<UserTier> findExpiredPaidSubscriptions(@Param("now") LocalDateTime now);

    /**
     * Count of all active, non-expired paid subscriptions.
     * Used by the dashboard.
     */
    @Query("""
            SELECT COUNT(ut)
            FROM UserTier ut
            JOIN ut.tier t
            WHERE ut.isActive = true
              AND ut.deletedAt IS NULL
              AND ut.endTime >= :now
              AND t.amount > 0
        """)
    long countActiveSubscriptions(@Param("now") LocalDateTime now);

    /**
     * Count of paid subscriptions expiring before :until.
     * Used by the dashboard for "expiring soon" metric.
     */
    @Query("""
            SELECT COUNT(ut)
            FROM UserTier ut
            JOIN ut.tier t
            WHERE ut.isActive = true
              AND ut.deletedAt IS NULL
              AND ut.endTime >= :now
              AND ut.endTime < :until
              AND t.amount > 0
        """)
    long countExpiringSoonSubscriptions(
            @Param("now") LocalDateTime now,
            @Param("until") LocalDateTime until);
}
