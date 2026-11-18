package com.historytalk.controller.authentication;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.authentication.ForgotPasswordRequest;
import com.historytalk.dto.authentication.LoginRequest;
import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.dto.authentication.LogoutResponse;
import com.historytalk.dto.authentication.LogoutRequest;
import com.historytalk.utils.HttpRequestUtils;
import com.historytalk.dto.authentication.RefreshTokenRequest;
import com.historytalk.dto.authentication.RefreshTokenResponse;
import com.historytalk.dto.authentication.RegisterRequest;
import com.historytalk.dto.authentication.RegisterResponse;
import com.historytalk.dto.authentication.RegisterStaffRequest;
import com.historytalk.dto.authentication.RegisterStaffResponse;
import com.historytalk.dto.authentication.ResetPasswordRequest;
import com.historytalk.dto.authentication.ResendVerificationRequest;
import com.historytalk.dto.authentication.VerifyEmailRequest;
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
                .body(ApiResponse.success(data, "Đăng ký tài khoản thành công"));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email", description = "Verify a newly registered user's email address using the verification token.")
    public ResponseEntity<ApiResponse<?>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        log.info("POST /api/v1/auth/verify-email");
        authService.verifyEmail(request.getToken());
        return ResponseEntity.ok(ApiResponse.success(null, "Xác thực email thành công"));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification", description = "Resend verification link if the account exists and is not yet verified.")
    public ResponseEntity<ApiResponse<?>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {

        log.info("POST /api/v1/auth/resend-verification");
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null, "Nếu tài khoản tồn tại và chưa được xác thực, email xác thực đã được gửi"));
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
        return ResponseEntity.ok(ApiResponse.success(data, "Tạo tài khoản nhân sự thành công"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with email and password, returns JWT tokens.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        log.info("POST /api/v1/auth/login - email: {}", request.getEmail());
        String ip = HttpRequestUtils.getClientIpAddress(servletRequest);
        String device = HttpRequestUtils.getDeviceInfo(servletRequest);
        LoginResponse data = authService.login(request, ip, device);
        return ResponseEntity.ok(ApiResponse.success(data, "Đăng nhập thành công"));
    }

    @PostMapping("/google")
    @Operation(summary = "Authenticate with Google ID Token", description = "Verifies the Google ID Token and returns JWT tokens.")
    public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(
            @RequestBody Map<String, String> requestBody) {
        String idToken = requestBody.get("idToken");
        log.info("POST /api/v1/auth/google");
        LoginResponse data = googleOAuthService.authenticateGoogleIdToken(idToken);
        return ResponseEntity.ok(ApiResponse.success(data, "Đăng nhập bằng Google thành công"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Request a password reset link for an account email.")
    public ResponseEntity<ApiResponse<?>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        log.info("POST /api/v1/auth/forgot-password");
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null, "Nếu email tồn tại, liên kết đặt lại mật khẩu đã được gửi"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Reset an account password with a valid reset token.")
    public ResponseEntity<ApiResponse<?>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        log.info("POST /api/v1/auth/reset-password");
        authService.resetPassword(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Đặt lại mật khẩu thành công"));
    }

    @PostMapping("/reset-password/{token}")
    @Operation(summary = "Reset password with path token", description = "Reset an account password using the token in path parameter (Express compatibility).")
    public ResponseEntity<ApiResponse<?>> resetPasswordWithPathToken(
            @PathVariable String token,
            @RequestBody Map<String, String> requestBody) {

        log.info("POST /api/v1/auth/reset-password/{}", token);
        String password = requestBody.getOrDefault("password", requestBody.get("newPassword"));
        authService.resetPassword(token, password, password);
        return ResponseEntity.ok(ApiResponse.success(null, "Đặt lại mật khẩu thành công"));
    }

    @PostMapping("/google/login-url")
    @Operation(summary = "Get Google login URL", description = "Returns backend Google OAuth2 authorization URL.")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleLoginUrl() {
        Map<String, String> data = Map.of("url", "/Historical-tell/oauth2/authorization/google");
        return ResponseEntity.ok(ApiResponse.success(data, "Tạo đường dẫn đăng nhập Google thành công"));
    }

    @GetMapping("/google/backend-test/success")
    @Operation(summary = "Backend-only Google OAuth success callback", description = "Local test endpoint for OAuth redirects before frontend integration.")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleBackendTestSuccess(
            @RequestParam Map<String, String> queryParams) {
        return ResponseEntity.ok(ApiResponse.success(queryParams, "Kiểm tra Google OAuth phía backend thành công"));
    }

    @GetMapping("/google/backend-test/failure")
    @Operation(summary = "Backend-only Google OAuth failure callback", description = "Local test endpoint for OAuth failure redirects before frontend integration.")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleBackendTestFailure(
            @RequestParam Map<String, String> queryParams) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Kiểm tra Google OAuth phía backend thất bại", queryParams.getOrDefault("error", "GOOGLE_OAUTH_FAILED")));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token", description = "Exchange a valid refresh token for a new access token.")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest servletRequest) {

        log.info("POST /api/v1/auth/refresh-token");
        String ip = HttpRequestUtils.getClientIpAddress(servletRequest);
        String device = HttpRequestUtils.getDeviceInfo(servletRequest);
        RefreshTokenResponse data = authService.refreshToken(request.getRefreshToken(), ip, device);
        return ResponseEntity.ok(ApiResponse.success(data, "Làm mới token thành công"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate the current session and revoke refresh token.")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            HttpServletRequest request,
            @RequestBody(required = false) LogoutRequest body) {
        log.info("POST /api/v1/auth/logout");
        String refreshToken = body != null ? body.getRefreshToken() : null;
        authService.logout(request.getHeader("Authorization"), refreshToken);
        LogoutResponse data = LogoutResponse.builder().message("Đăng xuất thành công").build();
        return ResponseEntity.ok(ApiResponse.success(data, "Đăng xuất thành công"));
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout from all devices", description = "Revoke all refresh tokens and active sessions for the current user.")
    public ResponseEntity<ApiResponse<LogoutResponse>> logoutAll() {
        String userId = SecurityUtils.getUserId();
        log.info("POST /api/v1/auth/logout-all - user: {}", userId);
        authService.logoutAll(userId);
        LogoutResponse data = LogoutResponse.builder().message("Đã thu hồi tất cả phiên đăng nhập").build();
        return ResponseEntity.ok(ApiResponse.success(data, "Đã thu hồi tất cả phiên đăng nhập"));
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
        return ResponseEntity.ok(ApiResponse.success(null, "Vô hiệu hóa tài khoản người dùng thành công"));
    }

    @PatchMapping("/me/deactivate")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Deactivate my account", description = "Deactivate the currently logged in user account")
    public ResponseEntity<ApiResponse<?>> deactivateMe() {
        String userId = SecurityUtils.getUserId();
        log.info("PATCH /api/v1/auth/me/deactivate - user {}", userId);
        authService.softDeleteUser(userId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Vô hiệu hóa tài khoản thành công"));
    }
}
