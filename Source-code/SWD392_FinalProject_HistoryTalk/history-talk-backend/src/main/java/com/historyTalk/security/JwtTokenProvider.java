package com.historyTalk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${JWT_SECRET}")
    private String jwtSecret;

    @Value("${JWT_EXPIRATION_MS}")
    private long jwtExpirationInMs;

    @Value("${JWT_REFRESH_EXPIRATION_MS}")
    private long jwtRefreshExpirationInMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Generate access token with extra claims (uid, role).
     */
    public String generateAccessToken(String email, Map<String, Object> extraClaims) {
        return Jwts.builder()
                .subject(email)
                .claims(extraClaims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
                .signWith(signingKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Generate refresh token (subject only, longer expiry).
     */
    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtRefreshExpirationInMs))
                .signWith(signingKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Extract subject (email) from token.
     */
    public String getUsernameFromToken(String token) {
        return getAllClaims(token).getSubject();
    }

    /**
     * Extract all claims from token.
     */
    public Claims getAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validate token signature and expiry.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            log.error("Token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public long getJwtExpirationInMs() {
        return jwtExpirationInMs;
    }
}
