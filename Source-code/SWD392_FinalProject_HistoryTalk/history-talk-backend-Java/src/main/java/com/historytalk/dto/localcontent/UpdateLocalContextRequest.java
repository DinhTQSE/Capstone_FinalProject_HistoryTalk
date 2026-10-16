package com.historytalk.dto.localcontent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.EventCategory;
import com.historytalk.entity.enums.EventEra;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Request body for Teacher updating their own local context.
 * All fields are optional (partial update).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLocalContextRequest {

    @Size(min = 3, max = 100, message = "Tên bối cảnh phải từ 3 đến 100 ký tự")
    @JsonProperty("name")
    private String name;

    @Size(min = 10, max = 5000, message = "Mô tả bối cảnh phải từ 10 đến 5000 ký tự")
    @JsonProperty("description")
    private String description;

    @JsonProperty("era")
    private EventEra era;

    @JsonProperty("category")
    private EventCategory category;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("startYear")
    private Integer startYear;

    @JsonProperty("endYear")
    private Integer endYear;

    @JsonProperty("isBC")
    private Boolean isBC;

    @Size(max = 255)
    @JsonProperty("location")
    private String location;

    @Size(max = 500)
    @JsonProperty("imageUrl")
    private String imageUrl;

    @Size(max = 500)
    @JsonProperty("videoUrl")
    private String videoUrl;

    @JsonProperty("contentScope")
    private ContentScope contentScope;

    /** Latitude for map pin (optional). */
    @JsonProperty("latitude")
    private Double latitude;

    /** Longitude for map pin (optional). */
    @JsonProperty("longitude")
    private Double longitude;

    /** IDs of classrooms to assign (replaces existing assignments). */
    @JsonProperty("classIds")
    private List<String> classIds;
}
