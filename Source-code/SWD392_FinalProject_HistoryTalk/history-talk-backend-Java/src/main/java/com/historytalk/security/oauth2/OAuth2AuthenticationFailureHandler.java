package com.historytalk.security.oauth2;

import com.historytalk.config.OAuth2Properties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final OAuth2Properties oauth2Properties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        log.error("Google OAuth authentication failed: {}", exception.getMessage(), exception);
        String redirectUrl = UriComponentsBuilder.fromUriString(oauth2Properties.getFailureRedirectUrl())
                .queryParam("error", "google_oauth_failed")
                .build()
                .encode()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
