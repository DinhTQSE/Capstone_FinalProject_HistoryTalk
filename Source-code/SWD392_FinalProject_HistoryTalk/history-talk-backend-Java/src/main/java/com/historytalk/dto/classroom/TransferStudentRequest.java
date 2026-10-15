package com.historytalk.dto.classroom;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class TransferStudentRequest {

    @NotNull(message = "Lớp học đích không được để trống")
    private UUID targetClassroomId;

    @NotEmpty(message = "Danh sách học sinh chuyển lớp không được để trống")
    private List<UUID> studentIds;
}
