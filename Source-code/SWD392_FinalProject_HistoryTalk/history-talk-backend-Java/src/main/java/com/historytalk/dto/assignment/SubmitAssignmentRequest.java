package com.historytalk.dto.assignment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitAssignmentRequest {

    @Schema(description = "ID phiên làm bài Quiz (nếu nộp bài QUIZ_TEST)", example = "c83b23e1-7e89-4912-a1b2-998877665544")
    private UUID quizSessionId;

    @Schema(description = "ID phiên chat AI với nhân vật (nếu nộp bài AI_CHAT_ROLEPLAY)", example = "d94c34e2-8f90-4823-b2c3-112233445566")
    private UUID chatSessionId;
}
