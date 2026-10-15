package com.historytalk.dto.quiz;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body to attach an existing bank question to a quiz. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachQuestionRequest {

    @NotNull(message = "Yêu cầu Question ID")
    private String questionId;
}
