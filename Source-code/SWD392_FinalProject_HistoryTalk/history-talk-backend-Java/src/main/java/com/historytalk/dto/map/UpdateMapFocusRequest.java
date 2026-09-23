package com.historytalk.dto.map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

/**
 * PATCH-semantics update request — all fields are optional.
 * Only non-null fields are applied to the existing entity.
 * <p>
 * Note: {@code orderIndex} is NOT editable here.
 * Use {@code PATCH /reorder} to change the display order.
 */
@Data
public class UpdateMapFocusRequest {

    /** New display name. Unchanged if null. */
    private String name;

    /** New description. Unchanged if null. */
    private String description;

    /** New latitude. Unchanged if null. */
    private Double latitude;

    /** New longitude. Unchanged if null. */
    private Double longitude;

    /**
     * New zoom level (1.0–20.0). Unchanged if null.
     */
    @DecimalMin(value = "1.0", message = "Zoom level must be at least 1.0")
    @DecimalMax(value = "20.0", message = "Zoom level must be at most 20.0")
    private Double zoomLevel;

    /**
     * Set to true to make this the default focus (unsets any existing default).
     * Unchanged if null.
     */
    private Boolean isDefault;

    /**
     * Set to false to hide this focus from regular users without deleting it.
     * Unchanged if null.
     */
    private Boolean isActive;
}
