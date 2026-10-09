package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateSchoolAdminRequest;
import com.historytalk.dto.school.CreateSchoolRequest;
import com.historytalk.dto.school.SchoolAdminResponse;
import com.historytalk.dto.school.SchoolResponse;
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
                .body(ApiResponse.success(response, "School and token quota initialized successfully"));
    }

    @GetMapping("/schools")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get list of schools with search and pagination (US-SP5-01)")
    public ResponseEntity<com.historytalk.dto.PaginatedApiResponse<SchoolResponse>> getSchools(
            @io.swagger.v3.oas.annotations.Parameter(description = "Keyword to search by school name, school code, or contact email")
            @RequestParam(required = false) String search,
            @io.swagger.v3.oas.annotations.Parameter(description = "Page number (0-indexed, default: 0)")
            @RequestParam(defaultValue = "0") int page,
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
        Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, safeSortBy));

        PaginatedResponse<SchoolResponse> response = schoolManagementService.getSchools(search, pageable);
        return ResponseEntity.ok(com.historytalk.dto.PaginatedApiResponse.of(response, "Schools retrieved successfully"));
    }

    @GetMapping("/schools/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get school details by ID")
    public ResponseEntity<ApiResponse<SchoolResponse>> getSchoolById(@PathVariable UUID id) {
        SchoolResponse response = schoolManagementService.getSchoolById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "School details retrieved successfully"));
    }

    @PostMapping("/school-admins")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Create School Admin account and setup RBAC (US-SP5-02)")
    public ResponseEntity<ApiResponse<SchoolAdminResponse>> createSchoolAdmin(@Valid @RequestBody CreateSchoolAdminRequest request) {
        SchoolAdminResponse response = schoolManagementService.createSchoolAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "School Admin account created successfully"));
    }
}
