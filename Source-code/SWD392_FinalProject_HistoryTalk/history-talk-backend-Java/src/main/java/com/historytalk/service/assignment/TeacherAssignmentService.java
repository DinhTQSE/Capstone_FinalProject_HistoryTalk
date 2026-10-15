package com.historytalk.service.assignment;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.assignment.AssignmentGradebookResponse;
import com.historytalk.dto.assignment.CreateAssignmentRequest;
import com.historytalk.dto.assignment.TeacherAssignmentResponse;
import com.historytalk.dto.assignment.UpdateAssignmentRequest;
import com.historytalk.entity.enums.AssignmentStatus;
import com.historytalk.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TeacherAssignmentService {

    TeacherAssignmentResponse createAssignment(CreateAssignmentRequest request, UserPrincipal principal);

    PaginatedResponse<TeacherAssignmentResponse> getAssignmentsByClassroom(
            UUID classroomId, AssignmentStatus status, Pageable pageable, UserPrincipal principal);

    AssignmentGradebookResponse getAssignmentGradebook(UUID assignmentId, UserPrincipal principal);

    TeacherAssignmentResponse updateAssignment(UUID assignmentId, UpdateAssignmentRequest request, UserPrincipal principal);

    void cancelAssignment(UUID assignmentId, UserPrincipal principal);
}
