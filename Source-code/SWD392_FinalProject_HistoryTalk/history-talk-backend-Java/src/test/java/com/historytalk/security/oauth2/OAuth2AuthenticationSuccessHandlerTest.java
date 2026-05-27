package com.historytalk.security.oauth2;

import com.historytalk.config.OAuth2Properties;
import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.service.authentication.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private OAuth2Properties oauth2Properties;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Test
    void onAuthenticationSuccess_redirectsToFrontendWithLoginResult() throws Exception {
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "user@gmail.com"),
                "email"
        );
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600000L)
                .uid("user-id")
                .userName("user")
                .email("user@gmail.com")
                .role("CUSTOMER")
                .build();

        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(googleOAuthService.authenticateGoogleUser(oauth2User)).thenReturn(loginResponse);
        when(oauth2Properties.getSuccessRedirectUrl()).thenReturn("http://localhost:5173/auth/google/success");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("http://localhost:5173/auth/google/success?accessToken=access-token&refreshToken=refresh-token&tokenType=Bearer&expiresIn=3600000&uid=user-id&userName=user&email=user@gmail.com&role=CUSTOMER");
    }
}
