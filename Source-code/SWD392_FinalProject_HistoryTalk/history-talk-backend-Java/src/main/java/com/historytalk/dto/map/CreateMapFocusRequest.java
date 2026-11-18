package com.historytalk.dto.map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMapFocusRequest {

    /** Display name for this focus point (e.g. "A1 Hill"). */
    @NotBlank(message = "Tên không được để trống")
    private String name;

    /** Optional description of the geographic focus point. */
    private String description;

    /** WGS-84 latitude of the camera target. */
    @NotNull(message = "Vĩ độ không được để trống")
    private Double latitude;

    /** WGS-84 longitude of the camera target. */
    @NotNull(message = "Kinh độ không được để trống")
    private Double longitude;

    /**
     * Leaflet/Mapbox zoom level. Must be between 1.0 and 20.0.
     * Defaults to 8.0 on the server if omitted.
     */
    @DecimalMin(value = "1.0", message = "Mức zoom phải từ 1.0 trở lên")
    @DecimalMax(value = "20.0", message = "Mức zoom tối đa là 20.0")
    private Double zoomLevel;

    /**
     * Whether this focus should become the default (initial) camera position.
     * Defaults to false if omitted. Setting true will unset any existing default.
     */
    private Boolean isDefault;
}
