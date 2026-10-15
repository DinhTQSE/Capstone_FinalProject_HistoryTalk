package com.historytalk.dto.classroom;

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
public class BatchEnrollResultResponse {

    private UUID classroomId;
    private String className;
    private int totalRequested;
    private int successCount;
    private int alreadyEnrolledCount;
    private long currentStudentCount;
    private List<String> notes;
}
