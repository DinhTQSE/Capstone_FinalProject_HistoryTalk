package com.historytalk.security;

import com.historytalk.entity.enums.AccountStatus;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolSubscriptionGuardInterceptorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @InjectMocks
    private SchoolSubscriptionGuardInterceptor interceptor;

    private UUID userId;
    private UUID schoolId;
    private School school;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        schoolId = UUID.randomUUID();

        school = School.builder()
                .id(schoolId)
                .name("Trường THPT Chu Văn An")
                .schoolCode("CVA")
                .status(SchoolStatus.ACTIVE)
                .subscriptionStartDate(LocalDate.now().minusMonths(1))
                .subscriptionEndDate(LocalDate.now().plusMonths(5))
                .build();

        user = User.builder()
                .uid(userId)
                .email("student@cva.edu.vn")
                .role(UserRole.SCHOOL_STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .school(school)
                .build();

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal("student@cva.edu.vn", userId.toString(), "SCHOOL_STUDENT");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preHandle_whenActiveSubscription_shouldAllow() {
        when(userRepository.findByIdWithSchool(userId)).thenReturn(Optional.of(user));

        boolean result = interceptor.preHandle(null, null, null);

        assertThat(result).isTrue();
        verify(userRepository).findByIdWithSchool(userId);
    }

    @Test
    void preHandle_whenSubscriptionExpired_shouldThrowAccessDeniedException() {
        school.setSubscriptionEndDate(LocalDate.now().minusDays(1));
        when(userRepository.findByIdWithSchool(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> interceptor.preHandle(null, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("SCHOOL_SUBSCRIPTION_EXPIRED");

        verify(schoolRepository).save(school);
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.EXPIRED);
    }

    @Test
    void preHandle_whenAccountDeactivated_shouldThrowAccessDeniedException() {
        user.setAccountStatus(AccountStatus.DEACTIVATED);
        when(userRepository.findByIdWithSchool(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> interceptor.preHandle(null, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ACCOUNT_DEACTIVATED");
    }

    @Test
    void preHandle_whenNoAuthentication_shouldAllow() {
        SecurityContextHolder.clearContext();

        boolean result = interceptor.preHandle(null, null, null);

        assertThat(result).isTrue();
        verifyNoInteractions(userRepository);
    }
}
