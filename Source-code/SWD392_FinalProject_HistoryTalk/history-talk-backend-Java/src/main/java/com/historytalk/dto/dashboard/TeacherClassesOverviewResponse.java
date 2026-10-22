package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherClassesOverviewResponse {
    private UUID teacherUid;
    private String teacherName;
    private long totalClassroomsManaged;
    private long totalStudentsManaged;
    private long totalTokensUsedByAllClasses;
    @Builder.Default
    private List<ClassroomTokenUsageSummaryDto> classrooms = new ArrayList<>();
}
