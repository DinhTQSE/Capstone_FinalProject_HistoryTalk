package com.historytalk.repository.payment;

import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {
    Optional<PaymentOrder> findByOrderCode(Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PaymentOrder o WHERE o.orderCode = :orderCode")
    Optional<PaymentOrder> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    List<PaymentOrder> findByUser_UidOrderByCreateAtDesc(UUID uid);

    @Query("""
        SELECT o
        FROM PaymentOrder o
        WHERE o.status = :status
          AND o.expiredAt <= :now
          AND o.deletedAt IS NULL
    """)
    List<PaymentOrder> findExpiredPendingOrders(
            @Param("status") PaymentOrderStatus status,
            @Param("now") OffsetDateTime now
    );
}
