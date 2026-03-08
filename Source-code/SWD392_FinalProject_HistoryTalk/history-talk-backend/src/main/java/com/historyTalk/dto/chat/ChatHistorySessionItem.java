package com.historyTalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatHistorySessionItem {

    @JsonProperty("id")
    private String id;

    @JsonProperty("characterId")
    private String characterId;

    @JsonProperty("characterName")
    private String characterName;

    @JsonProperty("characterTitle")
    private String characterTitle;

    @JsonProperty("characterImage")
    private String characterImage;

    @JsonProperty("contextId")
    private String contextId;

    @JsonProperty("contextName")
    private String contextName;

    @JsonProperty("sessionTitle")
    private String sessionTitle;

    @JsonProperty("lastMessage")
    private String lastMessage;

    @JsonProperty("lastMessageAt")
    private LocalDateTime lastMessageAt;

    @JsonProperty("messageCount")
    private int messageCount;
}
