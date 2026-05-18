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

    RefreshTokenResponse refreshToken(String refreshToken);

    void logout(String authorizationHeader);

    void softDeleteUser(String targetUserId, String staffId);
}
