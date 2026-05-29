package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginRequest;
import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.dto.authentication.RefreshTokenResponse;
import com.historytalk.dto.authentication.RegisterRequest;
import com.historytalk.dto.authentication.RegisterResponse;
import com.historytalk.dto.authentication.RegisterStaffRequest;
import com.historytalk.dto.authentication.RegisterStaffResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.security.UserPrincipal;
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
    private final TierRepository tierRepository;
    private final UserTierRepository userTierRepository;
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

        java.util.Optional<Tier> freeTierOpt = tierRepository.findByTitleIgnoreCaseAndIsActiveTrueAndDeletedAtIsNull("free");
        if (freeTierOpt.isPresent()) {
            Tier freeTier = freeTierOpt.get();
            user.setTier(freeTier);
            user.setToken(freeTier.getLimitedToken());
            user.setLastTokenResetAt(LocalDateTime.now());
        }

        User saved = userRepository.save(user);

        if (freeTierOpt.isPresent()) {
            Tier freeTier = freeTierOpt.get();
            UserTier userTier = UserTier.builder()
                    .user(saved)
                    .tier(freeTier)
                    .startTime(LocalDateTime.now())
                    .endTime(LocalDateTime.now().plusMonths(freeTier.getNoMonth() > 0 ? freeTier.getNoMonth() : 120)) // 10 years for free tier if no_month is 0
                    .isActive(true)
                    .build();
            userTierRepository.save(userTier);
        }

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
        role = parsePrivilegedRole(request.getRole());
        if (role == UserRole.CUSTOMER) {
            throw new InvalidRequestException("Cannot register a privileged account with role CUSTOMER. Use the public register endpoint instead.");
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
    public void softDeleteUser(String targetUserId, String staffId) {
        log.info("Soft deleting user {} requested by user {}", targetUserId, staffId);
        
        User requestUser = userRepository.findById(UUID.fromString(staffId))
                .orElseThrow(() -> new ResourceNotFoundException("Requesting user not found"));
                
        if (!targetUserId.equals(staffId) && requestUser.getRole() != UserRole.SYSTEM_ADMIN) {
            throw new InvalidRequestException("Only a SYSTEM_ADMIN can deactivate other user accounts.");
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
        entityManager.createQuery("UPDATE Document d SET d.deletedAt = CURRENT_TIMESTAMP WHERE d.createdBy.uid = :userId AND d.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();
        entityManager.createQuery("UPDATE Character c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.createdBy.uid = :userId AND c.deletedAt IS NULL")
                .setParameter("userId", userId).executeUpdate();

        // HistoricalContext & documents
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

    }

    private UserRole parsePrivilegedRole(String roleValue) {
        if (!StringUtils.hasText(roleValue)) {
            throw new InvalidRequestException("Role is required. Must be CONTENT_ADMIN or SYSTEM_ADMIN");
        }
        String normalized = roleValue.trim().toUpperCase();
        if ("STAFF".equals(normalized)) {
            normalized = "CONTENT_ADMIN";
        } else if ("ADMIN".equals(normalized)) {
            normalized = "SYSTEM_ADMIN";
        }
        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid role: " + roleValue + ". Must be CONTENT_ADMIN or SYSTEM_ADMIN");
        }
    }
}
