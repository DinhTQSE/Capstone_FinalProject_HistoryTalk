package com.historytalk.service.school.token;

import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.AccountStatus;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.school.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyTokenAllowanceServiceImplTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @InjectMocks
    private DailyTokenAllowanceServiceImpl dailyTokenAllowanceService;

    private School activeSchool;
    private User activeStudent;

    @BeforeEach
    void setUp() {
        activeSchool = School.builder()
                .id(UUID.randomUUID())
                .name("THPT Chu Văn An")
                .schoolCode("CVA-HN")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .totalSchoolTokenQuota(5_000_000)
                .unallocatedTokenQuota(100_000)
                .defaultDailyStudentQuota(1_000)
                .subscriptionStartDate(LocalDate.now().minusMonths(1))
                .subscriptionEndDate(LocalDate.now().plusMonths(5))
                .status(SchoolStatus.ACTIVE)
                .build();

        activeStudent = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_hs_1001")
                .fullName("Trần Quốc Dinh")
                .role(UserRole.SCHOOL_STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .school(activeSchool)
                .token(400) // 400 unused tokens from yesterday
                .lastTokenResetAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    @Test
    @DisplayName("US-SP7-15: Lazy daily reset refunds unused yesterday tokens to school pool and allocates today's daily limit")
    void verifyAndPrepareDailyAllowance_FirstCallToday_RefundsAndAllocates() {
        when(classStudentRepository.findActiveClassroomsByStudentUid(activeStudent.getUid()))
                .thenReturn(Collections.emptyList());

        int available = dailyTokenAllowanceService.verifyAndPrepareDailyAllowance(activeStudent);

        assertEquals(1_000, available);
        assertEquals(1_000, activeStudent.getToken());
        // Pool was 100,000 + 400 refunded - 1,000 allocated = 99,400
        assertEquals(99_400, activeSchool.getUnallocatedTokenQuota());
        assertEquals(LocalDate.now(), activeStudent.getLastTokenResetAt().toLocalDate());
        verify(schoolRepository).save(activeSchool);
        verify(userRepository).save(activeStudent);
    }

    @Test
    @DisplayName("US-SP7-15 & US-SP7-16: Classroom daily quota override takes precedence over school default")
    void verifyAndPrepareDailyAllowance_ClassroomOverride() {
        Classroom grade12Class = Classroom.builder()
                .id(UUID.randomUUID())
                .className("12A1")
                .dailyTokenQuota(2_500)
                .status("ACTIVE")
                .build();
        when(classStudentRepository.findActiveClassroomsByStudentUid(activeStudent.getUid()))
                .thenReturn(List.of(grade12Class));

        int available = dailyTokenAllowanceService.verifyAndPrepareDailyAllowance(activeStudent);

        assertEquals(2_500, available);
        assertEquals(100_000 + 400 - 2_500, activeSchool.getUnallocatedTokenQuota());
    }

    @Test
    @DisplayName("US-SP7-15: Second call on same day does not reset allowance again")
    void verifyAndPrepareDailyAllowance_SameDay_NoReset() {
        activeStudent.setToken(650);
        activeStudent.setLastTokenResetAt(LocalDateTime.now());

        int available = dailyTokenAllowanceService.verifyAndPrepareDailyAllowance(activeStudent);

        assertEquals(650, available);
        verify(schoolRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-SP7-14: Out of tokens throws AI_QUOTA_EXCEEDED when daily limit and school pool are exhausted")
    void verifyAndPrepareDailyAllowance_Exhausted_ThrowsAiQuotaExceeded() {
        activeStudent.setToken(0);
        activeStudent.setLastTokenResetAt(LocalDateTime.now());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> dailyTokenAllowanceService.verifyAndPrepareDailyAllowance(activeStudent));

        assertTrue(ex.getMessage().contains("AI_QUOTA_EXCEEDED"));
    }

    @Test
    @DisplayName("US-SP7-14: Expired school subscription throws SCHOOL_SUBSCRIPTION_EXPIRED")
    void verifyAndPrepareDailyAllowance_ExpiredSchool_ThrowsSubscriptionExpired() {
        activeSchool.setSubscriptionEndDate(LocalDate.now().minusDays(1));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> dailyTokenAllowanceService.verifyAndPrepareDailyAllowance(activeStudent));

        assertTrue(ex.getMessage().contains("SCHOOL_SUBSCRIPTION_EXPIRED"));
        assertEquals(SchoolStatus.EXPIRED, activeSchool.getStatus());
        verify(schoolRepository).save(activeSchool);
    }

    @Test
    @DisplayName("US-SP7-18: Deactivated student account throws ACCOUNT_DEACTIVATED")
    void verifyAndPrepareDailyAllowance_DeactivatedStudent_ThrowsAccountDeactivated() {
        activeStudent.setAccountStatus(AccountStatus.DEACTIVATED);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> dailyTokenAllowanceService.verifyAndPrepareDailyAllowance(activeStudent));

        assertTrue(ex.getMessage().contains("ACCOUNT_DEACTIVATED"));
    }
}
