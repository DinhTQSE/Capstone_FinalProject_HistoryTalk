package com.historyTalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SendMessageResponse {

    @JsonProperty("userMessage")
    private MessageResponse userMessage;

    @JsonProperty("assistantMessage")
    private MessageResponse assistantMessage;

    @JsonProperty("suggestedQuestions")
    private List<String> suggestedQuestions;
}
