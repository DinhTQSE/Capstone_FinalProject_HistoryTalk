package com.historyTalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "sessionId is required")
    @JsonProperty("sessionId")
    private String sessionId;

    @NotBlank(message = "content is required")
    @Size(max = 4000, message = "content must not exceed 4000 characters")
    @JsonProperty("content")
    private String content;
}
