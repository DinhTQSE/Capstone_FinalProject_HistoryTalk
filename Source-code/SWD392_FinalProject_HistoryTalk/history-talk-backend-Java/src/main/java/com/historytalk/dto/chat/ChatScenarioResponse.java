package com.historytalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.EventEra;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatScenarioResponse {

    @JsonProperty("scenarioId")
    private UUID scenarioId;

    @JsonProperty("characterId")
    private UUID characterId;

    @JsonProperty("characterName")
    private String characterName;

    @JsonProperty("characterTitle")
    private String characterTitle;

    @JsonProperty("characterAvatar")
    private String characterAvatar;

    @JsonProperty("characterPersonality")
    private String characterPersonality;

    @JsonProperty("contextId")
    private UUID contextId;

    @JsonProperty("contextName")
    private String contextName;

    @JsonProperty("contextEra")
    private EventEra contextEra;

    @JsonProperty("contextYear")
    private Integer contextYear;

    @JsonProperty("contextImage")
    private String contextImage;

    @JsonProperty("contextSummary")
    private String contextSummary;

    @JsonProperty("scope")
    private String scope;
}
