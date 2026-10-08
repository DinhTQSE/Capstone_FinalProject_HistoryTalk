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
public class UpdateTeacherStatusRequest {

    @NotNull(message = "Trạng thái active không được để trống")
    private Boolean active;
}
