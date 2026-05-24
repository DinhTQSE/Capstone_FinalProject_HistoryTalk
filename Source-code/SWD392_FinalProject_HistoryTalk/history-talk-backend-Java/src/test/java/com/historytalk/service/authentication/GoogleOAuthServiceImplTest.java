package com.historytalk.service.authentication;

import com.historytalk.dto.authentication.LoginResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private GoogleOAuthServiceImpl googleOAuthService;

    @Test
    void authenticateGoogleUser_createsCustomerWhenEmailDoesNotExist() {
        OAuth2User oauth2User = oauth2User("new.user@gmail.com", "New User");
        User saved = user("new.user", "new.user@gmail.com", UserRole.CUSTOMER);

        when(userRepository.findByEmailIgnoreCase("new.user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUserNameIgnoreCase("new.user")).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-placeholder");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtService.generateAccessToken(eq("new.user@gmail.com"), anyMap())).thenReturn("access-token");
        when(jwtService.generateRefreshToken("new.user@gmail.com")).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);

        LoginResponse response = googleOAuthService.authenticateGoogleUser(oauth2User);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new.user@gmail.com");
        assertThat(userCaptor.getValue().getUserName()).isEqualTo("new.user");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-placeholder");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getEmail()).isEqualTo("new.user@gmail.com");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void authenticateGoogleUser_reusesExistingActiveUser() {
        User existing = user("existing", "existing@gmail.com", UserRole.CONTENT_ADMIN);
        OAuth2User oauth2User = oauth2User("existing@gmail.com", "Existing User");

        when(userRepository.findByEmailIgnoreCase("existing@gmail.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(eq("existing@gmail.com"), anyMap())).thenReturn("access-token");
        when(jwtService.generateRefreshToken("existing@gmail.com")).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(3600000L);

        LoginResponse response = googleOAuthService.authenticateGoogleUser(oauth2User);

        assertThat(response.getUid()).isEqualTo(existing.getUid().toString());
        assertThat(response.getUserName()).isEqualTo("existing");
        assertThat(response.getRole()).isEqualTo("CONTENT_ADMIN");
    }

    @Test
    void authenticateGoogleUser_rejectsSoftDeletedUser() {
        User deleted = user("deleted", "deleted@gmail.com", UserRole.CUSTOMER);
        deleted.setDeletedAt(LocalDateTime.now());

        when(userRepository.findByEmailIgnoreCase("deleted@gmail.com")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> googleOAuthService.authenticateGoogleUser(oauth2User("deleted@gmail.com", "Deleted User")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account has been deactivated");
    }

    @Test
    void authenticateGoogleUser_rejectsMissingEmail() {
        OAuth2User oauth2User = new DefaultOAuth2User(
                authorities(),
                Map.of("name", "No Email"),
                "name"
        );

        assertThatThrownBy(() -> googleOAuthService.authenticateGoogleUser(oauth2User))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google account email is required");
    }

    private OAuth2User oauth2User(String email, String name) {
        return new DefaultOAuth2User(
                authorities(),
                Map.of("email", email, "name", name),
                "email"
        );
    }

    private List<SimpleGrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private User user(String userName, String email, UserRole role) {
        return User.builder()
                .uid(UUID.randomUUID())
                .userName(userName)
                .email(email)
                .password("encoded-password")
                .role(role)
                .build();
    }
}
