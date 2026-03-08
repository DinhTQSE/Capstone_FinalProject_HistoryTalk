package com.historyTalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatSessionResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("characterId")
    private String characterId;

    @JsonProperty("contextId")
    private String contextId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("lastMessage")
    private String lastMessage;

    @JsonProperty("lastMessageAt")
    private LocalDateTime lastMessageAt;

    @JsonProperty("messageCount")
    private int messageCount;
}
