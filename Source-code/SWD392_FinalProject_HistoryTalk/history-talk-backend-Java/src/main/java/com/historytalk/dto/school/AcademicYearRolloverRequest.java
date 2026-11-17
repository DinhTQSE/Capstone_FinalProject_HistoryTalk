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
public class AcademicYearRolloverRequest {

    @NotNull(message = "Năm học đã kết thúc không được để trống")
    private Integer completedAcademicYear;

    @Builder.Default
    private Boolean deactivateGraduatedGrade12 = true;
}
