package com.historytalk.controller.assignment;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.assignment.AssignmentGradebookResponse;
import com.historytalk.dto.assignment.CreateAssignmentRequest;
import com.historytalk.dto.assignment.TeacherAssignmentResponse;
import com.historytalk.dto.assignment.UpdateAssignmentRequest;
import com.historytalk.entity.enums.AssignmentStatus;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.assignment.TeacherAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher/assignments")
@RequiredArgsConstructor
@Tag(name = "Teacher Assignment Management", description = "Endpoints for teachers to create, monitor, update and cancel assignments (US-SP6-04)")
public class TeacherAssignmentController {

    private final TeacherAssignmentService teacherAssignmentService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Tạo mới bài tập cho lớp học và khởi tạo bản ghi nộp bài cho học sinh đang ACTIVE")
    public ResponseEntity<ApiResponse<TeacherAssignmentResponse>> createAssignment(
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TeacherAssignmentResponse response = teacherAssignmentService.createAssignment(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Assignment created successfully"));
    }

    @GetMapping("/classroom/{classroomId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Lấy danh sách bài tập của một lớp học có phân trang")
    public ResponseEntity<PaginatedApiResponse<TeacherAssignmentResponse>> getAssignmentsByClassroom(
            @PathVariable UUID classroomId,
            @RequestParam(required = false) AssignmentStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        PaginatedResponse<TeacherAssignmentResponse> response = teacherAssignmentService.getAssignmentsByClassroom(
                classroomId, status, pageable, principal);
        return ResponseEntity.ok(PaginatedApiResponse.of(response, "Assignments retrieved successfully"));
    }

    @GetMapping("/{assignmentId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Xem chi tiết bài tập kèm bảng điểm (Gradebook) của toàn bộ học sinh trong lớp")
    public ResponseEntity<ApiResponse<AssignmentGradebookResponse>> getAssignmentGradebook(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        AssignmentGradebookResponse response = teacherAssignmentService.getAssignmentGradebook(assignmentId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Gradebook retrieved successfully"));
    }

    @PutMapping("/{assignmentId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Cập nhật bài tập (tiêu đề, dặn dò, hạn chót deadline). Tự động revert LATE_SUBMITTED -> SUBMITTED nếu gia hạn.")
    public ResponseEntity<ApiResponse<TeacherAssignmentResponse>> updateAssignment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody UpdateAssignmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TeacherAssignmentResponse response = teacherAssignmentService.updateAssignment(assignmentId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Assignment updated successfully"));
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Hủy bài tập (Soft delete sang CANCELLED)")
    public ResponseEntity<ApiResponse<Void>> cancelAssignment(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        teacherAssignmentService.cancelAssignment(assignmentId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Assignment cancelled successfully"));
    }
}
