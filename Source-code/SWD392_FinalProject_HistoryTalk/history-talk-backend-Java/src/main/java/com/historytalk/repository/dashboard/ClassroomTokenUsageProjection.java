package com.historytalk.repository.dashboard;

import java.util.UUID;

public interface ClassroomTokenUsageProjection {
    UUID getClassroomId();
    String getClassName();
    String getClassCode();
    Integer getGradeLevel();
    Integer getAcademicYear();
    String getStatus();
    String getTeacherName();
    UUID getTeacherUid();
    Long getStudentCount();
    Long getTokensUsedTotal();
    Long getTokensUsedToday();
}
