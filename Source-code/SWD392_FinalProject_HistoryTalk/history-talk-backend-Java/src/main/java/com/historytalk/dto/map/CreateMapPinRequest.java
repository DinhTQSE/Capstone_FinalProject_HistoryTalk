package com.historytalk.dto.map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMapPinRequest {

    /** Marker popup title. Required for all pin types. */
    @NotBlank(message = "Nhãn không được để trống")
    private String label;

    /**
     * Detail text. Admins: historical battle description.
     * Users: personal study note.
     */
    private String description;

    /**
     * ADMIN pins only: "ALLIED_FORCE" or "ENEMY_FORCE".
     * Ignored (set to null) for USER pins — validated in service.
     */
    private String pinType;

    /** WGS-84 latitude. */
    @NotNull(message = "Vĩ độ không được để trống")
    private Double latitude;

    /** WGS-84 longitude. */
    @NotNull(message = "Kinh độ không được để trống")
    private Double longitude;

    /** The exact year on the context timeline this pin belongs to. */
    @NotNull(message = "Năm của map pin không được để trống")
    private Integer pinYear;
}
