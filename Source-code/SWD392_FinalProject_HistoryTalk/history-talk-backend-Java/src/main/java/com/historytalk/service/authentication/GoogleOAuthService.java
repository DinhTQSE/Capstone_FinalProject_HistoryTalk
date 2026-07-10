package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;

public interface GoogleOAuthService {
    LoginResponse authenticateGoogleUser(OAuth2User oauth2User);
    LoginResponse authenticateGoogleIdToken(String idToken);
}
