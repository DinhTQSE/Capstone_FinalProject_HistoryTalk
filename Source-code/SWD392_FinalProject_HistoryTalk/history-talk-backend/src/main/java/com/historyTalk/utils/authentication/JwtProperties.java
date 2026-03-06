package com.historyTalk.utils.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class JwtProperties {
    @Value("${JWT_SECRET}")
    private String secretKey;
    @Value("${JWT_EXPIRATION_MS}")
    private long jwtExpirationMs;
    @Value("${JWT_REFRESH_EXPIRATION_MS}")
    private long jwtRefreshTokenExpirationMs;
}
