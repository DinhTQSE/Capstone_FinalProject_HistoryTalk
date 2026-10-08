package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateTeacherRequest;
import com.historytalk.dto.school.TeacherResponse;
import com.historytalk.dto.school.UpdateTeacherStatusRequest;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.TeacherManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/school-admin/teachers")
@RequiredArgsConstructor
@Tag(name = "SaaS Teacher Management", description = "Endpoints for School Admin to manage teachers within their school (US-SP5-03)")
public class SchoolAdminTeacherController {

    private final TeacherManagementService teacherManagementService;

    @PostMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Create teacher account for school")
    public ResponseEntity<ApiResponse<TeacherResponse>> createTeacher(
            @Valid @RequestBody CreateTeacherRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        TeacherResponse response = teacherManagementService.createTeacher(request, principal.getSchoolId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Teacher account created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Get list of teachers with search and pagination")
    public ResponseEntity<ApiResponse<PaginatedResponse<TeacherResponse>>> getTeachers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PaginatedResponse<TeacherResponse> response = teacherManagementService.getTeachers(
                principal.getSchoolId(), search, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Teachers retrieved successfully"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Update teacher active status (ACTIVE / INACTIVE)")
    public ResponseEntity<ApiResponse<TeacherResponse>> updateTeacherStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTeacherStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        TeacherResponse response = teacherManagementService.updateTeacherStatus(
                id, principal.getSchoolId(), request.getActive());
        String msg = Boolean.TRUE.equals(request.getActive()) 
                ? "Teacher account activated successfully" 
                : "Teacher account deactivated successfully";
        return ResponseEntity.ok(ApiResponse.success(response, msg));
    }
}
