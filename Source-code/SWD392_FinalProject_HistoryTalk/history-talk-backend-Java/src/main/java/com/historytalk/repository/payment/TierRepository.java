package com.historytalk.repository.payment;

import com.historytalk.entity.payment.Tier;
import com.historytalk.repository.dashboard.DashboardTierUsersProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;
public interface TierRepository extends JpaRepository<Tier, UUID> {
    List<Tier> findByIsActiveTrueAndDeletedAtIsNull();

    long countByIsActiveTrueAndDeletedAtIsNull();

    @Query(value = """
            SELECT CAST(t.tier_id AS text) AS "tierId",
                   t.title AS "tierTitle",
                   COUNT(u.uid) AS users
            FROM tier t
            LEFT JOIN "user" u
              ON u.tier_id = t.tier_id
             AND u.deleted_at IS NULL
             AND u.role = 'CUSTOMER'
            WHERE t.deleted_at IS NULL
            GROUP BY t.tier_id, t.title
            ORDER BY users DESC, t.title ASC
            """, nativeQuery = true)
    List<DashboardTierUsersProjection> countCustomerUsersByTier();

    @Query(value = """
            SELECT COUNT(*)
            FROM "user" u
            JOIN tier t ON t.tier_id = u.tier_id
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
              AND t.deleted_at IS NULL
              AND t.amount > 0
            """, nativeQuery = true)
    long countCurrentPaidCustomers();

    @Query(value = """
            SELECT COUNT(*)
            FROM "user" u
            LEFT JOIN tier t ON t.tier_id = u.tier_id
            WHERE u.deleted_at IS NULL
              AND u.role = 'CUSTOMER'
              AND (u.tier_id IS NULL OR t.amount = 0 OR t.deleted_at IS NOT NULL)
            """, nativeQuery = true)
    long countCurrentFreeCustomers();
}
