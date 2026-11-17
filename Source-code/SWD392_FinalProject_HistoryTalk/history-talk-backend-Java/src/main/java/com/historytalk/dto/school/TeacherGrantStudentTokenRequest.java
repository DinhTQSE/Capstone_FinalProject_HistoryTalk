package com.historytalk.dto.school;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherGrantStudentTokenRequest {

    @NotNull(message = "Vui lòng nhập số token cần cấp")
    @Min(value = 50, message = "Số token cấp tối thiểu là 50")
    @Max(value = 50000, message = "Số token cấp tối đa là 50.000")
    private Integer tokens;

    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
