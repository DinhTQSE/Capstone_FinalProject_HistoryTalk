package com.historytalk.dto.map;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MapPinResponse {

    private String pinId;
    private String contextId;

    /** UID of the user who placed this pin (admin or regular user). */
    private String createdBy;

    /** "ADMIN" or "USER" — indicates who owns this pin. */
    private String pinOwnerType;

    /** Marker popup title. */
    private String label;

    /** Detail text / personal note. */
    private String description;

    /**
     * "ALLIED_FORCE" | "ENEMY_FORCE" for ADMIN pins.
     * null for USER pins.
     */
    private String pinType;

    private Double latitude;
    private Double longitude;

    /** The exact year on the context timeline this pin is anchored to. */
    private Integer pinYear;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
