package com.historytalk.dto.school;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSchoolTokenPolicyRequest {

    @Min(value = 100, message = "Default daily student quota must be at least 100 tokens")
    private Integer defaultDailyStudentQuota;

    private UUID classroomId;

    private Integer gradeLevel;

    @Min(value = 100, message = "Classroom daily quota must be at least 100 tokens")
    private Integer classroomDailyQuota;
}
