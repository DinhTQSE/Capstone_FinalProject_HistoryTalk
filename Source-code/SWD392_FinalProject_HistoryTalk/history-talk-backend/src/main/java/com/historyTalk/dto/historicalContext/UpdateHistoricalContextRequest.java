package com.historyTalk.dto.historicalContext;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historyTalk.entity.enums.EventCategory;
import com.historyTalk.entity.enums.EventEra;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateHistoricalContextRequest {
    
    @JsonProperty("name")
    @Size(min = 3, max = 100, message = "Context name must be between 3 and 100 characters")
    private String name;
    
    @JsonProperty("description")
    @Size(min = 10, max = 5000, message = "Context description must be between 10 and 5000 characters")
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

    @JsonProperty("beforeTCN")
    private Boolean beforeTCN;

    @JsonProperty("location")
    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;

    @JsonProperty("imageUrl")
    @Size(max = 500, message = "Image URL must be at most 500 characters")
    private String imageUrl;

    @JsonProperty("videoUrl")
    @Size(max = 500, message = "Video URL must be at most 500 characters")
    private String videoUrl;
}
