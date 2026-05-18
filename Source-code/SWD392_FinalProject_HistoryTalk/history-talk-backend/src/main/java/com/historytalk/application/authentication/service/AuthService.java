package com.historytalk.application.authentication.service;

import com.historytalk.presentation.authentication.dto.LoginRequest;
import com.historytalk.presentation.authentication.dto.LoginResponse;
import com.historytalk.presentation.authentication.dto.RefreshTokenResponse;
import com.historytalk.presentation.authentication.dto.RegisterRequest;
import com.historytalk.presentation.authentication.dto.RegisterResponse;
import com.historytalk.presentation.authentication.dto.RegisterStaffRequest;
import com.historytalk.presentation.authentication.dto.RegisterStaffResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    RegisterStaffResponse registerStaff(RegisterStaffRequest request);

    LoginResponse login(LoginRequest request);

    RefreshTokenResponse refreshToken(String refreshToken);

    void logout(String authorizationHeader);

    void softDeleteUser(String targetUserId, String staffId);
}
