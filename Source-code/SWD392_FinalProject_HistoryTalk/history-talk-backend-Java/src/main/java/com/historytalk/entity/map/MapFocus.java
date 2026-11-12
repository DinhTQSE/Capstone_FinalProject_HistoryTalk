package com.historytalk.entity.map;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a named geographic camera focus point for a historical context.
 * <p>
 * Admins define one or more focus points per context (e.g. "A1 Hill",
 * "Mường Thanh Valley"). The frontend uses the returned lat/lng/zoom_level
 * to pan and zoom the map when the context is opened.
 * <p>
 * Soft-delete is handled manually at the service layer (no {@code @Where} /
 * {@code @SQLDelete}) per the project hardening standard.
 */
@Entity
@Table(name = "map_focus", schema = "historical_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapFocus {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "focus_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID focusId;

    /**
     * Owning context id. Plain column (no FK / association) so map_focus stays
     * decoupled: purging a context never fails on focus rows, and orphan rows
     * never trigger lazy-load {@code EntityNotFoundException}.
     */
    @Column(name = "context_id", columnDefinition = "uuid", nullable = false)
    private UUID contextId;

    /** Display name shown in the map focus selector (e.g. "A1 Hill"). */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Optional description of the focus point. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** WGS-84 latitude of the camera target. */
    @Column(name = "latitude", nullable = false)
    private Double latitude;

    /** WGS-84 longitude of the camera target. */
    @Column(name = "longitude", nullable = false)
    private Double longitude;

    /**
     * Leaflet/Mapbox zoom level (1–20).
     * Default 8 gives a good regional overview.
     */
    @Builder.Default
    @Column(name = "zoom_level", nullable = false)
    private Double zoomLevel = 8.0;

    /**
     * Display order within the context's focus list (0-based).
     * Assigned and managed server-side; clients use {@code PATCH /reorder}
     * to change ordering.
     */
    @Builder.Default
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    /**
     * Whether this is the default (initial) camera position for the context.
     * Only one focus per context should have {@code is_default = true}.
     */
    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    /**
     * Whether this focus is visible to regular users.
     * Admins can hide a focus without deleting it.
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Creator's user id. Plain column, no FK / association. */
    @Column(name = "created_by", columnDefinition = "uuid", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
