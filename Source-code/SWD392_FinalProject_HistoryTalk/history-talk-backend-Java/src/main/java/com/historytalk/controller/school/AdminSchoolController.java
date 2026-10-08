package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.school.CreateSchoolAdminRequest;
import com.historytalk.dto.school.CreateSchoolRequest;
import com.historytalk.dto.school.SchoolAdminResponse;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.service.school.SchoolManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
