package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateTeacherRequest;
import com.historytalk.dto.school.TeacherResponse;
import com.historytalk.dto.school.UpdateTeacherStatusRequest;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.TeacherManagementService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Create teacher account for school")
    public ResponseEntity<ApiResponse<TeacherResponse>> createTeacher(
            @Valid @RequestBody CreateTeacherRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        UUID schoolId = resolveSchoolId(principal);
        TeacherResponse response = teacherManagementService.createTeacher(request, schoolId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Teacher account created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Get list of teachers with search and pagination (US-SP5-03)")
    public ResponseEntity<PaginatedApiResponse<TeacherResponse>> getTeachers(
            @Parameter(description = "Keyword to search teacher by full name, username, or email")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-indexed, default: 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page (default: 10)")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Field to sort by: createdAt, fullName, userName, email (default: createdAt)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: asc or desc (default: desc)")
            @RequestParam(defaultValue = "desc") String sortDirection,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        UUID schoolId = resolveSchoolId(principal);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String safeSortBy = switch (sortBy) {
            case "fullName", "userName", "email", "createdAt" -> sortBy;
            default -> "createdAt";
        };
        Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, safeSortBy));

        PaginatedResponse<TeacherResponse> response = teacherManagementService.getTeachers(
                schoolId, search, pageable);
        return ResponseEntity.ok(PaginatedApiResponse.of(response, "Teachers retrieved successfully"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Update teacher active status (ACTIVE / INACTIVE)")
    public ResponseEntity<ApiResponse<TeacherResponse>> updateTeacherStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTeacherStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        UUID schoolId = resolveSchoolId(principal);
        TeacherResponse response = teacherManagementService.updateTeacherStatus(
                id, schoolId, request.getActive());
        String msg = Boolean.TRUE.equals(request.getActive()) 
                ? "Teacher account activated successfully" 
                : "Teacher account deactivated successfully";
        return ResponseEntity.ok(ApiResponse.success(response, msg));
    }

    private UUID resolveSchoolId(UserPrincipal principal) {
        if (principal != null && principal.getSchoolId() != null) {
            return principal.getSchoolId();
        }
        String userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập với tài khoản Quản trị viên Trường");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
        if (user.getSchool() == null) {
            throw new InvalidRequestException("Tài khoản chưa được liên kết với bất kỳ trường học nào");
        }
        return user.getSchool().getId();
    }
}
