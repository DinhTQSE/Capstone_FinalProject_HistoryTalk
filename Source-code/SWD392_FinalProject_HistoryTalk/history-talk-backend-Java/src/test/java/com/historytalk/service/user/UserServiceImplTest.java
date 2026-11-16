package com.historytalk.service.user;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.user.AdminUpdateUserRequest;
import com.historytalk.dto.user.UpdateMyProfileRequest;
import com.historytalk.dto.user.UpdateUserRoleRequest;
import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.entity.enums.Gender;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.mapper.user.UserMapperImpl;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.school.token.DailyTokenAllowanceService;
import com.historytalk.entity.school.School;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserTierRepository userTierRepository;

    @Mock
    private DailyTokenAllowanceService dailyTokenAllowanceService;

    @Spy
    private UserMapperImpl userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void updateMyProfilePersistsAllowedProfileFields() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .uid(userId)
                .userName("old-name")
                .email("user@example.com")
                .role(UserRole.CUSTOMER)
                .token(10)
                .build();
        UpdateMyProfileRequest request = UpdateMyProfileRequest.builder()
                .userName("new-name")
                .fullName("Nguyen Van A")
                .dob(LocalDate.of(2000, 1, 2))
                .gender(Gender.MALE)
                .phoneNumber("0901234567")
                .address("Ho Chi Minh City")
                .avatarUrl("https://cdn.example.com/avatar.png")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUserNameIgnoreCase("new-name")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateMyProfile(userId.toString(), request);

        assertThat(response.getUserName()).isEqualTo("new-name");
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(response.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(response.getDob()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(response.getGender()).isEqualTo(Gender.MALE);
        assertThat(response.getPhoneNumber()).isEqualTo("0901234567");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getFullName()).isEqualTo("Nguyen Van A");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(userCaptor.getValue().getToken()).isEqualTo(10);
    }

    @Test
    void updateMyProfileRejectsDeactivatedUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .uid(userId)
                .userName("user")
                .email("user@example.com")
                .role(UserRole.CUSTOMER)
                .deletedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateMyProfile(userId.toString(), UpdateMyProfileRequest.builder().build()))
                .isInstanceOf(InvalidRequestException.class)
                ;
    }

    @Test
    void updateUserRoleChangesOnlyRole() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .uid(userId)
                .userName("content")
                .email("content@example.com")
                .role(UserRole.CUSTOMER)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateUserRole(
                userId.toString(),
                UpdateUserRoleRequest.builder().role(UserRole.CONTENT_ADMIN).build()
        );

        assertThat(response.getRole()).isEqualTo(UserRole.CONTENT_ADMIN);
        verify(userRepository).save(user);
    }

    @Test
    void listUsersReturnsPaginatedProfiles() {
        User user = User.builder()
                .uid(UUID.randomUUID())
                .userName("customer")
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .fullName("Customer Name")
                .build();
        PageImpl<User> page = new PageImpl<>(List.of(user));

        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        PaginatedResponse<UserProfileResponse> response = userService.listUsers(1, 10);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getFullName()).isEqualTo("Customer Name");
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getCurrentPage()).isEqualTo(1);
    }

    @Test
    void adminUpdateUserRejectsMissingUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.adminUpdateUser(userId.toString(), AdminUpdateUserRequest.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class)
                ;
    }

    @Test
    void dailyTopUpAfterUpgradeDayCountsOnlyTheNewPaidPlan() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .uid(userId)
                .userName("customer")
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .token(13_000)
                .lastTokenResetAt(LocalDateTime.now().minusDays(1))
                .build();
        Tier pro = Tier.builder().tierId(UUID.randomUUID()).title("pro").amount(99_000)
                .noMonth(1).limitedToken(10_000).build();
        UserTier proSub = UserTier.builder().id(UUID.randomUUID()).user(user).tier(pro)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(29))
                .isActive(true).build();
        // the old Plus plan was deactivated by fulfillment, so only Pro is still active
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userTierRepository.findCurrentActiveByUid(any(), any())).thenReturn(Optional.of(proSub));
        when(userTierRepository.findAllActiveByUid(any(), any())).thenReturn(List.of(proSub));

        userService.getMyProfile(userId.toString());

        assertThat(user.getToken()).isEqualTo(23_000);   // +10,000, not +15,000
    }

    @Test
    void schoolStudentProfileUsesDailyAllowanceInsteadOfTierTopUp() {
        UUID userId = UUID.randomUUID();
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        User student = User.builder()
                .uid(userId)
                .userName("hs")
                .email("hs@example.com")
                .role(UserRole.SCHOOL_STUDENT)
                .school(School.builder().id(UUID.randomUUID()).build())
                .lastTokenResetAt(yesterday)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));

        userService.getMyProfile(userId.toString());

        // The profile read must not mark the day as reset on its own, or yesterday's bonus survives.
        verify(dailyTokenAllowanceService).refreshDailyAllowance(student);
        verify(userTierRepository, never()).findAllActiveByUid(any(), any());
        assertThat(student.getLastTokenResetAt()).isEqualTo(yesterday);
    }
}
