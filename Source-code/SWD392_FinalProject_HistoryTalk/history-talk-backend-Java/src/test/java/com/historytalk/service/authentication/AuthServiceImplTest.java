package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginRequest;
import com.historytalk.dto.authentication.RegisterRequest;
import com.historytalk.dto.authentication.RegisterResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.service.notification.EmailVerificationService;
import com.historytalk.service.notification.PasswordResetEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TierRepository tierRepository;

    @Mock
    private UserTierRepository userTierRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordResetEmailService passwordResetEmailService;

    @Mock
    private EmailVerificationService emailVerificationService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                tierRepository,
                userTierRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                refreshTokenService,
                passwordResetEmailService,
                emailVerificationService
        );
        ReflectionTestUtils.setField(authService, "passwordResetTokenExpirationMinutes", 30L);
        ReflectionTestUtils.setField(authService, "emailVerificationTokenExpirationMinutes", 30L);
        ReflectionTestUtils.setField(authService, "emailVerificationResendCooldownSeconds", 60L);
    }

    @Test
    void forgotPasswordStoresHashedTokenFutureExpiryAndSendsRawToken() {
        User user = user("customer", "customer@example.com");
        when(userRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(" Customer@Example.com ");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getPasswordResetTokenHash()).hasSize(64);
        assertThat(saved.getPasswordResetExpiresAt()).isAfter(LocalDateTime.now());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetEmailService)
                .sendPasswordResetEmail(eq("customer@example.com"), eq("customer"), tokenCaptor.capture());
        String rawToken = tokenCaptor.getValue();
        assertThat(rawToken).isNotBlank();
        assertThat(saved.getPasswordResetTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getPasswordResetTokenHash()).isEqualTo(sha256Hex(rawToken));
    }

    @Test
    void forgotPasswordMissingEmailDoesNothing() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("missing@example.com");

        verify(userRepository, never()).save(any(User.class));
        verify(passwordResetEmailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void forgotPasswordDeletedUserDoesNothing() {
        User user = user("deleted", "deleted@example.com");
        user.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByEmailIgnoreCase("deleted@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword("deleted@example.com");

        verify(userRepository, never()).save(any(User.class));
        verify(passwordResetEmailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void forgotPasswordContinuesWhenEmailServiceThrowsAndKeepsSavedResetFields() {
        User user = user("customer", "customer@example.com");
        when(userRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("smtp failure"))
                .when(passwordResetEmailService)
                .sendPasswordResetEmail(eq("customer@example.com"), eq("customer"), any());

        assertThatCode(() -> authService.forgotPassword("customer@example.com"))
                .doesNotThrowAnyException();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getPasswordResetTokenHash()).hasSize(64);
        assertThat(saved.getPasswordResetExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void resetPasswordUpdatesEncodedPasswordAndClearsResetFieldsForValidToken() {
        String rawToken = "valid-reset-token";
        User user = user("customer", "customer@example.com");
        user.setPasswordResetTokenHash(sha256Hex(rawToken));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByPasswordResetTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        authService.resetPassword(rawToken, "new-password", "new-password");

        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        assertThat(user.getPasswordResetTokenHash()).isNull();
        assertThat(user.getPasswordResetExpiresAt()).isNull();
        assertThat(user.getPasswordChangedAt()).isNotNull();
        verify(refreshTokenService).revokeAllUserTokens(user.getUid());
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordRejectsMissingOrBlankToken() {
        assertThatThrownBy(() -> authService.resetPassword(null, "new-password", "new-password"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        assertThatThrownBy(() -> authService.resetPassword("  ", "new-password", "new-password"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");

        verify(userRepository, never()).findByPasswordResetTokenHash(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void resetPasswordRejectsTokenHashNotFound() {
        String rawToken = "unknown-reset-token";
        when(userRepository.findByPasswordResetTokenHash(sha256Hex(rawToken))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "new-password", "new-password"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsDeletedUser() {
        String rawToken = "deleted-user-token";
        User user = user("customer", "customer@example.com");
        user.setDeletedAt(LocalDateTime.now());
        user.setPasswordResetTokenHash(sha256Hex(rawToken));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByPasswordResetTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "new-password", "new-password"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsNullExpiry() {
        String rawToken = "null-expiry-token";
        User user = user("customer", "customer@example.com");
        user.setPasswordResetTokenHash(sha256Hex(rawToken));
        user.setPasswordResetExpiresAt(null);
        when(userRepository.findByPasswordResetTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "new-password", "new-password"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        String rawToken = "expired-reset-token";
        User user = user("customer", "customer@example.com");
        user.setPasswordResetTokenHash(sha256Hex(rawToken));
        user.setPasswordResetExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByPasswordResetTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "new-password", "new-password"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsPasswordMismatch() {
        assertThatThrownBy(() -> authService.resetPassword("reset-token", "new-password", "different-password"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Mật khẩu và mật khẩu xác nhận không khớp");

        verify(userRepository, never()).findByPasswordResetTokenHash(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void registerCreatesUnverifiedUserAndSendsVerificationEmail() {
        RegisterRequest request = new RegisterRequest("newuser", "NewUser@Gmail.com", "secret123", "secret123");
        when(userRepository.findByEmailIgnoreCase("newuser@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUserNameIgnoreCase("newuser")).thenReturn(false);
        when(tierRepository.findByTitleIgnoreCaseAndIsActiveTrueAndDeletedAtIsNull("free")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setUid(UUID.randomUUID());
            return u;
        });

        RegisterResponse response = authService.register(request);

        assertThat(response.getEmail()).isEqualTo("newuser@gmail.com");
        assertThat(response.getEmailVerified()).isFalse();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmailVerified()).isFalse();
        assertThat(saved.getEmailVerificationTokenHash()).hasSize(64);
        assertThat(saved.getEmailVerificationExpiresAt()).isAfter(LocalDateTime.now());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailVerificationService)
                .sendVerificationEmail(eq("newuser@gmail.com"), eq("newuser"), tokenCaptor.capture());
        assertThat(saved.getEmailVerificationTokenHash()).isEqualTo(sha256Hex(tokenCaptor.getValue()));
    }

    @Test
    void loginRejectsUnverifiedUser() {
        User user = user("unverified", "unverified@gmail.com");
        user.setEmailVerified(false);
        when(userRepository.findByEmailIgnoreCase("unverified@gmail.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("unverified@gmail.com", "encoded-password");
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "Chrome"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("EMAIL_NOT_VERIFIED");
    }

    @Test
    void verifyEmailMarksUserVerifiedAndClearsToken() {
        String rawToken = "valid-verify-token";
        User user = user("customer", "customer@gmail.com");
        user.setEmailVerified(false);
        user.setEmailVerificationTokenHash(sha256Hex(rawToken));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(15));
        when(userRepository.findByEmailVerificationTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(user));

        authService.verifyEmail(rawToken);

        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(user.getEmailVerificationTokenHash()).isNull();
        assertThat(user.getEmailVerificationExpiresAt()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        String rawToken = "expired-verify-token";
        User user = user("customer", "customer@gmail.com");
        user.setEmailVerified(false);
        user.setEmailVerificationTokenHash(sha256Hex(rawToken));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmailVerificationTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyEmail(rawToken))
                .isInstanceOf(UnauthorizedException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resendVerificationEmailRespectsCooldown() {
        User user = user("customer", "customer@gmail.com");
        user.setEmailVerified(false);
        user.setEmailVerificationSentAt(LocalDateTime.now().minusSeconds(10));
        when(userRepository.findByEmailIgnoreCase("customer@gmail.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resendVerificationEmail("customer@gmail.com"))
                .isInstanceOf(InvalidRequestException.class);
        verify(emailVerificationService, never()).sendVerificationEmail(any(), any(), any());
    }

    private User user(String userName, String email) {
        return User.builder()
                .uid(UUID.randomUUID())
                .userName(userName)
                .email(email)
                .password("encoded-password")
                .role(UserRole.CUSTOMER)
                .build();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
