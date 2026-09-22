package com.historytalk.dto.map;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MapFocusResponse {

    private String focusId;
    private String contextId;

    /** Display name of this focus point (e.g. "A1 Hill"). */
    private String name;

    /** Optional description. */
    private String description;

    /** WGS-84 latitude. */
    private Double latitude;

    /** WGS-84 longitude. */
    private Double longitude;

    /** Leaflet/Mapbox zoom level (1–20). */
    private Double zoomLevel;

    /** Display order within the context's focus list (0-based). */
    private Integer orderIndex;

    /** Whether this is the default (initial) camera position. */
    private Boolean isDefault;

    /** Whether this focus is visible to regular users. */
    private Boolean isActive;

    /** UID of the admin who created this focus point. */
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
