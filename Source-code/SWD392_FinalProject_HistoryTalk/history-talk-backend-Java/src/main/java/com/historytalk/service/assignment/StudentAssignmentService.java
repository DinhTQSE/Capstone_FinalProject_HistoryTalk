package com.historytalk.service.assignment;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.assignment.StudentAssignmentResponse;
import com.historytalk.dto.assignment.StudentSubmissionResponse;
import com.historytalk.dto.assignment.SubmitAssignmentRequest;
import com.historytalk.entity.enums.SubmissionStatus;
import com.historytalk.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StudentAssignmentService {

    PaginatedResponse<StudentAssignmentResponse> getMyAssignments(
            UUID classroomId, SubmissionStatus status, Pageable pageable, UserPrincipal principal);

    StudentAssignmentResponse getAssignmentDetail(UUID assignmentId, UserPrincipal principal);

    StudentSubmissionResponse startAssignment(UUID assignmentId, UserPrincipal principal);

    StudentSubmissionResponse submitAssignment(UUID assignmentId, SubmitAssignmentRequest request, UserPrincipal principal);
}
