package com.historytalk.dto.school;

import com.historytalk.entity.enums.TokenBoostRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenBoostRequestResponse {

    private UUID id;
    private UUID schoolId;
    private UUID classroomId;
    private String className;
    private UUID studentUid;
    private String studentName;
    private String studentCode;
    private Integer requestedTokens;
    private Integer approvedTokens;
    private String reason;
    private TokenBoostRequestStatus status;
    private UUID reviewedByUid;
    private String reviewedByName;
    private String reviewNote;
    private LocalDate requestDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
