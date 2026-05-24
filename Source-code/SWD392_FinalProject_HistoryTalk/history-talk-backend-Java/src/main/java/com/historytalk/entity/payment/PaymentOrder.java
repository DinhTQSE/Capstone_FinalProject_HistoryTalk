package com.historytalk.entity.payment;

import com.historytalk.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A payment order created when a user purchases a Tier subscription.
 * Integrates with PayOS / VNPay checkout flow.
 */
@Entity
@Table(name = "payment_order", schema = "historical_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "order_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uid", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", nullable = false)
    private Tier tier;

    /** External order code (bigint) from payment gateway */
    @Column(name = "order_code", nullable = false)
    private Long orderCode;

    /** Amount in VND */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** Payment link ID from gateway (e.g. PayOS) */
    @Column(name = "payment_link_id", length = 255)
    private String paymentLinkId;

    /** URL to redirect user to complete payment */
    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    /** QR code string for in-app display */
    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    /** e.g. PENDING, PAID, CANCELLED, EXPIRED */
    @Column(name = "status", length = 50, nullable = false)
    private String status;

    /** Timestamp when payment was confirmed */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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

    @Builder.Default
    @OneToMany(mappedBy = "paymentOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentTransaction> transactions = new ArrayList<>();
}
