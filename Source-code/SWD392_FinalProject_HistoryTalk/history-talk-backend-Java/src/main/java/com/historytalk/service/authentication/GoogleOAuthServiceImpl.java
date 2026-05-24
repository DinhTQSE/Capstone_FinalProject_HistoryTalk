package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleOAuthServiceImpl implements GoogleOAuthService {

    private static final String GOOGLE_PASSWORD_PREFIX = "GOOGLE_OAUTH_";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public LoginResponse authenticateGoogleUser(OAuth2User oauth2User) {
        String email = extractRequiredEmail(oauth2User);
        String displayName = extractDisplayName(oauth2User);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createGoogleUser(email, displayName));

        if (user.getDeletedAt() != null) {
            throw new UnauthorizedException("Account has been deactivated");
        }

        UserPrincipal principal = new UserPrincipal(user);
        Map<String, Object> claims = buildClaims(principal);
        String accessToken = jwtService.generateAccessToken(user.getEmail(), claims);
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        log.info("Google OAuth login successful for uid: {}", user.getUid());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs())
                .uid(user.getUid().toString())
                .userName(user.getUserName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    private String extractRequiredEmail(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        if (!StringUtils.hasText(email)) {
            throw new UnauthorizedException("Google account email is required");
        }
        return email.toLowerCase();
    }

    private String extractDisplayName(OAuth2User oauth2User) {
        String name = oauth2User.getAttribute("name");
        return StringUtils.hasText(name) ? name : null;
    }

    private User createGoogleUser(String email, String displayName) {
        String userName = generateUniqueUserName(email, displayName);
        User user = User.builder()
                .userName(userName)
                .email(email)
                .password(passwordEncoder.encode(GOOGLE_PASSWORD_PREFIX + UUID.randomUUID()))
                .role(UserRole.CUSTOMER)
                .build();
        return userRepository.save(user);
    }

    private String generateUniqueUserName(String email, String displayName) {
        String fallback = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
        String base = sanitizeUserName(StringUtils.hasText(displayName) ? displayName : fallback);

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUserNameIgnoreCase(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private String sanitizeUserName(String input) {
        String sanitized = input.toLowerCase()
                .replaceAll("[^a-z0-9._-]", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "");

        if (sanitized.length() < 3) {
            sanitized = "user" + sanitized;
        }
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized;
    }

    private Map<String, Object> buildClaims(UserPrincipal principal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", principal.getUid());
        claims.put("role", principal.getRole().name());
        return claims;
    }
}
