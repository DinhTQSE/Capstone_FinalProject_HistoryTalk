package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginRequest;
import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.dto.authentication.RefreshTokenResponse;
import com.historytalk.dto.authentication.RegisterRequest;
import com.historytalk.dto.authentication.RegisterResponse;
import com.historytalk.dto.authentication.RegisterStaffRequest;
import com.historytalk.dto.authentication.RegisterStaffResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    RegisterStaffResponse registerStaff(RegisterStaffRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse login(LoginRequest request, String ipAddress, String deviceInfo);

    void forgotPassword(String email);

    void resetPassword(String token, String newPassword, String confirmPassword);

    RefreshTokenResponse refreshToken(String refreshToken);

    RefreshTokenResponse refreshToken(String refreshToken, String ipAddress, String deviceInfo);

    void logout(String authorizationHeader);

    void logout(String authorizationHeader, String refreshToken);

    void logoutAll(String userId);

    void softDeleteUser(String targetUserId, String staffId);
}
