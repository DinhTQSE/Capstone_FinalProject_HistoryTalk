package com.historyTalk.service.authentication;

import com.historyTalk.dto.authentication.LoginRequest;
import com.historyTalk.dto.authentication.LoginResponse;
import com.historyTalk.dto.authentication.RefreshTokenResponse;
import com.historyTalk.dto.authentication.RegisterRequest;
import com.historyTalk.dto.authentication.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    RefreshTokenResponse refreshToken(String refreshToken);

    void logout(String authorizationHeader);
}
