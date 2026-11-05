package com.historytalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MessageResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private String content;

    @JsonProperty("messageType")
    private String messageType;

    @JsonProperty("quotes")
    private java.util.List<String> quotes;

    /** Grade of the learner's previous answer. Only present on exam feedback messages. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("examVerdict")
    private String examVerdict;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}
