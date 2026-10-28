package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.RefreshTokenResponse;
import com.historytalk.entity.authentication.RefreshToken;
import com.historytalk.entity.user.User;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.authentication.RefreshTokenRepository;
import com.historytalk.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Value("${JWT_REFRESH_EXPIRATION_MS:604800000}")
    private long jwtRefreshExpirationInMs;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    @Transactional
    public String createRefreshToken(User user, String ipAddress, String deviceInfo) {
        String rawToken = generateSecureRandomToken();
        String tokenHash = sha256Hex(rawToken);
        UUID familyId = UUID.randomUUID();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .isRevoked(false)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .expiresAt(Instant.now().plusMillis(jwtRefreshExpirationInMs))
                .build();

        refreshTokenRepository.save(token);
        log.info("Created new refresh token for user {} with familyId {}", user.getUid(), familyId);
        return rawToken;
    }

    @Override
    @Transactional
    public RefreshTokenResponse rotateRefreshToken(String rawRefreshToken, String ipAddress, String deviceInfo) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new UnauthorizedException("Refresh token không được để trống");
        }

        String tokenHash = sha256Hex(rawRefreshToken);
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token không hợp lệ hoặc không tồn tại"));

        // 1. Kiểm tra hết hạn thời gian
        if (oldToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token đã hết hạn");
        }

        // 2. BÁO ĐỘNG ĐỎ: Reuse Detection
        if (oldToken.isRevoked()) {
            log.warn("SECURITY ALERT: Phát hiện hành vi tái sử dụng Refresh Token! FamilyId: {}, User: {}",
                    oldToken.getFamilyId(), oldToken.getUser().getEmail());
            // Khai tử toàn bộ chuỗi token thuộc familyId này
            refreshTokenRepository.revokeFamily(oldToken.getFamilyId());
            throw new UnauthorizedException("Cảnh báo an ninh: Refresh token đã bị thu hồi trước đó. Toàn bộ phiên làm việc liên quan đã bị hủy.");
        }

        User user = oldToken.getUser();
        if (user.getDeletedAt() != null) {
            throw new UnauthorizedException("Tài khoản đã bị vô hiệu hóa");
        }

        // 3. Xoay vòng token hợp lệ: Hủy token cũ
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        // 4. Sinh token mới kế thừa cùng familyId
        String newRawRefreshToken = generateSecureRandomToken();
        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(newRawRefreshToken))
                .familyId(oldToken.getFamilyId())
                .isRevoked(false)
                .deviceInfo(StringUtils.hasText(deviceInfo) ? deviceInfo : oldToken.getDeviceInfo())
                .ipAddress(StringUtils.hasText(ipAddress) ? ipAddress : oldToken.getIpAddress())
                .expiresAt(Instant.now().plusMillis(jwtRefreshExpirationInMs))
                .build();
        refreshTokenRepository.save(newToken);

        // 5. Sinh Access Token mới (15 phút)
        UserPrincipal principal = new UserPrincipal(user);
        Map<String, Object> claims = buildClaims(principal);
        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), claims);

        log.info("Rotated refresh token successfully for user {} in familyId {}", user.getUid(), oldToken.getFamilyId());

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs())
                .build();
    }

    @Override
    @Transactional
    public void revokeToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }
        String tokenHash = sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for user {} (familyId: {})", token.getUser().getUid(), token.getFamilyId());
        });
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.revokeAllUserTokens(userId);
        log.info("Revoked all refresh tokens for user {}", userId);
    }

    private Map<String, Object> buildClaims(UserPrincipal principal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", principal.getUid());
        claims.put("role", principal.getRole().name());
        return claims;
    }

    private String generateSecureRandomToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng", ex);
        }
    }
}
