package com.historytalk.entity.payment;

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
 * Tier subscription plan (e.g. free, plus, pro).
 */
@Entity
@Table(name = "tier", schema = "historical_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tier {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "tier_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID tierId;

    /** e.g. "free", "plus", "pro" */
    @Column(name = "title", length = 50, nullable = false)
    private String title;

    /** Price in smallest currency unit (e.g. VND) */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** Subscription duration in months */
    @Column(name = "no_month", nullable = false)
    private Integer noMonth;

    /** Monthly token allowance */
    @Column(name = "limited_token", nullable = false)
    private Integer limitedToken;

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
    @OneToMany(mappedBy = "tier", fetch = FetchType.LAZY)
    private List<UserTier> userTiers = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "tier", fetch = FetchType.LAZY)
    private List<PaymentOrder> paymentOrders = new ArrayList<>();
}
