package com.historytalk.dto.classroom;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomResponse {

    private UUID id;
    private UUID schoolId;
    private String schoolName;
    private String schoolCode;

    private String className;
    private String classCode;
    private Integer gradeLevel;
    private Integer academicYear;
    private String description;
    private String status;

    private TeacherSummaryDto teacher;
    private Long studentCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
