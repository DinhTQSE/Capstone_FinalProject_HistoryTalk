package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.RefreshTokenResponse;
import com.historytalk.entity.authentication.RefreshToken;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.authentication.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, jwtService);
        ReflectionTestUtils.setField(refreshTokenService, "jwtRefreshExpirationInMs", 604800000L); // 7 days
    }

    @Test
    void createRefreshTokenSavesHashedTokenAndReturnsRaw() {
        User user = testUser();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(user, "127.0.0.1", "Mozilla/5.0");

        assertThat(rawToken).isNotBlank();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getFamilyId()).isNotNull();
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(86400 * 6));
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getDeviceInfo()).isEqualTo("Mozilla/5.0");
    }

    @Test
    void rotateRefreshTokenSuccessReturnsRotatedTokenWithSameFamilyId() {
        User user = testUser();
        UUID familyId = UUID.randomUUID();

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String rawToken = refreshTokenService.createRefreshToken(user, "127.0.0.1", "Chrome");

        String hash = sha256Hex(rawToken);
        RefreshToken existingToken = RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .user(user)
                .tokenHash(hash)
                .familyId(familyId)
                .isRevoked(false)
                .expiresAt(Instant.now().plusSeconds(86400 * 5))
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(existingToken));
        when(jwtService.generateAccessToken(eq(user.getEmail()), anyMap())).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        RefreshTokenResponse response = refreshTokenService.rotateRefreshToken(rawToken, "10.0.0.1", "Firefox");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotEqualTo(rawToken);
        assertThat(existingToken.isRevoked()).isTrue();
    }

    @Test
    void rotateRefreshTokenReuseDetectionRevokesFamilyAndThrowsUnauthorized() {
        User user = testUser();
        UUID familyId = UUID.randomUUID();

        String rawToken = "reused-stolen-token";
        String hash = sha256Hex(rawToken);

        RefreshToken revokedToken = RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .user(user)
                .tokenHash(hash)
                .familyId(familyId)
                .isRevoked(true) // ALREADY REVOKED!
                .expiresAt(Instant.now().plusSeconds(86400 * 5))
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken, "1.1.1.1", "HackerBot"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Cảnh báo an ninh: Refresh token đã bị thu hồi trước đó");

        verify(refreshTokenRepository).revokeFamily(eq(familyId));
    }

    @Test
    void rotateRefreshTokenRejectsExpiredToken() {
        User user = testUser();
        UUID familyId = UUID.randomUUID();

        String rawToken = "expired-token";
        String hash = sha256Hex(rawToken);

        RefreshToken expiredToken = RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .user(user)
                .tokenHash(hash)
                .familyId(familyId)
                .isRevoked(false)
                .expiresAt(Instant.now().minusSeconds(300)) // EXPIRED
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken, "127.0.0.1", "Chrome"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token đã hết hạn");
    }

    @Test
    void revokeTokenRevokesExistingToken() {
        String rawToken = "token-to-revoke";
        String hash = sha256Hex(rawToken);

        RefreshToken token = RefreshToken.builder()
                .tokenId(UUID.randomUUID())
                .user(testUser())
                .tokenHash(hash)
                .familyId(UUID.randomUUID())
                .isRevoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        refreshTokenService.revokeToken(rawToken);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revokeAllUserTokensCallsRepository() {
        UUID userId = UUID.randomUUID();
        refreshTokenService.revokeAllUserTokens(userId);
        verify(refreshTokenRepository).revokeAllUserTokens(userId);
    }

    private User testUser() {
        return User.builder()
                .uid(UUID.randomUUID())
                .userName("testuser")
                .email("test@example.com")
                .role(UserRole.CUSTOMER)
                .build();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
