package com.historytalk.dto.dashboard;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestNotificationRequest {
    @NotBlank(message = "type không được để trống")
    private String type; // daily_reminder | payment_success | subscription_expired
}
