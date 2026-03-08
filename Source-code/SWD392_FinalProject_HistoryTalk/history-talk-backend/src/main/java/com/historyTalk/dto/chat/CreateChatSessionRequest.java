package com.historyTalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateChatSessionRequest {

    @NotBlank(message = "contextId is required")
    @JsonProperty("contextId")
    private String contextId;

    @NotBlank(message = "characterId is required")
    @JsonProperty("characterId")
    private String characterId;
}
