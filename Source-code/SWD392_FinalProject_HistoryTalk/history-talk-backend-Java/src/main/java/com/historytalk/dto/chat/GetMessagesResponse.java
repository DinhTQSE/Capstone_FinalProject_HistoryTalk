package com.historytalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** Only present for exam sessions. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("exam")
    private ExamProgressResponse exam;
}
