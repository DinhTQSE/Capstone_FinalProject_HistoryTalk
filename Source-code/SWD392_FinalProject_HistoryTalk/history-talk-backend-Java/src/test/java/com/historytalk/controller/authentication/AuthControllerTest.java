package com.historytalk.controller.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.authentication.ForgotPasswordRequest;
import com.historytalk.dto.authentication.LoginRequest;
import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.dto.authentication.RefreshTokenRequest;
import com.historytalk.dto.authentication.RefreshTokenResponse;
import com.historytalk.dto.authentication.RegisterRequest;
import com.historytalk.dto.authentication.RegisterResponse;
import com.historytalk.dto.authentication.ResetPasswordRequest;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.service.authentication.AuthService;
import com.historytalk.service.authentication.GoogleOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private GoogleOAuthService googleOAuthService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("IT-AUTH-01 (F-01): POST /api/v1/auth/register creates new customer account and returns 201 Created")
    void register_ValidRequest_Returns201() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .userName("new_customer")
                .email("new_customer@example.com")
                .password("Pass@12345")
                .confirmPassword("Pass@12345")
                .build();

        RegisterResponse res = RegisterResponse.builder()
                .uid(UUID.randomUUID().toString())
                .userName("new_customer")
                .email("new_customer@example.com")
                .role("CUSTOMER")
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userName").value("new_customer"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("IT-AUTH-02 (WF-01 / F-01): POST /api/v1/auth/login authenticates user and returns JWT tokens")
    void login_ValidCredentials_Returns200AndTokens() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .email("schooladmin@cva.edu.vn")
                .password("Ht@123456")
                .build();

        LoginResponse res = LoginResponse.builder()
                .accessToken("jwt.access.token")
                .refreshToken("jwt.refresh.token")
                .expiresIn(3600L)
                .build();

        when(authService.login(any(LoginRequest.class), anyString(), anyString())).thenReturn(res);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("jwt.access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("jwt.refresh.token"));
    }

    @Test
    @DisplayName("IT-AUTH-03 (WF-01 / F-01): POST /api/v1/auth/login with wrong password returns 400 Bad Request")
    void login_InvalidCredentials_Returns400() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .email("schooladmin@cva.edu.vn")
                .password("WrongPass")
                .build();

        when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                .thenThrow(new InvalidRequestException("Email hoặc mật khẩu không hợp lệ"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("IT-AUTH-04 (F-01): POST /api/v1/auth/refresh-token rotates refresh token and returns new access token")
    void refreshToken_ValidToken_Returns200() throws Exception {
        RefreshTokenRequest req = RefreshTokenRequest.builder()
                .refreshToken("valid-refresh-token")
                .build();

        RefreshTokenResponse res = RefreshTokenResponse.builder()
                .accessToken("new.access.token")
                .refreshToken("new.refresh.token")
                .expiresIn(3600L)
                .build();

        when(authService.refreshToken(eq("valid-refresh-token"), anyString(), anyString())).thenReturn(res);

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new.access.token"));
    }

    @Test
    @DisplayName("IT-AUTH-05 (F-01): POST /api/v1/auth/forgot-password and /reset-password return 200 OK")
    void forgotAndResetPassword_ValidRequests_Return200() throws Exception {
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
        forgotReq.setEmail("student@cva.edu.vn");
        doNothing().when(authService).forgotPassword("student@cva.edu.vn");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ResetPasswordRequest resetReq = ResetPasswordRequest.builder()
                .token("reset-token-123")
                .newPassword("NewPass@123")
                .confirmPassword("NewPass@123")
                .build();
        doNothing().when(authService).resetPassword("reset-token-123", "NewPass@123", "NewPass@123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).resetPassword("reset-token-123", "NewPass@123", "NewPass@123");
    }
}
