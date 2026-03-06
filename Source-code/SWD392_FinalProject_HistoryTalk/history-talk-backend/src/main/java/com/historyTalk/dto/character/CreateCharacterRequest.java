package com.historyTalk.dto.character;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCharacterRequest {

    @JsonProperty("name")
    @NotBlank(message = "Character name is required")
    @Size(min = 2, max = 100, message = "Character name must be between 2 and 100 characters")
    private String name;

    @JsonProperty("background")
    @NotBlank(message = "Background is required")
    private String background;

    @JsonProperty("image")
    @Size(max = 255, message = "Image URL must not exceed 255 characters")
    private String image;

    @JsonProperty("personality")
    @Size(max = 500, message = "Personality must not exceed 500 characters")
    private String personality;

    @JsonProperty("contextId")
    @NotNull(message = "Context ID is required")
    private String contextId;
}
