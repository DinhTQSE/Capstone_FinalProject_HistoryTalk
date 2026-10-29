package com.historytalk.dto.historicalContext;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.EventCategory;
import com.historytalk.entity.enums.EventEra;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateHistoricalContextRequest {
    
    @JsonProperty("name")
    @NotBlank(message = "Yêu cầu tên bối cảnh")
    @Size(min = 3, max = 100, message = "Tên bối cảnh phải từ 3 đến 100 ký tự")
    private String name;
    
    @JsonProperty("description")
    @NotBlank(message = "Yêu cầu mô tả bối cảnh")
    @Size(min = 10, max = 5000, message = "Mô tả bối cảnh phải từ 10 đến 5000 ký tự")
    private String description;

    // Era, year and location place the context on the timeline, era filters and the battle map,
    // so the staff form requires them; the API enforces the same.
    @JsonProperty("era")
    @NotNull(message = "Yêu cầu thời đại")
    private EventEra era;

    @JsonProperty("category")
    private EventCategory category;

    @JsonProperty("year")
    @NotNull(message = "Yêu cầu năm diễn ra")
    private Integer year;

    @JsonProperty("startYear")
    private Integer startYear;

    @JsonProperty("endYear")
    private Integer endYear;

    @JsonProperty("isBC")
    private Boolean isBC = false;

    @JsonProperty("location")
    @NotBlank(message = "Yêu cầu địa điểm")
    @Size(max = 255, message = "Địa điểm tối đa 255 ký tự")
    private String location;

    @JsonProperty("imageUrl")
    @Size(max = 500, message = "URL hình ảnh tối đa 500 ký tự")
    private String imageUrl;

    @JsonProperty("videoUrl")
    @Size(max = 500, message = "URL Video tối đa 500 ký tự")
    private String videoUrl;

    /**
     * Optional. Full battleMap object (version, mode, imageUrl, factions[], symbols[]).
     * See map-api.md §5 for the full schema.
     * If null, no battle map is stored. Validated in service before persisting.
     * Only CONTENT_ADMIN are permitted to set this.
     */
    @JsonProperty("battleMap")
    private Object battleMap;

    @JsonProperty("isPublished")
    private Boolean isPublished = false;

    public Boolean getIsDraft() {
        return isPublished == null ? null : !isPublished;
    }
}
