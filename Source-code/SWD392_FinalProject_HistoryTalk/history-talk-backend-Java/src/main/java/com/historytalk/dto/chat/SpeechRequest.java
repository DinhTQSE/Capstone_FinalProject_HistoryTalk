package com.historytalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SpeechRequest {

    @NotBlank(message = "Yêu cầu characterId")
    @JsonProperty("characterId")
    private String characterId;

    /** One sentence or a short paragraph; clients split long replies so playback can start early. */
    @NotBlank(message = "Yêu cầu nội dung (text)")
    @Size(max = 1000, message = "Nội dung đọc không được vượt quá 1000 ký tự")
    @JsonProperty("text")
    private String text;
}
