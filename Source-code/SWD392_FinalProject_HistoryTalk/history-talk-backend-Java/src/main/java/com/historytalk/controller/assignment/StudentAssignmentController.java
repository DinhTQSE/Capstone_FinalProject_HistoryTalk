package com.historytalk.controller.assignment;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.assignment.StudentAssignmentResponse;
import com.historytalk.dto.assignment.StudentSubmissionResponse;
import com.historytalk.dto.assignment.SubmitAssignmentRequest;
import com.historytalk.entity.enums.SubmissionStatus;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.assignment.StudentAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/assignments")
@RequiredArgsConstructor
@Tag(name = "Student Assignment Execution", description = "Endpoints for students to view, start and submit multi-task assignments (US-SP6-05)")
public class StudentAssignmentController {

    private final StudentAssignmentService studentAssignmentService;

    @GetMapping
    @PreAuthorize("hasRole('SCHOOL_STUDENT')")
    @Operation(summary = "Lấy danh sách bài tập được giao cho học sinh có phân trang và lọc theo trạng thái/lớp")
    public ResponseEntity<PaginatedApiResponse<StudentAssignmentResponse>> getMyAssignments(
            @RequestParam(required = false) UUID classroomId,
            @RequestParam(required = false) SubmissionStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        PaginatedResponse<StudentAssignmentResponse> response = studentAssignmentService.getMyAssignments(
                classroomId, status, pageable, principal);
        return ResponseEntity.ok(PaginatedApiResponse.of(response, "Assignments retrieved successfully"));
    }

    @GetMapping("/{assignmentId}")
    @PreAuthorize("hasRole('SCHOOL_STUDENT')")
    @Operation(summary = "Xem chi tiết bài tập và trạng thái làm bài của học sinh")
    public ResponseEntity<ApiResponse<StudentAssignmentResponse>> getAssignmentDetail(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentAssignmentResponse response = studentAssignmentService.getAssignmentDetail(assignmentId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Assignment detail retrieved successfully"));
    }

    @PostMapping("/{assignmentId}/start")
    @PreAuthorize("hasRole('SCHOOL_STUDENT')")
    @Operation(summary = "Bắt đầu làm bài tập (chuyển NOT_STARTED sang IN_PROGRESS và ghi nhận startedAt)")
    public ResponseEntity<ApiResponse<StudentSubmissionResponse>> startAssignment(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentSubmissionResponse response = studentAssignmentService.startAssignment(assignmentId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Assignment started successfully"));
    }

    @PostMapping("/{assignmentId}/submit")
    @PreAuthorize("hasRole('SCHOOL_STUDENT')")
    @Operation(summary = "Nộp bài tập và tự động chấm điểm trên Server (AI Chat count, QuizSession score, Reading duration)")
    public ResponseEntity<ApiResponse<StudentSubmissionResponse>> submitAssignment(
            @PathVariable UUID assignmentId,
            @RequestBody(required = false) SubmitAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentSubmissionResponse response = studentAssignmentService.submitAssignment(assignmentId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Assignment submitted successfully"));
    }
}
