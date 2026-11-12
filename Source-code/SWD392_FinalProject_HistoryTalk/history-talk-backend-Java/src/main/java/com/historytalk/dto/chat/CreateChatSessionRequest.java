package com.historytalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.ChatSessionMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateChatSessionRequest {

    @JsonProperty("contextId")
    private String contextId;

    @NotBlank(message = "Yêu cầu characterId")
    @JsonProperty("characterId")
    private String characterId;

    /** Defaults to CHAT when omitted. */
    @JsonProperty("mode")
    private ChatSessionMode mode;

    /** Only used in EXAM mode, defaults to 5 when omitted. */
    @Min(value = 3, message = "Số câu vấn đáp tối thiểu là 3")
    @Max(value = 10, message = "Số câu vấn đáp tối đa là 10")
    @JsonProperty("examQuestionCount")
    private Integer examQuestionCount;

    /** EXAM only: a chat of the same character whose conversation the questions are asked about. */
    @JsonProperty("sourceSessionId")
    private String sourceSessionId;
}
