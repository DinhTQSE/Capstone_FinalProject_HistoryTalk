package com.historytalk.repository.payment;

import com.historytalk.entity.payment.Tier;
import com.historytalk.repository.dashboard.DashboardTierUsersProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;
public interface TierRepository extends JpaRepository<Tier, UUID> {
    List<Tier> findByIsActiveTrueAndDeletedAtIsNull();
    java.util.Optional<Tier> findByTitleIgnoreCaseAndIsActiveTrueAndDeletedAtIsNull(String title);

    long countByIsActiveTrueAndDeletedAtIsNull();

    @Query(value = """
            SELECT CAST(t.tier_id AS text) AS "tierId",
                   t.title AS "tierTitle",
                   COUNT(DISTINCT u.uid) AS users
            FROM tier t
            LEFT JOIN user_tier ut
              ON ut.tier_id = t.tier_id
             AND ut.deleted_at IS NULL
             AND ut.is_active = true
             AND ut.end_time > CURRENT_TIMESTAMP
             AND t.amount > 0
            LEFT JOIN "user" u
              ON (
                  (t.amount > 0 AND u.uid = ut.uid)
                  OR
                  (t.amount = 0 AND NOT EXISTS (
                      SELECT 1
                      FROM user_tier ut2
                      JOIN tier t2 ON t2.tier_id = ut2.tier_id
                      WHERE ut2.uid = u.uid
                        AND ut2.deleted_at IS NULL
                        AND ut2.is_active = true
                        AND ut2.end_time > CURRENT_TIMESTAMP
                        AND t2.deleted_at IS NULL
                        AND t2.amount > 0
                  ))
              )
             AND u.deleted_at IS NULL
             AND u.role = 'CUSTOMER'
            WHERE t.deleted_at IS NULL
            GROUP BY t.tier_id, t.title
            ORDER BY users DESC, t.title ASC
            """, nativeQuery = true)
    List<DashboardTierUsersProjection> countCustomerUsersByTier();

    @Query(value = """
            SELECT COUNT(DISTINCT u.uid)
            FROM "user" u
            JOIN user_tier ut ON ut.uid = u.uid
            JOIN tier t ON t.tier_id = ut.tier_id
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
              AND ut.deleted_at IS NULL
              AND ut.is_active = true
              AND ut.end_time > CURRENT_TIMESTAMP
              AND t.deleted_at IS NULL
              AND t.amount > 0
            """, nativeQuery = true)
    long countCurrentPaidCustomers();

    @Query(value = """
            SELECT COUNT(DISTINCT u.uid)
            FROM "user" u
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
              AND NOT EXISTS (
                  SELECT 1
                  FROM user_tier ut
                  JOIN tier t ON t.tier_id = ut.tier_id
                  WHERE ut.uid = u.uid
                    AND ut.deleted_at IS NULL
                    AND ut.is_active = true
                    AND ut.end_time > CURRENT_TIMESTAMP
                    AND t.deleted_at IS NULL
                    AND t.amount > 0
              )
            """, nativeQuery = true)
    long countCurrentFreeCustomers();
}
