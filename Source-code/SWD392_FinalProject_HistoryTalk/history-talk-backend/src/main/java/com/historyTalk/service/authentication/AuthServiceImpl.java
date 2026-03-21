package com.historyTalk.service.authentication;

import com.historyTalk.dto.authentication.LoginRequest;
import com.historyTalk.dto.authentication.LoginResponse;
import com.historyTalk.dto.authentication.RefreshTokenResponse;
import com.historyTalk.dto.authentication.RegisterRequest;
import com.historyTalk.dto.authentication.RegisterResponse;
import com.historyTalk.dto.authentication.RegisterStaffRequest;
import com.historyTalk.dto.authentication.RegisterStaffResponse;
import com.historyTalk.entity.enums.UserRole;
import com.historyTalk.entity.user.User;
import com.historyTalk.exception.DataConflictException;
import com.historyTalk.exception.InvalidRequestException;
import com.historyTalk.exception.ResourceNotFoundException;
import com.historyTalk.exception.UnauthorizedException;
import com.historyTalk.repository.UserRepository;
import com.historyTalk.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** In-memory blacklist for logged-out tokens. Replace with Redis in production. */
    public static final Set<String> BLACKLISTED_TOKENS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @PersistenceContext
    private EntityManager entityManager;

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
            throw new InvalidRequestException("Your email already existed");
        }
        if (userRepository.existsByUserNameIgnoreCase(request.getUserName())) {
            throw new InvalidRequestException("Your username already existed");
        }

        User user = User.builder()
                .userName(request.getUserName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.CUSTOMER)
                .build();

        User saved = userRepository.save(user);
        log.info("User registered with uid: {}", saved.getUid());

        return RegisterResponse.builder()
                .uid(saved.getUid().toString())
                .userName(saved.getUserName())
                .email(saved.getEmail())
                .role(saved.getRole().name())
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

        // Reject soft-deleted (deactivated) users
        if (user.getDeletedAt() != null) {
            throw new UnauthorizedException("Account has been deactivated");
        }

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
                .uid(user.getUid().toString())
                .userName(user.getUserName())
                .email(user.getEmail())
                .role(user.getRole().name())
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

    @Override
    @Transactional
    public RegisterStaffResponse registerStaff(RegisterStaffRequest request) {
        log.info("Registering new staff/admin account: {}", request.getEmail());

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new InvalidRequestException("Password and confirmation password do not match");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DataConflictException("Email already exists");
        }
        if (userRepository.existsByUserNameIgnoreCase(request.getUserName())) {
            throw new DataConflictException("Username already exists");
        }

        UserRole role;
        try {
            role = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid role: " + request.getRole() + ". Must be STAFF or ADMIN");
        }
        if (role == UserRole.CUSTOMER) {
            throw new InvalidRequestException("Cannot register a privileged account with role USER. Use the public register endpoint instead.");
        }

        User user = User.builder()
                .userName(request.getUserName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();
        User saved = userRepository.save(user);

        log.info("Staff/admin account registered with uid: {}", saved.getUid());

        return RegisterStaffResponse.builder()
                .uid(saved.getUid().toString())
                .userName(saved.getUserName())
                .email(saved.getEmail())
                .role(saved.getRole().name())
                .message("Staff account registered successfully")
                .build();
    }

    private Map<String, Object> buildClaims(UserPrincipal principal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", principal.getUid());
        claims.put("role", principal.getRole().name());
        return claims;
    }

    @Override
    @Transactional
    public void softDeleteUser(String targetUserId, String adminId) {
        log.info("Soft deleting user {} requested by user {}", targetUserId, adminId);
        
        User requestUser = userRepository.findById(UUID.fromString(adminId))
                .orElseThrow(() -> new ResourceNotFoundException("Requesting user not found"));
                
        if (!targetUserId.equals(adminId) && requestUser.getRole() != UserRole.ADMIN) {
            throw new InvalidRequestException("Only an ADMIN can deactivate other user accounts.");
        }
        
        User targetUser = userRepository.findById(UUID.fromString(targetUserId))
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));
                
        targetUser.setDeletedAt(LocalDateTime.now());
        userRepository.save(targetUser);

        // Cascade soft delete all user content
        cascadeSoftDeleteContent(targetUser.getUid());
        
        log.info("User {} successfully deactivated and content cascading initiated.", targetUserId);
    }

    private void cascadeSoftDeleteContent(UUID userId) {
        // Character & documents
        entityManager.createQuery("UPDATE CharacterDocument cd SET cd.deletedAt = CURRENT_TIMESTAMP WHERE cd.character.createdBy.uid = :userId AND cd.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();
        entityManager.createQuery("UPDATE Character c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.createdBy.uid = :userId AND c.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();

        // HistoricalContext & documents
        entityManager.createQuery("UPDATE HistoricalContextDocument hcd SET hcd.deletedAt = CURRENT_TIMESTAMP WHERE hcd.historicalContext.createdBy.uid = :userId AND hcd.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();
        entityManager.createQuery("UPDATE HistoricalContext hc SET hc.deletedAt = CURRENT_TIMESTAMP WHERE hc.createdBy.uid = :userId AND hc.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();

        // ChatSession & messages
        entityManager.createQuery("UPDATE Message m SET m.deletedAt = CURRENT_TIMESTAMP WHERE m.chatSession.user.uid = :userId AND m.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();
        entityManager.createQuery("UPDATE ChatSession cs SET cs.deletedAt = CURRENT_TIMESTAMP WHERE cs.user.uid = :userId AND cs.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();

        // QuizSession
        entityManager.createQuery("UPDATE QuizSession qs SET qs.deletedAt = CURRENT_TIMESTAMP WHERE qs.user.uid = :userId AND qs.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();

        // QuizResult & answer details
        entityManager.createQuery("UPDATE QuizAnswerDetail qd SET qd.deletedAt = CURRENT_TIMESTAMP WHERE qd.quizResult.user.uid = :userId AND qd.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();
        entityManager.createQuery("UPDATE QuizResult qr SET qr.deletedAt = CURRENT_TIMESTAMP WHERE qr.user.uid = :userId AND qr.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();
    }
}
