package com.historyTalk.service.authentication;

import io.jsonwebtoken.Claims;

import java.util.Map;

/**
 * Simplified JWT operations wrapper used by the Auth service layer.
 * Delegates to {@link com.historyTalk.security.JwtTokenProvider}.
 */
public interface JwtService {

    String generateAccessToken(String email, Map<String, Object> extraClaims);

    String generateRefreshToken(String email);

    String extractEmail(String token);

    Claims extractAllClaims(String token);

    boolean isTokenValid(String token);

    long getAccessTokenExpirationMs();
}
