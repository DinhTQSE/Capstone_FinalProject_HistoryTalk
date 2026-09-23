package com.historytalk.entity.map;

import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "map_pin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapPin {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "pin_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID pinId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id", nullable = false)
    private HistoricalContext historicalContext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /**
     * Discriminator: "ADMIN" for admin-placed educational pins,
     * "USER" for personal study pins placed by regular users.
     */
    @Column(name = "pin_owner_type", nullable = false, length = 10)
    private String pinOwnerType;

    /** Display title shown on the map marker popup. Required for all pin types. */
    @Column(name = "label", nullable = false, length = 200)
    private String label;

    /** Detail text. Admins describe the historical event; users write personal notes. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Meaningful only for ADMIN pins: "ALLIED_FORCE" | "ENEMY_FORCE".
     * Null for USER pins.
     */
    @Column(name = "pin_type", length = 50)
    private String pinType;

    /** WGS-84 latitude. */
    @Column(name = "latitude", nullable = false)
    private Double latitude;

    /** WGS-84 longitude. */
    @Column(name = "longitude", nullable = false)
    private Double longitude;

    /**
     * The exact year on the context timeline this pin is anchored to.
     * Frontend timeline slider uses exact-match filter: pin_year = :year.
     */
    @Column(name = "pin_year", nullable = false)
    private Integer pinYear;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
