package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassroomTokenUsageSummaryDto {
    private UUID classroomId;
    private String className;
    private String classCode;
    private Integer gradeLevel;
    private Integer academicYear;
    private String status;
    private String teacherName;
    private UUID teacherUid;
    private long studentCount;
    private long tokensUsedTotal;
    private long tokensUsedToday;
    private double percentageOfSchoolUsage;
}
