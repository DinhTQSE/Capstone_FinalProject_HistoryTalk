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
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', COALESCE(transaction_date, created_at))
                    WHEN :bucket = 'week' THEN date_trunc('week', COALESCE(transaction_date, created_at))
                    ELSE date_trunc('day', COALESCE(transaction_date, created_at))
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) FILTER (WHERE status = 'SUCCESS') AS success,
            COUNT(*) FILTER (WHERE status = 'FAILED') AS failed
            FROM payment_transaction
            WHERE deleted_at IS NULL
              AND COALESCE(transaction_date, created_at) >= :from
              AND COALESCE(transaction_date, created_at) < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    java.util.List<DashboardTransactionTrendProjection> countTransactionsByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);
}
