package com.historytalk.controller.user;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.user.AdminUpdateUserRequest;
import com.historytalk.dto.user.UpdateUserRoleRequest;
import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.dto.user.BulkRestoreUsersRequest;
import com.historytalk.dto.user.BulkRestoreUsersResponse;
import com.historytalk.service.authentication.AuthService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users (Admin)", description = "System admin user management endpoints")
public class AdminUserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "List users")
    public ResponseEntity<ApiResponse<PaginatedResponse<UserProfileResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /api/v1/admin/users - page: {}, size: {}", page, size);
        return ResponseEntity.ok(ApiResponse.success(
                userService.listUsers(page, size),
                "Users retrieved successfully"
        ));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(@PathVariable String userId) {
        log.info("GET /api/v1/admin/users/{}", userId);
        return ResponseEntity.ok(ApiResponse.success(
                userService.getUserById(userId),
                "User retrieved successfully"
        ));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Update user profile as admin")
    public ResponseEntity<ApiResponse<UserProfileResponse>> adminUpdateUser(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        log.info("PATCH /api/v1/admin/users/{}", userId);
        return ResponseEntity.ok(ApiResponse.success(
                userService.adminUpdateUser(userId, request),
                "User updated successfully"
        ));
    }

    @PatchMapping("/{userId}/role")
    @Operation(summary = "Update user role")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        log.info("PATCH /api/v1/admin/users/{}/role", userId);
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateUserRole(userId, request),
                "User role updated successfully"
        ));
    }

    @PatchMapping("/{userId}/deactivate")
    @Operation(summary = "Deactivate user")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable String userId) {
        String adminId = SecurityUtils.getUserId();
        log.info("PATCH /api/v1/admin/users/{}/deactivate - by {}", userId, adminId);
        authService.softDeleteUser(userId, adminId);
        return ResponseEntity.ok(ApiResponse.success(null, "User account deactivated successfully"));
    }

    @PatchMapping("/{userId}/restore")
    @Operation(summary = "Restore deactivated user")
    public ResponseEntity<ApiResponse<UserProfileResponse>> restoreUser(@PathVariable String userId) {
        log.info("PATCH /api/v1/admin/users/{}/restore", userId);
        return ResponseEntity.ok(ApiResponse.success(
                userService.restoreUser(userId),
                "User account restored successfully"
        ));
    }

    @PatchMapping("/restore/batch")
    @Operation(summary = "Restore multiple deactivated users in batch")
    public ResponseEntity<ApiResponse<BulkRestoreUsersResponse>> restoreUsersBatch(
            @Valid @RequestBody BulkRestoreUsersRequest request) {
        log.info("PATCH /api/v1/admin/users/restore/batch");
        return ResponseEntity.ok(ApiResponse.success(
                userService.restoreUsersBatch(request.getUserIds()),
                "Batch user restoration completed"
        ));
    }

    @PatchMapping("/restore/all")
    @Operation(summary = "Restore all deactivated users")
    public ResponseEntity<ApiResponse<Integer>> restoreAllUsers() {
        log.info("PATCH /api/v1/admin/users/restore/all");
        int count = userService.restoreAllUsers();
        return ResponseEntity.ok(ApiResponse.success(
                count,
                "All deactivated users restored successfully (" + count + " users restored)"
        ));
    }
}
