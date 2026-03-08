package com.historyTalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatHistoryGroupResponse {

    @JsonProperty("contextId")
    private String contextId;

    @JsonProperty("contextName")
    private String contextName;

    @JsonProperty("sessions")
    private List<ChatHistorySessionItem> sessions;
}
