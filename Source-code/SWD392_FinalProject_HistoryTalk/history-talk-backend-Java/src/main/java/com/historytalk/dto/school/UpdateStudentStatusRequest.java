package com.historytalk.dto.school;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudentStatusRequest {

    @NotNull(message = "Trạng thái hoạt động không được để trống")
    private Boolean active;
}
