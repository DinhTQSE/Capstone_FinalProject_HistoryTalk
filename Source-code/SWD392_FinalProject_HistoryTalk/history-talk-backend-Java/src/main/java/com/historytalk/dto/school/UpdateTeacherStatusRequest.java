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

    @NotNull(message = "Active status must not be null")
    private Boolean active;
}
