package com.historytalk.security.oauth2;

import com.historytalk.config.OAuth2Properties;
import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.service.authentication.GoogleOAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final GoogleOAuthService googleOAuthService;
    private final OAuth2Properties oauth2Properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        LoginResponse loginResponse = googleOAuthService.authenticateGoogleUser(oauth2User);

        String redirectUrl = UriComponentsBuilder.fromUriString(oauth2Properties.getSuccessRedirectUrl())
                .queryParam("accessToken", loginResponse.getAccessToken())
                .queryParam("refreshToken", loginResponse.getRefreshToken())
                .queryParam("tokenType", loginResponse.getTokenType())
                .queryParam("expiresIn", loginResponse.getExpiresIn())
                .queryParam("uid", loginResponse.getUid())
                .queryParam("userName", loginResponse.getUserName())
                .queryParam("email", loginResponse.getEmail())
                .queryParam("role", loginResponse.getRole())
                .build()
                .encode()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
