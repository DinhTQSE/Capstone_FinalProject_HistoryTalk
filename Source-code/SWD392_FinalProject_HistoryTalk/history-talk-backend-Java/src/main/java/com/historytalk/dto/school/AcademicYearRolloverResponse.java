package com.historytalk.dto.school;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicYearRolloverResponse {

    private UUID schoolId;
    private Integer completedAcademicYear;
    private Integer archivedClassroomsCount;
    private Long reclaimedTokensToPool;
    private Integer deactivatedGraduatedStudentsCount;
    private Integer remainingSchoolTokenPool;
}
