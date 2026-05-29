package com.historytalk.controller.user;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.user.ChangePasswordRequest;
import com.historytalk.dto.user.UpdateMyProfileRequest;
import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.service.user.UserService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Authenticated user profile endpoints")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        String userId = SecurityUtils.getUserId();
        log.info("GET /api/v1/users/me - user {}", userId);
        return ResponseEntity.ok(ApiResponse.success(
                userService.getMyProfile(userId),
                "User profile retrieved successfully"
        ));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateMyProfileRequest request) {
        String userId = SecurityUtils.getUserId();
        log.info("PATCH /api/v1/users/me - user {}", userId);
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateMyProfile(userId, request),
                "User profile updated successfully"
        ));
    }

    @PatchMapping("/me/password")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Change current user password")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        String userId = SecurityUtils.getUserId();
        log.info("PATCH /api/v1/users/me/password - user {}", userId);
        userService.changeMyPassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }
}
