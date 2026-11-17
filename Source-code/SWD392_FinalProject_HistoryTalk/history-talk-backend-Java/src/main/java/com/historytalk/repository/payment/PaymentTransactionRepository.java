package com.historytalk.repository.payment;

import com.historytalk.entity.enums.PaymentTransactionStatus;
import com.historytalk.entity.payment.PaymentTransaction;
import com.historytalk.repository.dashboard.DashboardTransactionTrendProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<com.historytalk.entity.payment.PaymentTransaction> findByReference(String reference);

    boolean existsByReference(String reference);

    long countByDeletedAtIsNullAndStatus(PaymentTransactionStatus status);

    @Query(value = """
            SELECT COUNT(*)
            FROM payment_transaction pt
            JOIN payment_order po ON po.order_id = pt.order_id
            WHERE pt.deleted_at IS NULL
              AND po.deleted_at IS NULL
              AND pt.status = :status
              AND po.order_type = :orderType
            """, nativeQuery = true)
    long countByStatusAndOrderType(@Param("status") String status, @Param("orderType") String orderType);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', COALESCE(pt.transaction_date, pt.created_at))
                    WHEN :bucket = 'week' THEN date_trunc('week', COALESCE(pt.transaction_date, pt.created_at))
                    ELSE date_trunc('day', COALESCE(pt.transaction_date, pt.created_at))
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) FILTER (WHERE pt.status = 'SUCCESS') AS success,
            COUNT(*) FILTER (WHERE pt.status = 'FAILED') AS failed
            FROM payment_transaction pt
            WHERE pt.deleted_at IS NULL
              AND COALESCE(pt.transaction_date, pt.created_at) >= :from
              AND COALESCE(pt.transaction_date, pt.created_at) < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    java.util.List<DashboardTransactionTrendProjection> countTransactionsByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', COALESCE(pt.transaction_date, pt.created_at))
                    WHEN :bucket = 'week' THEN date_trunc('week', COALESCE(pt.transaction_date, pt.created_at))
                    ELSE date_trunc('day', COALESCE(pt.transaction_date, pt.created_at))
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) FILTER (WHERE pt.status = 'SUCCESS') AS success,
            COUNT(*) FILTER (WHERE pt.status = 'FAILED') AS failed
            FROM payment_transaction pt
            JOIN payment_order po ON po.order_id = pt.order_id
            WHERE pt.deleted_at IS NULL
              AND po.deleted_at IS NULL
              AND po.order_type = :orderType
              AND COALESCE(pt.transaction_date, pt.created_at) >= :from
              AND COALESCE(pt.transaction_date, pt.created_at) < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    java.util.List<DashboardTransactionTrendProjection> countTransactionsByPeriodAndOrderType(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("orderType") String orderType);
}
