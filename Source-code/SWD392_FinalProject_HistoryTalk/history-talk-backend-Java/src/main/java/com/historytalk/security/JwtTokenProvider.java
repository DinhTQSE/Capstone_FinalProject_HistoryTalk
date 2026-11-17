package com.historytalk.security;

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

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String ISSUER = "historytalk-api";

    @Value("${JWT_SECRET}")
    private String jwtSecret;

    @Value("${JWT_EXPIRATION_MS}")
    private long jwtExpirationInMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Generate access token with extra claims (uid, role).
     * In JJWT 0.12.x, .claims(map) overrides previously set claims, so extra claims
     * must be populated before setting standard claims.
     */
    public String generateAccessToken(String email, Map<String, Object> extraClaims) {
        var builder = Jwts.builder();
        if (extraClaims != null && !extraClaims.isEmpty()) {
            builder.claims(extraClaims);
        }
        return builder
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
                .issuer(ISSUER)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
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
     * Extract issuedAt Date from token.
     */
    public Date getIssuedAtFromToken(String token) {
        return getAllClaims(token).getIssuedAt();
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
     * Validate access token signature, expiry, issuer, and token type.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(ISSUER)
                    .require(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject() != null && !claims.getSubject().isBlank();
        } catch (Exception ex) {
            log.error("Token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public long getJwtExpirationInMs() {
        return jwtExpirationInMs;
    }
}
