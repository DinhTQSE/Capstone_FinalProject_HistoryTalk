package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateSchoolAdminRequest;
import com.historytalk.dto.school.CreateSchoolRequest;
import com.historytalk.dto.school.ResetPasswordResponse;
import com.historytalk.dto.school.SchoolAdminResponse;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.dto.school.UpdateSchoolAdminRequest;
import com.historytalk.dto.school.UpdateSchoolAdminStatusRequest;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.service.school.SchoolManagementService;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "SaaS School Administration", description = "Endpoints for System Admin to manage schools and school admin accounts (US-SP5-01, US-SP5-02)")
public class AdminSchoolController {

    private final SchoolManagementService schoolManagementService;

    @PostMapping("/schools")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Create school and assign enterprise token package (US-SP5-01)")
    public ResponseEntity<ApiResponse<SchoolResponse>> createSchool(@Valid @RequestBody CreateSchoolRequest request) {
        SchoolResponse response = schoolManagementService.createSchool(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Khởi tạo trường học và hạn mức token thành công"));
    }

    @GetMapping("/schools")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get list of schools with search and pagination (US-SP5-01)")
    public ResponseEntity<com.historytalk.dto.PaginatedApiResponse<SchoolResponse>> getSchools(
            @io.swagger.v3.oas.annotations.Parameter(description = "Keyword to search by school name, school code, or contact email")
            @RequestParam(required = false, defaultValue = "") String search,
            @io.swagger.v3.oas.annotations.Parameter(description = "Page number (1-indexed, default: 1)")
            @RequestParam(defaultValue = "1") int page,
            @io.swagger.v3.oas.annotations.Parameter(description = "Number of items per page (default: 10)")
            @RequestParam(defaultValue = "10") int size,
            @io.swagger.v3.oas.annotations.Parameter(description = "Field to sort by: createdAt, name, schoolCode, totalSchoolTokenQuota (default: createdAt)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @io.swagger.v3.oas.annotations.Parameter(description = "Sort direction: asc or desc (default: desc)")
            @RequestParam(defaultValue = "desc") String sortDirection) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String safeSortBy = switch (sortBy) {
            case "name", "schoolCode", "createdAt", "totalSchoolTokenQuota" -> sortBy;
            default -> "createdAt";
        };
        Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), Math.max(1, size), Sort.by(direction, safeSortBy));

        PaginatedResponse<SchoolResponse> response = schoolManagementService.getSchools(search, pageable);
        return ResponseEntity.ok(com.historytalk.dto.PaginatedApiResponse.of(response, "Lấy danh sách trường học thành công"));
    }

    @GetMapping("/schools/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get school details by ID")
    public ResponseEntity<ApiResponse<SchoolResponse>> getSchoolById(@PathVariable UUID id) {
        SchoolResponse response = schoolManagementService.getSchoolById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết trường học thành công"));
    }

    @PostMapping("/school-admins")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Create School Admin account and setup RBAC (US-SP5-02)")
    public ResponseEntity<ApiResponse<SchoolAdminResponse>> createSchoolAdmin(@Valid @RequestBody CreateSchoolAdminRequest request) {
        SchoolAdminResponse response = schoolManagementService.createSchoolAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Tạo tài khoản quản trị trường thành công"));
    }

    @GetMapping("/schools/{schoolId}/school-admins")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get list of School Admins of a school with pagination, search, and status filter (US-SP5-02)")
    public ResponseEntity<PaginatedApiResponse<SchoolAdminResponse>> getSchoolAdmins(
            @PathVariable UUID schoolId,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String safeSortBy = switch (sortBy) {
            case "fullName", "userName", "email", "createdAt" -> sortBy;
            default -> "createdAt";
        };
        Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), Math.max(1, size), Sort.by(direction, safeSortBy));

        PaginatedResponse<SchoolAdminResponse> response = schoolManagementService.getSchoolAdmins(schoolId, search, active, pageable);
        return ResponseEntity.ok(PaginatedApiResponse.of(response, "Lấy danh sách quản trị trường thành công"));
    }

    @PutMapping("/school-admins/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Update School Admin profile (US-SP5-02)")
    public ResponseEntity<ApiResponse<SchoolAdminResponse>> updateSchoolAdmin(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSchoolAdminRequest request) {
        SchoolAdminResponse response = schoolManagementService.updateSchoolAdmin(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật quản trị trường thành công"));
    }

    @PutMapping("/school-admins/{id}/status")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Update School Admin active status (US-SP5-02)")
    public ResponseEntity<ApiResponse<SchoolAdminResponse>> updateSchoolAdminStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSchoolAdminStatusRequest request) {
        SchoolAdminResponse response = schoolManagementService.updateSchoolAdminStatus(id, request.getActive());
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật trạng thái quản trị trường thành công"));
    }

    @DeleteMapping("/school-admins/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Soft delete School Admin account (US-SP5-02)")
    public ResponseEntity<ApiResponse<Void>> deleteSchoolAdmin(@PathVariable UUID id) {
        schoolManagementService.deleteSchoolAdmin(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa tài khoản quản trị trường thành công"));
    }

    @PostMapping("/school-admins/{id}/reset-password")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Reset School Admin password (US-SP5-02)")
    public ResponseEntity<ApiResponse<ResetPasswordResponse>> resetSchoolAdminPassword(@PathVariable UUID id) {
        ResetPasswordResponse response = schoolManagementService.resetSchoolAdminPassword(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Đặt lại mật khẩu quản trị trường thành công"));
    }
}
