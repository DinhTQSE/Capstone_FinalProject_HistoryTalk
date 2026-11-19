package com.historytalk.repository.payment;

import com.historytalk.entity.enums.PaymentFulfillmentStatus;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.PaymentOrderType;
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
    @Query("SELECT o FROM PaymentOrder o LEFT JOIN FETCH o.tier WHERE o.user.uid = :uid ORDER BY o.createdAt DESC")
    List<PaymentOrder> findByUser_UidOrderByCreatedAtDesc(@Param("uid") UUID uid);

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

    @Query("""
        SELECT o
        FROM PaymentOrder o
        WHERE o.deletedAt IS NULL
          AND o.status = :paidStatus
          AND o.fulfillmentAttempts < :maxAttempts
          AND (
                o.fulfillmentStatus IN :retryStatuses
                OR (o.fulfillmentStatus = :processingStatus AND o.fulfillmentLockedAt < :staleBefore)
          )
        ORDER BY COALESCE(o.updatedAt, o.createdAt) ASC, o.createdAt ASC
    """)
    List<PaymentOrder> findOrdersNeedingFulfillmentRetry(
            @Param("paidStatus") PaymentOrderStatus paidStatus,
            @Param("retryStatuses") List<PaymentFulfillmentStatus> retryStatuses,
            @Param("processingStatus") PaymentFulfillmentStatus processingStatus,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable
    );

    /**
     * Admin query: returns all non-deleted payment orders, newest first.
     * Both filters are optional — passing null ignores that filter.
     * JOIN FETCH on user and tier prevents N+1 selects on lazy associations.
     */
    @Query(value = """
        SELECT o FROM PaymentOrder o
        JOIN FETCH o.user u
        LEFT JOIN FETCH o.tier t
        LEFT JOIN FETCH o.school s
        WHERE o.deletedAt IS NULL
          AND (:status IS NULL OR o.status = :status)
          AND (:userId IS NULL OR u.uid = :userId)
        ORDER BY o.createdAt DESC
    """,
    countQuery = """
        SELECT COUNT(o) FROM PaymentOrder o
        WHERE o.deletedAt IS NULL
          AND (:status IS NULL OR o.status = :status)
          AND (:userId IS NULL OR o.user.uid = :userId)
    """)
    Page<PaymentOrder> findAllForAdmin(
            @Param("status") PaymentOrderStatus status,
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query(value = """
        SELECT o FROM PaymentOrder o
        LEFT JOIN FETCH o.school s
        WHERE o.school.id = :schoolId
          AND o.orderType = :orderType
          AND o.deletedAt IS NULL
        ORDER BY o.createdAt DESC
    """,
    countQuery = """
        SELECT COUNT(o) FROM PaymentOrder o
        WHERE o.school.id = :schoolId
          AND o.orderType = :orderType
          AND o.deletedAt IS NULL
    """)
    Page<PaymentOrder> findBySchoolIdAndOrderTypeOrderByCreatedAtDesc(
            @Param("schoolId") UUID schoolId,
            @Param("orderType") PaymentOrderType orderType,
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
          AND o.orderType = :orderType
    """)
    Long sumAmountByStatusAndOrderType(
            @Param("status") PaymentOrderStatus status,
            @Param("orderType") PaymentOrderType orderType);

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

    @Query("""
        SELECT COALESCE(SUM(o.amount), 0)
        FROM PaymentOrder o
        WHERE o.deletedAt IS NULL
          AND o.status = :status
          AND o.orderType = :orderType
          AND o.paidAt >= :from
          AND o.paidAt < :to
    """)
    Long sumAmountByStatusAndOrderTypeAndPaidAtBetween(
            @Param("status") PaymentOrderStatus status,
            @Param("orderType") PaymentOrderType orderType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    long countByDeletedAtIsNullAndStatus(PaymentOrderStatus status);

    long countByDeletedAtIsNullAndStatusAndOrderType(PaymentOrderStatus status, PaymentOrderType orderType);

    long countByDeletedAtIsNullAndSchool_IdAndStatusAndOrderType(
            UUID schoolId, PaymentOrderStatus status, PaymentOrderType orderType);

    @Query("""
        SELECT COALESCE(SUM(o.amount), 0)
        FROM PaymentOrder o
        WHERE o.deletedAt IS NULL
          AND o.school.id = :schoolId
          AND o.status = :status
          AND o.orderType = :orderType
    """)
    Long sumAmountBySchoolIdAndStatusAndOrderType(
            @Param("schoolId") UUID schoolId,
            @Param("status") PaymentOrderStatus status,
            @Param("orderType") PaymentOrderType orderType);

    @Query("""
        SELECT COALESCE(SUM(o.topupTokens), 0)
        FROM PaymentOrder o
        WHERE o.deletedAt IS NULL
          AND o.school.id = :schoolId
          AND o.status = :status
          AND o.orderType = :orderType
    """)
    Long sumTopupTokensBySchoolIdAndStatusAndOrderType(
            @Param("schoolId") UUID schoolId,
            @Param("status") PaymentOrderStatus status,
            @Param("orderType") PaymentOrderType orderType);

    @Query(value = """
            SELECT status AS status,
                   COUNT(*) AS count
            FROM payment_order
            WHERE deleted_at IS NULL
            GROUP BY status
            """, nativeQuery = true)
    List<DashboardStatusCountProjection> countOrdersByStatus();

    @Query(value = """
            SELECT status AS status,
                   COUNT(*) AS count
            FROM payment_order
            WHERE deleted_at IS NULL
              AND order_type = :orderType
            GROUP BY status
            """, nativeQuery = true)
    List<DashboardStatusCountProjection> countOrdersByStatusAndOrderType(@Param("orderType") String orderType);

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
              AND order_type = :orderType
              AND paid_at IS NOT NULL
              AND paid_at >= :from
              AND paid_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardPeriodRevenueProjection> sumPaidRevenueByPeriodAndOrderType(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("orderType") String orderType);

    @Query(value = """
            SELECT MIN(CAST(t.tier_id AS text)) AS "tierId",
                   LOWER(t.title) AS "tierTitle",
                   COALESCE(SUM(o.amount), 0) AS revenue,
                   COUNT(o.order_id) AS "paidOrders"
            FROM tier t
            LEFT JOIN payment_order o
              ON o.tier_id = t.tier_id
             AND o.deleted_at IS NULL
             AND o.status = 'PAID'
             AND o.order_type = 'TIER_SUBSCRIPTION'
             AND o.paid_at IS NOT NULL
             AND o.paid_at >= :from
             AND o.paid_at < :to
            WHERE t.deleted_at IS NULL
            GROUP BY LOWER(t.title)
            ORDER BY revenue DESC, "paidOrders" DESC, "tierTitle" ASC
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
