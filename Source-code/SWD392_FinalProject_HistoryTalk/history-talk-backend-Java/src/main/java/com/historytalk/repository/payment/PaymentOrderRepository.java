package com.historytalk.repository.payment;

import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.repository.dashboard.DashboardPeriodRevenueProjection;
import com.historytalk.repository.dashboard.DashboardStatusCountProjection;
import com.historytalk.repository.dashboard.DashboardTierRevenueProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {
    Optional<PaymentOrder> findByOrderCode(Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PaymentOrder o WHERE o.orderCode = :orderCode")
    Optional<PaymentOrder> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    // Fixed: was findByUser_UidOrderByCreateAtDesc (typo: createAt → createdAt)
    List<PaymentOrder> findByUser_UidOrderByCreatedAtDesc(UUID uid);

    // Fixed: was OffsetDateTime, but PaymentOrder.expiredAt is LocalDateTime
    @Query("""
        SELECT o
        FROM PaymentOrder o
        WHERE o.status = :status
          AND o.expiredAt <= :now
          AND o.deletedAt IS NULL
    """)
    List<PaymentOrder> findExpiredPendingOrders(
            @Param("status") PaymentOrderStatus status,
            @Param("now") LocalDateTime now
    );

    /**
     * Admin query: returns all non-deleted payment orders, newest first.
     * Both filters are optional — passing null ignores that filter.
     * JOIN FETCH on user and tier prevents N+1 selects on lazy associations.
     */
    @Query("""
        SELECT o FROM PaymentOrder o
        JOIN FETCH o.user u
        JOIN FETCH o.tier t
        WHERE o.deletedAt IS NULL
          AND (:status IS NULL OR o.status = :status)
          AND (:userId IS NULL OR u.uid = :userId)
        ORDER BY o.createdAt DESC
    """)
    Page<PaymentOrder> findAllForAdmin(
            @Param("status") PaymentOrderStatus status,
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query("""
        SELECT COALESCE(SUM(o.amount), 0)
        FROM PaymentOrder o
        WHERE o.deletedAt IS NULL
          AND o.status = :status
    """)
    Long sumAmountByStatus(@Param("status") PaymentOrderStatus status);

    @Query("""
        SELECT COALESCE(SUM(o.amount), 0)
        FROM PaymentOrder o
        WHERE o.deletedAt IS NULL
          AND o.status = :status
          AND o.paidAt >= :from
          AND o.paidAt < :to
    """)
    Long sumAmountByStatusAndPaidAtBetween(
            @Param("status") PaymentOrderStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    long countByDeletedAtIsNullAndStatus(PaymentOrderStatus status);

    @Query(value = """
            SELECT status AS status,
                   COUNT(*) AS count
            FROM payment_order
            WHERE deleted_at IS NULL
            GROUP BY status
            """, nativeQuery = true)
    List<DashboardStatusCountProjection> countOrdersByStatus();

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', paid_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', paid_at)
                    ELSE date_trunc('day', paid_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COALESCE(SUM(amount), 0) AS revenue,
            COUNT(*) AS "paidOrders"
            FROM payment_order
            WHERE deleted_at IS NULL
              AND status = 'PAID'
              AND paid_at IS NOT NULL
              AND paid_at >= :from
              AND paid_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodRevenueProjection> sumPaidRevenueByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT CAST(t.tier_id AS text) AS "tierId",
                   t.title AS "tierTitle",
                   COALESCE(SUM(o.amount), 0) AS revenue,
                   COUNT(o.order_id) AS "paidOrders"
            FROM tier t
            LEFT JOIN payment_order o
              ON o.tier_id = t.tier_id
             AND o.deleted_at IS NULL
             AND o.status = 'PAID'
             AND o.paid_at IS NOT NULL
             AND o.paid_at >= :from
             AND o.paid_at < :to
            WHERE t.deleted_at IS NULL
            GROUP BY t.tier_id, t.title
            ORDER BY revenue DESC, "paidOrders" DESC, t.title ASC
            """, nativeQuery = true)
    List<DashboardTierRevenueProjection> sumPaidRevenueByTier(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT COUNT(DISTINCT o.uid)
            FROM payment_order o
            JOIN tier t ON t.tier_id = o.tier_id
            WHERE o.deleted_at IS NULL
              AND o.status = 'PAID'
              AND t.amount > 0
            """, nativeQuery = true)
    long countDistinctPaidCustomers();
}
