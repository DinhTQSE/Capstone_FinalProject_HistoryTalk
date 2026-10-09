package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.school.SchoolTokenQuotaResponse;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.SchoolManagementService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin")
@RequiredArgsConstructor
@Tag(name = "SaaS Token Quota Management", description = "Endpoints for School Admin to check remaining and allocated token quota (US-SP5-05)")
public class SchoolAdminQuotaController {

    private final SchoolManagementService schoolManagementService;
    private final UserRepository userRepository;

    @GetMapping("/token-quota")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Get current token quota status of the school (US-SP5-05)")
    public ResponseEntity<ApiResponse<SchoolTokenQuotaResponse>> getTokenQuota(
            @AuthenticationPrincipal UserPrincipal principal) {
        
        UUID schoolId = resolveSchoolId(principal);
        SchoolTokenQuotaResponse response = schoolManagementService.getSchoolTokenQuota(schoolId);
        return ResponseEntity.ok(ApiResponse.success(response, "School token quota retrieved successfully"));
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
