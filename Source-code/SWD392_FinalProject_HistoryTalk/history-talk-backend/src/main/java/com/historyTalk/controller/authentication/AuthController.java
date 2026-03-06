package com.historyTalk.controller.authentication;

import com.historyTalk.dto.ApiResponse;
import com.historyTalk.dto.authentication.LoginRequest;
import com.historyTalk.dto.authentication.LoginResponse;
import com.historyTalk.dto.authentication.LogoutResponse;
import com.historyTalk.dto.authentication.RefreshTokenRequest;
import com.historyTalk.dto.authentication.RefreshTokenResponse;
import com.historyTalk.dto.authentication.RegisterRequest;
import com.historyTalk.dto.authentication.RegisterResponse;
import com.historyTalk.dto.authentication.RegisterStaffRequest;
import com.historyTalk.dto.authentication.RegisterStaffResponse;
import com.historyTalk.service.authentication.AuthService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration, login, logout and token refresh")
public class AuthController {

    private final AuthService authService;

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

    @PostMapping("/register-staff")
//    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Register staff/admin account",
               description = "Creates a new STAFF or ADMIN account. Requires ADMIN role.")
    public ResponseEntity<ApiResponse<RegisterStaffResponse>> registerStaff(
            @Valid @RequestBody RegisterStaffRequest request) {

        log.info("POST /api/v1/auth/register-staff - email: {}", request.getEmail());
        RegisterStaffResponse data = authService.registerStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Staff account registered successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with email and password, returns JWT tokens.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("POST /api/v1/auth/login - email: {}", request.getEmail());
        LoginResponse data = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(data, "Login successful"));
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
}
