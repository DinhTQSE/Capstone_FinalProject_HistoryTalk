package com.historytalk.dto.map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for PUT /api/v1/historical-contexts/{contextId}/map-pins/{pinId}.
 *
 * All fields are optional (partial update semantics):
 * - If a field is omitted (null), the existing value on the pin is preserved.
 * - To clear the description, send an empty string "".
 * - If label is provided, it must not be blank.
 */
@Data
public class UpdateMapPinRequest {

    /**
     * Detail text / battle commentary.
     * - Max 5000 characters.
     * - Sending "" clears the description (saved as null in DB).
     * - Omitting the field (null) keeps the existing description.
     */
    @Size(max = 5000, message = "description tối đa 5000 ký tự")
    private String description;

    /**
     * Marker popup title.
     * - If provided, must not be blank.
     * - Max 200 characters.
     */
    @Size(max = 200, message = "label tối đa 200 ký tự")
    private String label;

    /**
     * WGS-84 latitude. Valid range: -90 to 90.
     * Uses @DecimalMin/@DecimalMax because the field is Double, not an integer type.
     */
    @DecimalMin(value = "-90.0", message = "latitude phải >= -90")
    @DecimalMax(value = "90.0",  message = "latitude phải <= 90")
    private Double latitude;

    /**
     * WGS-84 longitude. Valid range: -180 to 180.
     * Uses @DecimalMin/@DecimalMax because the field is Double, not an integer type.
     */
    @DecimalMin(value = "-180.0", message = "longitude phải >= -180")
    @DecimalMax(value = "180.0",  message = "longitude phải <= 180")
    private Double longitude;

    /** The exact year on the context timeline this pin is anchored to. */
    private Integer pinYear;

    /**
     * GeoJSON LineString for the movement/route arrow.
     * If provided, will be validated to ensure type = "LineString".
     * Send {"type":"LineString","coordinates":[]} for a point-only pin.
     */
    private Object pathGeoJson;
}
