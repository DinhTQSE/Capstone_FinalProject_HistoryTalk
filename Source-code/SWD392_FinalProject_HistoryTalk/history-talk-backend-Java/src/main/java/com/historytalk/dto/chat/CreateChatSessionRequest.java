package com.historytalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateChatSessionRequest {

    @JsonProperty("contextId")
    private String contextId;

    @NotBlank(message = "Yêu cầu characterId")
    @JsonProperty("characterId")
    private String characterId;
}
