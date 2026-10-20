package com.historytalk.dto.school;

import com.historytalk.entity.enums.TokenBoostRequestStatus;
import com.historytalk.entity.enums.UserRole;
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
    private UUID requesterUid;
    private String requesterName;
    private UserRole requesterRole;
    private Integer requestedTokens;
    private Integer approvedTokens;
    private String reason;
    private TokenBoostRequestStatus status;
    private UUID teacherReviewedByUid;
    private String teacherReviewedByName;
    private String teacherReviewNote;
    private LocalDateTime teacherReviewedAt;
    private UUID adminReviewedByUid;
    private String adminReviewedByName;
    private String adminReviewNote;
    private LocalDateTime adminReviewedAt;
    private UUID reviewedByUid;
    private String reviewedByName;
    private String reviewNote;
    private LocalDate requestDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
