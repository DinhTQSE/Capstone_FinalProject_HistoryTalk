package com.historytalk.controller.classroom;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.ClassroomResponse;
import com.historytalk.dto.classroom.CreateClassroomRequest;
import com.historytalk.dto.classroom.UpdateClassroomRequest;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.classroom.ClassroomService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "SaaS Classroom Management", description = "Endpoints for managing classrooms, codes, and role-based access (US-SP5-06, US-SP5-07)")
public class ClassroomController {

    private final ClassroomService classroomService;

    @PostMapping("/classrooms")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @Operation(summary = "Create a new classroom with smart semantic class code")
    public ResponseEntity<ApiResponse<ClassroomResponse>> createClassroom(
            @Valid @RequestBody CreateClassroomRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        ClassroomResponse response = classroomService.createClassroom(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Classroom created successfully"));
    }

    @PutMapping("/classrooms/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @Operation(summary = "Update classroom information or assign teacher")
    public ResponseEntity<ApiResponse<ClassroomResponse>> updateClassroom(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClassroomRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        ClassroomResponse response = classroomService.updateClassroom(id, request, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Classroom updated successfully"));
    }

    @GetMapping("/classrooms/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SCHOOL_STUDENT')")
    @Operation(summary = "Get detailed information of a classroom")
    public ResponseEntity<ApiResponse<ClassroomResponse>> getClassroomById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        ClassroomResponse response = classroomService.getClassroomById(id, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Classroom details retrieved successfully"));
    }

    @DeleteMapping("/classrooms/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Delete (soft-delete) a classroom")
    public ResponseEntity<ApiResponse<Void>> deleteClassroom(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        classroomService.deleteClassroom(id, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Classroom deleted successfully"));
    }

    @GetMapping("/school-admin/classrooms")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "School Admin: View all classrooms in the school with search & filters")
    public ResponseEntity<ApiResponse<PaginatedResponse<ClassroomResponse>>> getSchoolClassrooms(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer gradeLevel,
            @RequestParam(required = false) Integer academicYear,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<ClassroomResponse> response = classroomService.getSchoolClassrooms(
                search, gradeLevel, academicYear, pageable, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "School classrooms retrieved successfully"));
    }

    @GetMapping("/teacher/classrooms")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Teacher: View all classrooms managed by the current teacher")
    public ResponseEntity<ApiResponse<PaginatedResponse<ClassroomResponse>>> getTeacherClassrooms(
            @RequestParam(required = false) Integer academicYear,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<ClassroomResponse> response = classroomService.getTeacherClassrooms(
                academicYear, pageable, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Teacher classrooms retrieved successfully"));
    }

    @GetMapping("/student/classrooms")
    @PreAuthorize("hasRole('SCHOOL_STUDENT')")
    @Operation(summary = "Student: View all classrooms the student has joined")
    public ResponseEntity<ApiResponse<List<ClassroomResponse>>> getStudentClassrooms(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<ClassroomResponse> response = classroomService.getStudentClassrooms(principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Student classrooms retrieved successfully"));
    }
}
