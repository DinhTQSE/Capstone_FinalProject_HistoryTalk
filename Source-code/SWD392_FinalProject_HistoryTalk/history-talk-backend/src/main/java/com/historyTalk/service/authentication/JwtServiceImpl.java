package com.historyTalk.service.authentication;

import com.historyTalk.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public String generateAccessToken(String email, Map<String, Object> extraClaims) {
        return jwtTokenProvider.generateAccessToken(email, extraClaims);
    }

    @Override
    public String generateRefreshToken(String email) {
        return jwtTokenProvider.generateRefreshToken(email);
    }

    @Override
    public String extractEmail(String token) {
        return jwtTokenProvider.getUsernameFromToken(token);
    }

    @Override
    public Claims extractAllClaims(String token) {
        return jwtTokenProvider.getAllClaims(token);
    }

    @Override
    public boolean isTokenValid(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    @Override
    public long getAccessTokenExpirationMs() {
        return jwtTokenProvider.getJwtExpirationInMs();
    }
}
