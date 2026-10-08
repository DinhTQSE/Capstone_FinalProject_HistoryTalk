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
@Tag(name = "SaaS Teacher Management", description = "APIs dành cho School Admin quản lý Giáo viên trong trường (US-SP5-03)")
public class SchoolAdminTeacherController {

    private final TeacherManagementService teacherManagementService;

    @PostMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Tạo tài khoản Giáo viên mới trong trường")
    public ResponseEntity<ApiResponse<TeacherResponse>> createTeacher(
            @Valid @RequestBody CreateTeacherRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        TeacherResponse response = teacherManagementService.createTeacher(request, principal.getSchoolId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Tạo tài khoản Giáo viên thành công"));
    }

    @GetMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Xem danh sách Giáo viên trong trường có tìm kiếm và phân trang")
    public ResponseEntity<ApiResponse<PaginatedResponse<TeacherResponse>>> getTeachers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PaginatedResponse<TeacherResponse> response = teacherManagementService.getTeachers(
                principal.getSchoolId(), search, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách Giáo viên thành công"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Cập nhật trạng thái hoạt động (ACTIVE / INACTIVE) của Giáo viên")
    public ResponseEntity<ApiResponse<TeacherResponse>> updateTeacherStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTeacherStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        TeacherResponse response = teacherManagementService.updateTeacherStatus(
                id, principal.getSchoolId(), request.getActive());
        String msg = Boolean.TRUE.equals(request.getActive()) ? "Kích hoạt tài khoản Giáo viên thành công" : "Khóa tài khoản Giáo viên thành công";
        return ResponseEntity.ok(ApiResponse.success(response, msg));
    }
}
