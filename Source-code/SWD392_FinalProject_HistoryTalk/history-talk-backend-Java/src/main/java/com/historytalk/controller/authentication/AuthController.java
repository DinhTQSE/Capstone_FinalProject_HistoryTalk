package com.historytalk.controller.authentication;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.authentication.ForgotPasswordRequest;
import com.historytalk.dto.authentication.LoginRequest;
import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.dto.authentication.LogoutResponse;
import com.historytalk.dto.authentication.RefreshTokenRequest;
import com.historytalk.dto.authentication.RefreshTokenResponse;
import com.historytalk.dto.authentication.RegisterRequest;
import com.historytalk.dto.authentication.RegisterResponse;
import com.historytalk.dto.authentication.RegisterStaffRequest;
import com.historytalk.dto.authentication.RegisterStaffResponse;
import com.historytalk.dto.authentication.ResetPasswordRequest;
import com.historytalk.service.authentication.AuthService;
import com.historytalk.service.authentication.GoogleOAuthService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration, login, logout and token refresh")
public class AuthController {

    private final AuthService authService;
    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
               description = "Creates a new REGISTERED user account. Staff accounts are managed by admins.")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        log.info("POST /api/v1/auth/register - email: {}", request.getEmail());
        RegisterResponse data = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "User registered successfully"));
    }

    @PostMapping("/register-content-admin")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Register content admin/system admin account",
               description = "Creates a new CONTENT_ADMIN or SYSTEM_ADMIN account. Requires SYSTEM_ADMIN role.")
    public ResponseEntity<ApiResponse<RegisterStaffResponse>> registerContentAdmin(
            @Valid @RequestBody RegisterStaffRequest request) {

        log.info("POST /api/v1/auth/register-content-admin - email: {}", request.getEmail());
        RegisterStaffResponse data = authService.registerStaff(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Staff account registered successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with email and password, returns JWT tokens.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("POST /api/v1/auth/login - email: {}", request.getEmail());
        LoginResponse data = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Login successful"));
    }

    @PostMapping("/google")
    @Operation(summary = "Authenticate with Google ID Token", description = "Verifies the Google ID Token and returns JWT tokens.")
    public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(
            @RequestBody Map<String, String> requestBody) {
        String idToken = requestBody.get("idToken");
        log.info("POST /api/v1/auth/google");
        LoginResponse data = googleOAuthService.authenticateGoogleIdToken(idToken);
        return ResponseEntity.ok(ApiResponse.success(data, "Google login successful"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Request a password reset link for an account email.")
    public ResponseEntity<ApiResponse<?>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        log.info("POST /api/v1/auth/forgot-password");
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null, "If the email exists, a password reset link has been sent"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Reset an account password with a valid reset token.")
    public ResponseEntity<ApiResponse<?>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        log.info("POST /api/v1/auth/reset-password");
        authService.resetPassword(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successfully"));
    }

    @PostMapping("/google/login-url")
    @Operation(summary = "Get Google login URL", description = "Returns backend Google OAuth2 authorization URL.")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleLoginUrl() {
        Map<String, String> data = Map.of("url", "/Historical-tell/oauth2/authorization/google");
        return ResponseEntity.ok(ApiResponse.success(data, "Google login URL generated successfully"));
    }

    @GetMapping("/google/backend-test/success")
    @Operation(summary = "Backend-only Google OAuth success callback", description = "Local test endpoint for OAuth redirects before frontend integration.")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleBackendTestSuccess(
            @RequestParam Map<String, String> queryParams) {
        return ResponseEntity.ok(ApiResponse.success(queryParams, "Google OAuth backend test successful"));
    }

    @GetMapping("/google/backend-test/failure")
    @Operation(summary = "Backend-only Google OAuth failure callback", description = "Local test endpoint for OAuth failure redirects before frontend integration.")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleBackendTestFailure(
            @RequestParam Map<String, String> queryParams) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Google OAuth backend test failed", queryParams.getOrDefault("error", "GOOGLE_OAUTH_FAILED")));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token", description = "Exchange a valid refresh token for a new access token.")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        log.info("POST /api/v1/auth/refresh-token");
        RefreshTokenResponse data = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate the current bearer token.")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(HttpServletRequest request) {
        log.info("POST /api/v1/auth/logout");
        authService.logout(request.getHeader("Authorization"));
        LogoutResponse data = LogoutResponse.builder().message("Logout successful").build();
        return ResponseEntity.ok(ApiResponse.success(data, "Logout successful"));
    }

    @PatchMapping("/users/{userId}/deactivate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Deactivate user", description = "Soft delete a user account (System Admin only)")
    public ResponseEntity<ApiResponse<?>> deactivateUser(
            @PathVariable String userId) {
        log.info("PATCH /api/v1/auth/users/{}/deactivate", userId);
        String staffId = SecurityUtils.getUserId();
        authService.softDeleteUser(userId, staffId);
        return ResponseEntity.ok(ApiResponse.success(null, "User account deactivated successfully"));
    }

    @PatchMapping("/me/deactivate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Deactivate my account", description = "Deactivate the currently logged in user account")
    public ResponseEntity<ApiResponse<?>> deactivateMe() {
        String userId = SecurityUtils.getUserId();
        log.info("PATCH /api/v1/auth/me/deactivate - user {}", userId);
        authService.softDeleteUser(userId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Account deactivated successfully"));
    }
}
