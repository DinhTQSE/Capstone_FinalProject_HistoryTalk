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

    @NotNull(message = "Completed academic year must not be null")
    private Integer completedAcademicYear;

    @Builder.Default
    private Boolean deactivateGraduatedGrade12 = true;
}
