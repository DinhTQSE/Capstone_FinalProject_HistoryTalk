package com.historyTalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GetMessagesResponse {

    @JsonProperty("messages")
    private List<MessageResponse> messages;

    @JsonProperty("suggestedQuestions")
    private List<String> suggestedQuestions;
}
