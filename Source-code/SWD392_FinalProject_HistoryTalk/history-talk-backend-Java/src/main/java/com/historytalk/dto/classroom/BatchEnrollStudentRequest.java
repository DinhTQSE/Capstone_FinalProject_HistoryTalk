package com.historytalk.dto.classroom;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchEnrollStudentRequest {

    @NotEmpty(message = "Danh sách học sinh không được để trống")
    private List<UUID> studentIds;
}
