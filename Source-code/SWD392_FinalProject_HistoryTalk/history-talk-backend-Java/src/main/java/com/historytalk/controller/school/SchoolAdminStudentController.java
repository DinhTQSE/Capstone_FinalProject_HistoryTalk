package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.student.StudentImportProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/students")
@RequiredArgsConstructor
@Tag(name = "SaaS Student Management", description = "Endpoints for School Admin to manage and bulk-import students (US-SP5-04)")
public class SchoolAdminStudentController {

    @Qualifier("SCHOOL_WIDE_IMPORT")
    private final StudentImportProcessor studentImportProcessor;

    @Value("${historytalk.saas.student.default-initial-token:10000}")
    private Integer configuredDefaultToken;

    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Bulk import student accounts via CSV file (US-SP5-04)")
    public ResponseEntity<ApiResponse<StudentImportResultDto>> importStudents(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "defaultInitialToken", required = false) Integer defaultInitialToken,
            @AuthenticationPrincipal UserPrincipal principal) {

        int effectiveDefaultToken = (defaultInitialToken != null && defaultInitialToken >= 0)
                ? defaultInitialToken
                : configuredDefaultToken;

        StudentImportContext context = StudentImportContext.builder()
                .schoolId(principal.getSchoolId())
                .schoolCode(principal.getSchoolCode())
                .operatorId(UUID.fromString(principal.getUid()))
                .operatorRole(UserRole.SCHOOL_ADMIN)
                .defaultInitialToken(effectiveDefaultToken)
                .build();

        StudentImportResultDto result = studentImportProcessor.processImport(file, context);

        String message = String.format("Import completed: %d succeeded, %d failed. Allocated %,d tokens.",
                result.getSuccessCount(), result.getFailureCount(), result.getTotalTokensAllocated());

        return ResponseEntity.ok(ApiResponse.success(result, message));
    }
}
