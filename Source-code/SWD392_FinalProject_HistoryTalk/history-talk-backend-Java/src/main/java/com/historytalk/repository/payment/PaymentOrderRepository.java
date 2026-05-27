package com.historytalk.repository.payment;

import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
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
}
