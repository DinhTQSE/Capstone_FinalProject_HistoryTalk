package com.historytalk.dto.speech;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoicePreviewRequest {

    @NotBlank(message = "Yêu cầu voiceId")
    @JsonProperty("voiceId")
    private String voiceId;

    /** Empty means a sample sentence. */
    @Size(max = 300, message = "Câu nghe thử không được vượt quá 300 ký tự")
    @JsonProperty("text")
    private String text;
}
