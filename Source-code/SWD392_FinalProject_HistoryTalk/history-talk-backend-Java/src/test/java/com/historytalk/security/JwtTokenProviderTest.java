package com.historytalk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 900000L);
    }

    @Test
    void generateAccessTokenIncludesSubUidRoleTypAndIssuer() {
        String token = provider.generateAccessToken(
                "user@example.com",
                Map.of("uid", "11111111-1111-1111-1111-111111111111", "role", "CUSTOMER")
        );

        assertThat(provider.validateToken(token)).isTrue();
        Claims claims = provider.getAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo("user@example.com");
        assertThat(claims.getIssuer()).isEqualTo("historytalk-api");
        assertThat(claims.get("typ", String.class)).isEqualTo("access");
        assertThat(claims.get("uid", String.class)).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void validateTokenRejectsTokenWithoutAccessTypOrIssuer() {
        String forgedOrRefreshLike = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
                .compact();

        assertThat(provider.validateToken(forgedOrRefreshLike)).isFalse();
    }
}
