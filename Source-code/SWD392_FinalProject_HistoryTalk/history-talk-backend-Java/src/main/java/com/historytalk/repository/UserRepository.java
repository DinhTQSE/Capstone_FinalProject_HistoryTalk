package com.historytalk.repository;

import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.repository.dashboard.DashboardPeriodCountProjection;
import com.historytalk.repository.dashboard.DashboardTokenBalanceByTierProjection;
import com.historytalk.repository.dashboard.DashboardTokenBalanceSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserNameIgnoreCase(String userName);

    Optional<User> findByPasswordResetTokenHash(String tokenHash);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserNameIgnoreCase(String userName);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.token = CASE WHEN (u.token - :tokensToDeduct) < 0 THEN 0 ELSE (u.token - :tokensToDeduct) END WHERE u.uid = :userId")
    int deductTokens(@Param("userId") UUID userId, @Param("tokensToDeduct") Integer tokensToDeduct);

    @Query("SELECT COUNT(u) FROM User u")
    long countAllUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL")
    long countActiveUsers();

    default long countInactiveUsers() {
        return 0L;
    }

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NOT NULL")
    long countDeletedUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.role = :role")
    long countActiveUsersByRole(@Param("role") UserRole role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.lastActiveDate >= :from")
    long countRecentlyActiveUsers(@Param("from") LocalDateTime from);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', created_at)
                    ELSE date_trunc('day', created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM "user"
            WHERE created_at >= :from
              AND created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countNewUsersByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', last_active_date)
                    WHEN :bucket = 'week' THEN date_trunc('week', last_active_date)
                    ELSE date_trunc('day', last_active_date)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS count
            FROM "user"
            WHERE deleted_at IS NULL
              AND last_active_date IS NOT NULL
              AND last_active_date >= :from
              AND last_active_date < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodCountProjection> countActiveUsersByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(u.token, 0)), 0) AS "remainingTokens",
                   COALESCE(AVG(COALESCE(u.token, 0)), 0) AS "averageRemainingTokens",
                   COALESCE(SUM(CASE WHEN COALESCE(u.token, 0) <= 0 THEN 1 ELSE 0 END), 0) AS "usersOutOfTokens"
            FROM "user" u
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
            """, nativeQuery = true)
    DashboardTokenBalanceSummaryProjection getTokenBalanceSummary();

    @Query(value = """
            WITH ranked_tiers AS (
                SELECT ut.uid, ut.tier_id,
                       ROW_NUMBER() OVER(PARTITION BY ut.uid ORDER BY t.amount DESC) as rn
                FROM user_tier ut
                JOIN tier t ON t.tier_id = ut.tier_id
                WHERE ut.deleted_at IS NULL
                  AND ut.is_active = true
                  AND ut.end_time > CURRENT_TIMESTAMP
                  AND t.deleted_at IS NULL
            )
            SELECT CAST(t.tier_id AS text) AS "tierId",
                   COALESCE(t.title, 'free') AS "tierTitle",
                   COUNT(u.uid) AS users,
                   COALESCE(SUM(COALESCE(u.token, 0)), 0) AS "remainingTokens",
                   COALESCE(AVG(COALESCE(u.token, 0)), 0) AS "averageRemainingTokens",
                   COALESCE(SUM(CASE WHEN COALESCE(u.token, 0) <= 0 THEN 1 ELSE 0 END), 0) AS "usersOutOfTokens"
            FROM "user" u
            LEFT JOIN ranked_tiers rt ON rt.uid = u.uid AND rt.rn = 1
            LEFT JOIN tier t ON t.tier_id = rt.tier_id
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
            GROUP BY t.tier_id, t.title
            ORDER BY users DESC, "tierTitle" ASC
            """, nativeQuery = true)
    List<DashboardTokenBalanceByTierProjection> countTokenBalanceByTier();

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.deletedAt = null WHERE u.deletedAt IS NOT NULL")
    int restoreAllUsers();
}
