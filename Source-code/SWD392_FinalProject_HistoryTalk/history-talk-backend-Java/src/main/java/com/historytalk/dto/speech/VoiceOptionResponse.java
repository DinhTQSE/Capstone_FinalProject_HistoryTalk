package com.historytalk.dto.speech;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoiceOptionResponse {

    /** Value to send as voiceId when creating or updating a character. */
    @JsonProperty("id")
    private String id;

    /** MALE or FEMALE. */
    @JsonProperty("gender")
    private String gender;

    /** NORTH, CENTRAL or SOUTH. */
    @JsonProperty("region")
    private String region;

    @JsonProperty("style")
    private String style;

    @JsonProperty("recommended")
    private boolean recommended;

    @JsonProperty("isDefault")
    private boolean isDefault;
}
