package com.historyTalk.service.authentication;

import com.historyTalk.dto.authentication.LoginRequest;
import com.historyTalk.dto.authentication.LoginResponse;
import com.historyTalk.dto.authentication.RefreshTokenResponse;
import com.historyTalk.dto.authentication.RegisterRequest;
import com.historyTalk.dto.authentication.RegisterResponse;
import com.historyTalk.dto.authentication.RegisterStaffRequest;
import com.historyTalk.dto.authentication.RegisterStaffResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    RegisterStaffResponse registerStaff(RegisterStaffRequest request);

    LoginResponse login(LoginRequest request);

    RefreshTokenResponse refreshToken(String refreshToken);

    void logout(String authorizationHeader);

    void softDeleteUser(String targetUserId, String adminId);
}
