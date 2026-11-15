package com.historytalk.security;

import com.historytalk.entity.enums.AccountStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    private JwtTokenProvider tokenProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", 900000L);
        filter = new JwtAuthenticationFilter(tokenProvider, userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesActiveUserUsingDbRole() throws Exception {
        UUID uid = UUID.randomUUID();
        User user = User.builder()
                .uid(uid)
                .email("teacher@school.vn")
                .role(UserRole.TEACHER)
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(false)
                .build();
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));

        // Token claims stale role SYSTEM_ADMIN, DB has TEACHER
        String jwt = tokenProvider.generateAccessToken(
                "teacher@school.vn",
                Map.of("uid", uid.toString(), "role", "SYSTEM_ADMIN")
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/Historical-tell/api/v1/teacher/classrooms");
        req.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_TEACHER");
    }

    @Test
    void rejectsTokenWhenUserIsDeactivated() throws Exception {
        UUID uid = UUID.randomUUID();
        User user = User.builder()
                .uid(uid)
                .email("locked@school.vn")
                .role(UserRole.SCHOOL_STUDENT)
                .accountStatus(AccountStatus.DEACTIVATED)
                .build();
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));

        String jwt = tokenProvider.generateAccessToken(
                "locked@school.vn",
                Map.of("uid", uid.toString(), "role", "SCHOOL_STUDENT")
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/Historical-tell/api/v1/chat/sessions");
        req.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsTokenIssuedBeforePasswordChangedAt() throws Exception {
        UUID uid = UUID.randomUUID();
        String jwt = tokenProvider.generateAccessToken(
                "user@example.com",
                Map.of("uid", uid.toString(), "role", "CUSTOMER")
        );

        User user = User.builder()
                .uid(uid)
                .email("user@example.com")
                .role(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .passwordChangedAt(LocalDateTime.now().plusSeconds(5))
                .build();
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/Historical-tell/api/v1/users/me");
        req.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void blocksBusinessEndpointWith403WhenMustChangePasswordIsTrue() throws Exception {
        UUID uid = UUID.randomUUID();
        User user = User.builder()
                .uid(uid)
                .email("student@saas.historytalk.vn")
                .role(UserRole.SCHOOL_STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .build();
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));

        String jwt = tokenProvider.generateAccessToken(
                "student@saas.historytalk.vn",
                Map.of("uid", uid.toString(), "role", "SCHOOL_STUDENT")
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/Historical-tell/api/v1/chat/sessions");
        req.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void allowsChangePasswordEndpointWhenMustChangePasswordIsTrue() throws Exception {
        UUID uid = UUID.randomUUID();
        User user = User.builder()
                .uid(uid)
                .email("student@saas.historytalk.vn")
                .role(UserRole.SCHOOL_STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .build();
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));

        String jwt = tokenProvider.generateAccessToken(
                "student@saas.historytalk.vn",
                Map.of("uid", uid.toString(), "role", "SCHOOL_STUDENT")
        );

        MockHttpServletRequest req = new MockHttpServletRequest("PATCH", "/Historical-tell/api/v1/users/me/password");
        req.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
