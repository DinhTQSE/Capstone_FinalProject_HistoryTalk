package com.historytalk.dto.historicalContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class UpdateHistoricalContextRequest {
    
    @JsonProperty("name")
    @Size(min = 3, max = 100, message = "Tên bối cảnh phải từ 3 đến 100 ký tự")
    private String name;
    
    @JsonProperty("description")
    @Size(min = 10, max = 5000, message = "Mô tả bối cảnh phải từ 10 đến 5000 ký tự")
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

    @JsonProperty("location")
    @Size(max = 255, message = "Địa điểm tối đa 255 ký tự")
    private String location;

    @JsonProperty("imageUrl")
    @Size(max = 500, message = "URL hình ảnh tối đa 500 ký tự")
    private String imageUrl;

    @JsonProperty("videoUrl")
    @Size(max = 500, message = "URL Video tối đa 500 ký tự")
    private String videoUrl;

    /**
     * Optional. Full battleMap object to persist. Pass null to explicitly clear the battle map.
     * If the field is absent from the JSON body, the existing value is preserved (null-check pattern).
     * Validated against the isBattleMap schema in service before saving.
     */
    @JsonProperty("battleMap")
    private Object battleMap;

    /**
     * Optional story script. Absent key = keep the stored script; {"storyScript": null} = delete it.
     * Jackson calls the setter for an explicit null too, so {@link #storyScriptPresent} tells the two apart.
     */
    @JsonProperty("storyScript")
    private Object storyScript;

    /** True when the request body contained the "storyScript" key (even with a null value). */
    @JsonIgnore
    private boolean storyScriptPresent;

    @JsonProperty("isPublished")
    private Boolean isPublished;

    @JsonProperty("storyScript")
    public void setStoryScript(Object storyScript) {
        this.storyScript = storyScript;
        this.storyScriptPresent = true;
    }
}
