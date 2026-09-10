package com.historytalk.dto.gamification;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClaimQuestRequest {
    @NotBlank(message = "questId không được để trống")
    private String questId;
}
