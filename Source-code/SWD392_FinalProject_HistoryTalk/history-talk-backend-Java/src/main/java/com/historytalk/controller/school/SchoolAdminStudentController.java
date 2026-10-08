package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.school.AllocateStudentTokenRequest;
import com.historytalk.dto.school.AllocateStudentTokenResponse;
import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.student.StudentImportProcessor;
import com.historytalk.service.school.token.StudentTokenAllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
@Tag(name = "SaaS Student Management", description = "Endpoints for School Admin to manage students and allocate tokens (US-SP5-04, US-SP5-05)")
public class SchoolAdminStudentController {

    @Qualifier("SCHOOL_WIDE_IMPORT")
    private final StudentImportProcessor studentImportProcessor;

    private final StudentTokenAllocationService studentTokenAllocationService;

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

    @PutMapping("/token-allocation")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "School Admin allocates token quota to students (US-SP5-05)")
    public ResponseEntity<ApiResponse<AllocateStudentTokenResponse>> allocateTokens(
            @Valid @RequestBody AllocateStudentTokenRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        AllocateStudentTokenResponse response = studentTokenAllocationService.allocateTokens(
                principal.getSchoolId(), request);

        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }

    @GetMapping(value = "/template", produces = "text/csv; charset=UTF-8")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Download CSV template for bulk student import")
    public ResponseEntity<byte[]> downloadStudentImportTemplate() {
        String csvContent = "student_code,full_name,email,dob,gender,phone_number,class_code,password,additional_token\n"
                + "HS1001,Nguyễn Văn An,,2009-03-15,MALE,0912345671,HIS10A1-2026,,0\n"
                + "HS1002,Trần Thị Mai,,2009-07-22,FEMALE,0912345672,HIS10A1-2026,,0\n"
                + "HS1003,Lê Hoàng Nam,nam.lh@gmail.com,2009-11-05,MALE,0912345673,HIS10A2-2026,StudentPass@2026,5000\n";

        byte[] bytes = ("\uFEFF" + csvContent).getBytes(StandardCharsets.UTF_8); // UTF-8 BOM for Excel

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student_import_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}
