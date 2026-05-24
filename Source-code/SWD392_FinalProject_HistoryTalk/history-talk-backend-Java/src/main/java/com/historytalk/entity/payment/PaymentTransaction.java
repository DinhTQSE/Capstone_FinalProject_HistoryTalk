package com.historytalk.entity.payment;

import com.historytalk.entity.enums.PaymentTransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An individual transaction/webhook event associated with a PaymentOrder.
 * Records each callback / IPN from the payment gateway.
 */
@Entity
@Table(name = "payment_transaction", schema = "historical_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "transaction_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private PaymentOrder paymentOrder;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "payment_link_id", length = 255)
    private String paymentLinkId;

    /** Raw JSON payload from the payment gateway webhook */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    /** e.g. SUCCESS, FAILED */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private PaymentTransactionStatus status = PaymentTransactionStatus.PENDING;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    /** Gateway transaction reference / bank reference code */
    @Column(name = "reference", length = 255)
    private String reference;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
