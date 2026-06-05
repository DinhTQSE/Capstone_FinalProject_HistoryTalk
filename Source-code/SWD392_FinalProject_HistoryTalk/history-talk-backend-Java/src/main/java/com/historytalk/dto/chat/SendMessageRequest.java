package com.historytalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "Yêu cầu sessionId")
    @JsonProperty("sessionId")
    private String sessionId;

    @NotBlank(message = "Yêu cầu nội dung (content)")
    @Size(max = 4000, message = "nội dung (content) không được vượt quá 4000 ký tự")
    @JsonProperty("content")
    private String content;

    @JsonProperty("messageType")
    private String messageType;
}
