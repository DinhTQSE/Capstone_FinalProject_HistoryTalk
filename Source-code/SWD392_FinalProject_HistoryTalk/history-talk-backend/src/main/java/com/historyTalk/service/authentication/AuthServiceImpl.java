package com.historyTalk.service.authentication;

import com.historyTalk.dto.authentication.LoginRequest;
import com.historyTalk.dto.authentication.LoginResponse;
import com.historyTalk.dto.authentication.RefreshTokenResponse;
import com.historyTalk.dto.authentication.RegisterRequest;
import com.historyTalk.dto.authentication.RegisterResponse;
import com.historyTalk.entity.User;
import com.historyTalk.entity.UserType;
import com.historyTalk.exception.DuplicateResourceException;
import com.historyTalk.exception.UnauthorizedException;
import com.historyTalk.repository.UserRepository;
import com.historyTalk.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** In-memory blacklist for logged-out tokens. Replace with Redis in production. */
    public static final Set<String> BLACKLISTED_TOKENS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getEmail());

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and confirmation password do not match");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (userRepository.existsByUserNameIgnoreCase(request.getUserName())) {
            throw new DuplicateResourceException("User", "userName", request.getUserName());
        }

        User user = User.builder()
                .userName(request.getUserName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .userType(UserType.REGISTERED)
                .build();

        User saved = userRepository.save(user);
        log.info("User registered with uid: {}", saved.getUid());

        return RegisterResponse.builder()
                .uid(saved.getUid())
                .userName(saved.getUserName())
                .email(saved.getEmail())
                .userType(saved.getUserType())
                .message("User registered successfully")
                .build();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        UserPrincipal principal = new UserPrincipal(user);
        Map<String, Object> claims = buildClaims(principal);

        String accessToken  = jwtService.generateAccessToken(user.getEmail(), claims);
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        log.info("Login successful for uid: {}", user.getUid());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs())
                .uid(user.getUid())
                .userName(user.getUserName())
                .email(user.getEmail())
                .userType(user.getUserType())
                .build();
    }

    @Override
    public RefreshTokenResponse refreshToken(String refreshToken) {
        log.info("Refreshing token");

        if (!StringUtils.hasText(refreshToken) || !jwtService.isTokenValid(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        if (BLACKLISTED_TOKENS.contains(refreshToken)) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        UserPrincipal principal = new UserPrincipal(user);
        Map<String, Object> claims = buildClaims(principal);

        String newAccessToken = jwtService.generateAccessToken(email, claims);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs())
                .build();
    }

    @Override
    public void logout(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid Authorization header format. Expected: Bearer <token>");
        }
        String token = authorizationHeader.substring(7);
        BLACKLISTED_TOKENS.add(token);
        log.info("Token blacklisted on logout");
    }

    private Map<String, Object> buildClaims(UserPrincipal principal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", principal.getUid());
        claims.put("userType", principal.getUserType().name());
        if (principal.getRoleName() != null) {
            claims.put("roleName", principal.getRoleName());
        }
        return claims;
    }
}
