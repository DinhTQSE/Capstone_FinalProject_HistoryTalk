package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.RefreshTokenResponse;
import com.historytalk.entity.user.User;

import java.util.UUID;

public interface RefreshTokenService {

    /**
     * Create initial refresh token upon login with new family_id.
     * Returns raw random 64-byte string to be given to client.
     */
    String createRefreshToken(User user, String ipAddress, String deviceInfo);

    /**
     * Rotate refresh token using RTR (Refresh Token Rotation).
     * If reuse of revoked token is detected, revokes whole family_id and throws 401.
     * Returns newly generated { accessToken, refreshToken, expiresIn, tokenType }.
     */
    RefreshTokenResponse rotateRefreshToken(String rawRefreshToken, String ipAddress, String deviceInfo);

    /**
     * Revoke single refresh token during logout.
     */
    void revokeToken(String rawRefreshToken);

    /**
     * Revoke all refresh tokens for a user (logout-all, password change, deactivation).
     */
    void revokeAllUserTokens(UUID userId);
}
