package com.historytalk.dto.chat;

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

    @JsonProperty("remainingTokens")
    private Integer remainingTokens;

    @JsonProperty("promptTokens")
    private Integer promptTokens;

    @JsonProperty("completionTokens")
    private Integer completionTokens;
}
