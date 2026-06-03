package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.notification.GoogleOAuthPasswordEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleOAuthServiceImpl implements GoogleOAuthService {

    private static final String GOOGLE_PASSWORD_PREFIX = "HT-GOOGLE-";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final TierRepository tierRepository;
    private final UserTierRepository userTierRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleOAuthPasswordEmailService passwordEmailService;

    @Override
    @Transactional
    public LoginResponse authenticateGoogleUser(OAuth2User oauth2User) {
        String email = extractRequiredEmail(oauth2User);
        String displayName = extractDisplayName(oauth2User);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createGoogleUserAndSendPasswordEmail(email, displayName));

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

    private User createGoogleUserAndSendPasswordEmail(String email, String displayName) {
        String temporaryPassword = generateTemporaryPassword();
        User user = createGoogleUser(email, displayName, temporaryPassword);
        sendTemporaryPasswordEmailAfterCommit(user, temporaryPassword);
        return user;
    }

    private User createGoogleUser(String email, String displayName, String temporaryPassword) {
        String userName = generateUniqueUserName(email, displayName);
        User user = User.builder()
                .userName(userName)
                .email(email)
                .password(passwordEncoder.encode(temporaryPassword))
                .role(UserRole.CUSTOMER)
                .build();

        java.util.Optional<Tier> freeTierOpt = tierRepository.findByTitleIgnoreCaseAndIsActiveTrueAndDeletedAtIsNull("free");
        if (freeTierOpt.isPresent()) {
            Tier freeTier = freeTierOpt.get();
            user.setToken(freeTier.getLimitedToken());
            user.setLastTokenResetAt(LocalDateTime.now());
        }

        User saved = userRepository.save(user);

        if (freeTierOpt.isPresent()) {
            Tier freeTier = freeTierOpt.get();
            UserTier userTier = UserTier.builder()
                    .user(saved)
                    .tier(freeTier)
                    .startTime(LocalDateTime.now())
                    .endTime(LocalDateTime.now().plusMonths(freeTier.getNoMonth() > 0 ? freeTier.getNoMonth() : 120))
                    .isActive(true)
                    .build();
            userTierRepository.save(userTier);
        }

        return saved;
    }

    private String generateTemporaryPassword() {
        byte[] randomBytes = new byte[18];
        SECURE_RANDOM.nextBytes(randomBytes);
        return GOOGLE_PASSWORD_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private void sendTemporaryPasswordEmailAfterCommit(User user, String temporaryPassword) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendTemporaryPasswordEmail(user, temporaryPassword);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendTemporaryPasswordEmail(user, temporaryPassword);
            }
        });
    }

    private void sendTemporaryPasswordEmail(User user, String temporaryPassword) {
        try {
            passwordEmailService.sendTemporaryPasswordEmail(user.getEmail(), user.getUserName(), temporaryPassword);
        } catch (RuntimeException ex) {
            log.warn("Could not send Google OAuth temporary password email for uid: {}", user.getUid());
        }
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
