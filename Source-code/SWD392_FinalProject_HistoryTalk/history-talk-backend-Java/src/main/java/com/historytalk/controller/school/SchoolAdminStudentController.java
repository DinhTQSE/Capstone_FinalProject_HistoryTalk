package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.AllocateStudentTokenRequest;
import com.historytalk.dto.school.AllocateStudentTokenResponse;
import com.historytalk.dto.school.ResetPasswordResponse;
import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.dto.school.UpdateStudentRequest;
import com.historytalk.dto.school.UpdateStudentStatusRequest;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.StudentManagementService;
import com.historytalk.service.school.student.StudentImportProcessor;
import com.historytalk.service.school.token.StudentTokenAllocationService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/students")
@RequiredArgsConstructor
@Tag(name = "SaaS Student Management", description = "Endpoints for School Admin to manage and bulk-import students (US-SP5-04)")
public class SchoolAdminStudentController {

    @Qualifier("SCHOOL_WIDE_IMPORT")
    private final StudentImportProcessor studentImportProcessor;
    private final StudentManagementService studentManagementService;
    private final StudentTokenAllocationService studentTokenAllocationService;
    private final UserRepository userRepository;

    @Value("${historytalk.saas.student.default-initial-token:10000}")
    private Integer configuredDefaultToken;

    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Bulk import student accounts via CSV file (US-SP5-04)")
    public ResponseEntity<ApiResponse<StudentImportResultDto>> importStudents(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Default initial token for each student (default: 10000)")
            @RequestParam(name = "defaultInitialToken", required = false) Integer defaultInitialToken,
            @AuthenticationPrincipal UserPrincipal principal) {

        int effectiveDefaultToken = (defaultInitialToken != null && defaultInitialToken >= 0)
                ? defaultInitialToken
                : configuredDefaultToken;

        UUID schoolId;
        String schoolCode;
        UUID operatorId;
        com.historytalk.entity.enums.UserRole operatorRole;

        if (principal != null && principal.getSchoolId() != null) {
            schoolId = principal.getSchoolId();
            schoolCode = principal.getSchoolCode();
            operatorId = UUID.fromString(principal.getUid());
            operatorRole = principal.getRole();
        } else {
            User schoolAdmin = resolveSchoolAdmin(principal);
            schoolId = schoolAdmin.getSchool().getId();
            schoolCode = schoolAdmin.getSchool().getSchoolCode();
            operatorId = schoolAdmin.getUid();
            operatorRole = schoolAdmin.getRole();
        }

        StudentImportContext context = StudentImportContext.builder()
                .schoolId(schoolId)
                .schoolCode(schoolCode)
                .operatorId(operatorId)
                .operatorRole(operatorRole)
                .defaultInitialToken(effectiveDefaultToken)
                .build();

        StudentImportResultDto result = studentImportProcessor.processImport(file, context);

        String message = String.format("Import completed: %d succeeded, %d failed. Allocated %,d tokens.",
                result.getSuccessCount(), result.getFailureCount(), result.getTotalTokensAllocated());

        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    @GetMapping("/template")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Download CSV template for student import (US-SP5-04)")
    public ResponseEntity<byte[]> downloadTemplate() {
        String csvContent = "\uFEFFstudent_code,full_name,email,dob,gender,phone_number,password,additional_token\n"
                + "HS001,Nguyễn Văn An,an.nguyen@example.com,2008-01-15,MALE,0901234567,,0\n";
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student_import_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    @PutMapping("/token-allocation")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Allocate tokens to individual student or list of students (US-SP5-05)")
    public ResponseEntity<ApiResponse<AllocateStudentTokenResponse>> allocateTokens(
            @Valid @RequestBody AllocateStudentTokenRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        AllocateStudentTokenResponse response = studentTokenAllocationService.allocateTokens(schoolId, request);
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }

    @GetMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Get list of students with search, active filter, and pagination (US-SP5-04)")
    public ResponseEntity<PaginatedApiResponse<StudentResponse>> getStudents(
            @Parameter(description = "Keyword to search student by code, name, username, or email")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filter by active status (true: active, false: inactive)")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Page number (0-indexed, default: 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page (default: 10)")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Field to sort by: studentCode, fullName, userName, email, token, createdAt (default: createdAt)")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: asc or desc (default: desc)")
            @RequestParam(defaultValue = "desc") String sortDirection,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String safeSortBy = switch (sortBy) {
            case "studentCode", "fullName", "userName", "email", "token", "createdAt" -> sortBy;
            default -> "createdAt";
        };
        Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, safeSortBy));

        PaginatedResponse<StudentResponse> response = studentManagementService.getStudents(
                schoolId, search, active, pageable);
        return ResponseEntity.ok(PaginatedApiResponse.of(response, "Students retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Get student details by ID (US-SP5-04)")
    public ResponseEntity<ApiResponse<StudentResponse>> getStudentById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        StudentResponse response = studentManagementService.getStudentById(id, schoolId);
        return ResponseEntity.ok(ApiResponse.success(response, "Student details retrieved successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Update student profile (US-SP5-04)")
    public ResponseEntity<ApiResponse<StudentResponse>> updateStudent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStudentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        StudentResponse response = studentManagementService.updateStudent(id, schoolId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Student profile updated successfully"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Update student active status (ACTIVE / INACTIVE) (US-SP5-04)")
    public ResponseEntity<ApiResponse<StudentResponse>> updateStudentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStudentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        StudentResponse response = studentManagementService.updateStudentStatus(id, schoolId, request.getActive());
        String msg = Boolean.TRUE.equals(request.getActive())
                ? "Student account activated successfully"
                : "Student account deactivated successfully";
        return ResponseEntity.ok(ApiResponse.success(response, msg));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Soft delete student account (US-SP5-04)")
    public ResponseEntity<ApiResponse<Void>> deleteStudent(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        studentManagementService.deleteStudent(id, schoolId);
        return ResponseEntity.ok(ApiResponse.success(null, "Student account deleted successfully"));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Reset student password to default (US-SP5-04)")
    public ResponseEntity<ApiResponse<ResetPasswordResponse>> resetStudentPassword(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        ResetPasswordResponse response = studentManagementService.resetStudentPassword(id, schoolId);
        return ResponseEntity.ok(ApiResponse.success(response, "Student password reset successfully"));
    }

    private UUID resolveSchoolId(UserPrincipal principal) {
        if (principal != null && principal.getSchoolId() != null) {
            return principal.getSchoolId();
        }
        return resolveSchoolAdmin(principal).getSchool().getId();
    }

    private User resolveSchoolAdmin(UserPrincipal principal) {
        if (principal != null && principal.getSchoolId() != null) {
            if (userRepository == null) {
                return null;
            }
            return userRepository.findById(UUID.fromString(principal.getUid()))
                    .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
        }
        String userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập với tài khoản Quản trị viên Trường");
        }
        if (userRepository == null) {
            throw new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
        if (user.getSchool() == null) {
            throw new InvalidRequestException("Tài khoản chưa được liên kết với bất kỳ trường học nào");
        }
        return user;
    }
}
