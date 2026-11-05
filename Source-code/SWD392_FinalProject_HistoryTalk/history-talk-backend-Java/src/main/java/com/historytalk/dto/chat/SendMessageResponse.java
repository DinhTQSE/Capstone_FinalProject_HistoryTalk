package com.historytalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** Only present for exam sessions. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("exam")
    private ExamProgressResponse exam;
}
