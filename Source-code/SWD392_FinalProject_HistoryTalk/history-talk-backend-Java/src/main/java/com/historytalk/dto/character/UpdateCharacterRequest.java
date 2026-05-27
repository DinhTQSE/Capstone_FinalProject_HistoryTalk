package com.historytalk.dto.character;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCharacterRequest {

    @JsonProperty("name")
    @Size(min = 2, max = 100, message = "Character name must be between 2 and 100 characters")
    private String name;

    @JsonProperty("title")
    @Size(max = 150, message = "Title must not exceed 150 characters")
    private String title;

    @JsonProperty("background")
    private String background;

    @JsonProperty("image")
    @Size(max = 255, message = "Image URL must not exceed 255 characters")
    private String imageUrl;

    @JsonProperty("modelUrl")
    @Size(max = 500, message = "3D model URL must not exceed 500 characters")
    private String modelUrl;

    @JsonProperty("personality")
    @Size(max = 500, message = "Personality must not exceed 500 characters")
    private String personality;

    @JsonProperty("bornYear")
    private Integer bornYear;

    @JsonProperty("bornMonth")
    private Integer bornMonth;

    @JsonProperty("bornDay")
    private Integer bornDay;

    @JsonProperty("isBornBc")
    private Boolean isBornBc;

    @JsonProperty("deathYear")
    private Integer deathYear;

    @JsonProperty("deathMonth")
    private Integer deathMonth;

    @JsonProperty("deathDay")
    private Integer deathDay;

    @JsonProperty("isDeathBc")
    private Boolean isDeathBc;

    @JsonProperty("isPublished")
    private Boolean isPublished;
}
