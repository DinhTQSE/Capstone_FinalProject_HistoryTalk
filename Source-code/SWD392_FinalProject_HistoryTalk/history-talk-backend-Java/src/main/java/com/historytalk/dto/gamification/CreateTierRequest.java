package com.historytalk.dto.gamification;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTierRequest {

    @NotBlank(message = "Title không được để trống")
    private String title;

    @NotNull(message = "Amount không được để trống")
    @Min(value = 0, message = "Amount phải >= 0")
    private Double amount;

    @NotNull(message = "noMonth không được để trống")
    @Min(value = 1, message = "noMonth phải >= 1")
    private Integer noMonth;

    @NotNull(message = "limitedToken không được để trống")
    @Min(value = 1, message = "limitedToken phải >= 1")
    private Integer limitedToken;

    private Boolean isActive = true;
}
