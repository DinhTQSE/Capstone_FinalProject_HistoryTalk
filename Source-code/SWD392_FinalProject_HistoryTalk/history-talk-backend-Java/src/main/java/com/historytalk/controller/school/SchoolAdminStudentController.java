package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.student.StudentImportProcessor;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

        User schoolAdmin = resolveSchoolAdmin(principal);

        StudentImportContext context = StudentImportContext.builder()
                .schoolId(schoolAdmin.getSchool().getId())
                .schoolCode(schoolAdmin.getSchool().getSchoolCode())
                .operatorId(schoolAdmin.getUid())
                .operatorRole(schoolAdmin.getRole())
                .defaultInitialToken(effectiveDefaultToken)
                .build();

        StudentImportResultDto result = studentImportProcessor.processImport(file, context);

        String message = String.format("Import completed: %d succeeded, %d failed. Allocated %,d tokens.",
                result.getSuccessCount(), result.getFailureCount(), result.getTotalTokensAllocated());

        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    private User resolveSchoolAdmin(UserPrincipal principal) {
        if (principal != null && principal.getSchoolId() != null) {
            return userRepository.findById(UUID.fromString(principal.getUid()))
                    .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
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
        return user;
    }
}
