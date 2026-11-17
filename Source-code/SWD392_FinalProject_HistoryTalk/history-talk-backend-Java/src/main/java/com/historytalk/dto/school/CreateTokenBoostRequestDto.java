package com.historytalk.dto.school;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTokenBoostRequestDto {

    @NotNull(message = "Vui lòng nhập số token cần xin")
    @Min(value = 100, message = "Số token xin tối thiểu là 100")
    @Max(value = 50000, message = "Mỗi yêu cầu xin tối đa 50.000 token")
    private Integer requestedTokens;

    @NotBlank(message = "Vui lòng nhập lý do")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;

    /**
     * Optional target student ID (used when a Teacher creates a ticket for a specific student after running out of daily teacher tokens).
     */
    private UUID studentId;

    /**
     * Optional classroom ID (used when a Teacher creates a ticket for a classroom or student).
     */
    private UUID classroomId;
}
