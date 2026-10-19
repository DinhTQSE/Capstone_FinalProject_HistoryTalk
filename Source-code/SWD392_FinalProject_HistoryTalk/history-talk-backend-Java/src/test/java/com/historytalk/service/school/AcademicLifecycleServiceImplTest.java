package com.historytalk.service.school;

import com.historytalk.dto.school.AcademicYearRolloverRequest;
import com.historytalk.dto.school.AcademicYearRolloverResponse;
import com.historytalk.dto.school.DeactivateStudentRequest;
import com.historytalk.dto.school.RenewSchoolSubscriptionRequest;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.dto.school.TopUpSchoolTokensRequest;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.AccountStatus;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicLifecycleServiceImplTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AcademicLifecycleServiceImpl academicLifecycleService;

    private School school;
    private User schoolAdmin;
    private UserPrincipal schoolAdminPrincipal;

    @BeforeEach
    void setUp() {
        school = School.builder()
                .id(UUID.randomUUID())
                .name("THPT Chuyên Lê Hồng Phong")
                .schoolCode("LHP-HCM")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .totalSchoolTokenQuota(5_000_000)
                .unallocatedTokenQuota(1_000_000)
                .defaultDailyStudentQuota(1_000)
                .subscriptionStartDate(LocalDate.of(2025, 9, 1))
                .subscriptionEndDate(LocalDate.of(2026, 5, 31))
                .status(SchoolStatus.ACTIVE)
                .build();

        schoolAdmin = User.builder()
                .uid(UUID.randomUUID())
                .userName("lhp_admin")
                .email("admin@lhp.edu.vn")
                .role(UserRole.SCHOOL_ADMIN)
                .school(school)
                .build();

        schoolAdminPrincipal = new UserPrincipal(schoolAdmin);
    }

    @Test
    @DisplayName("US-SP7-14: Renewing subscription in May succeeds and sets renewedForNextYear=true")
    void renewSchoolSubscription_InMay_Success() {
        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(schoolRepository.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));

        RenewSchoolSubscriptionRequest request = RenewSchoolSubscriptionRequest.builder()
                .packageType(EnterprisePackage.ENTERPRISE_MEDIUM)
                .referenceDate(LocalDate.of(2026, 5, 15))
                .build();

        SchoolResponse response = academicLifecycleService.renewSchoolSubscription(school.getId(), request);

        assertTrue(response.getRenewedForNextYear());
        assertEquals(EnterprisePackage.ENTERPRISE_MEDIUM, response.getNextPackageType());
    }

    @Test
    @DisplayName("US-SP7-14: Renewing subscription outside May is rejected")
    void renewSchoolSubscription_OutsideMay_ThrowsInvalidRequest() {
        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));

        RenewSchoolSubscriptionRequest request = RenewSchoolSubscriptionRequest.builder()
                .packageType(EnterprisePackage.ENTERPRISE_MEDIUM)
                .referenceDate(LocalDate.of(2026, 4, 20))
                .build();

        assertThrows(InvalidRequestException.class,
                () -> academicLifecycleService.renewSchoolSubscription(school.getId(), request));
    }

    @Test
    @DisplayName("US-SP7-14: Activating renewed subscription on Sep 1 rolls over 20% of unused pool and sets 01/09 - 31/05 cycle")
    void activateRenewedSubscription_Sep1_SuccessWith20PercentRollover() {
        school.setRenewedForNextYear(true);
        school.setNextPackageType(EnterprisePackage.ENTERPRISE_SMALL); // 5,000,000
        school.setUnallocatedTokenQuota(1_000_000); // 20% rollover = 200,000

        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(userRepository.findBySchoolIdAndRoleAndDeletedAtIsNull(school.getId(), UserRole.SCHOOL_STUDENT))
                .thenReturn(Collections.emptyList());
        when(schoolRepository.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));

        SchoolResponse response = academicLifecycleService.activateRenewedSubscription(
                school.getId(), LocalDate.of(2026, 9, 1));

        assertEquals(5_200_000, response.getTotalSchoolTokenQuota());
        assertEquals(5_200_000, response.getUnallocatedTokenQuota());
        assertEquals(LocalDate.of(2026, 9, 1), response.getSubscriptionStartDate());
        assertEquals(LocalDate.of(2027, 5, 31), response.getSubscriptionEndDate());
        assertFalse(response.getRenewedForNextYear());
        assertEquals(SchoolStatus.ACTIVE, response.getStatus());
    }

    @Test
    @DisplayName("US-SP7-14: Mid-year top-up adds tokens to pool without changing subscriptionEndDate")
    void topUpSchoolTokens_Success() {
        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(schoolRepository.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));

        TopUpSchoolTokensRequest request = TopUpSchoolTokensRequest.builder()
                .additionalTokens(500_000)
                .note("Mua thêm token học kỳ 2")
                .build();

        SchoolResponse response = academicLifecycleService.topUpSchoolTokens(school.getId(), request);

        assertEquals(5_500_000, response.getTotalSchoolTokenQuota());
        assertEquals(1_500_000, response.getUnallocatedTokenQuota());
        assertEquals(LocalDate.of(2026, 5, 31), response.getSubscriptionEndDate());
    }

    @Test
    @DisplayName("US-SP7-18: Academic year rollover archives old classes, reclaims tokens, and deactivates Grade 12 graduates")
    void rolloverAcademicYear_ArchivesAndDeactivatesGraduates() {
        Classroom grade10 = Classroom.builder()
                .id(UUID.randomUUID())
                .className("10A1")
                .gradeLevel(10)
                .academicYear(2025)
                .status("ACTIVE")
                .build();

        Classroom grade12 = Classroom.builder()
                .id(UUID.randomUUID())
                .className("12A1")
                .gradeLevel(12)
                .academicYear(2025)
                .status("ACTIVE")
                .build();

        User gradStudent = User.builder()
                .uid(UUID.randomUUID())
                .userName("lhp_hs_1201")
                .role(UserRole.SCHOOL_STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .school(school)
                .token(500)
                .build();

        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(classroomRepository.findBySchoolIdAndAcademicYearAndDeletedAtIsNull(school.getId(), 2025))
                .thenReturn(List.of(grade10, grade12));
        when(classStudentRepository.findEnrolledStudentUids(grade12.getId()))
                .thenReturn(List.of(gradStudent.getUid()));
        when(userRepository.findBySchoolIdAndRoleAndDeletedAtIsNull(school.getId(), UserRole.SCHOOL_STUDENT))
                .thenReturn(List.of(gradStudent));
        when(userRepository.findById(gradStudent.getUid())).thenReturn(Optional.of(gradStudent));
        when(classStudentRepository.findByStudentUidAndStatus(gradStudent.getUid(), "ACTIVE"))
                .thenReturn(Collections.emptyList());

        AcademicYearRolloverRequest request = AcademicYearRolloverRequest.builder()
                .completedAcademicYear(2025)
                .deactivateGraduatedGrade12(true)
                .build();

        AcademicYearRolloverResponse response = academicLifecycleService.rolloverAcademicYear(
                schoolAdminPrincipal, request);

        assertEquals(2, response.getArchivedClassroomsCount());
        assertEquals("ARCHIVED", grade10.getStatus());
        assertEquals("ARCHIVED", grade12.getStatus());
        assertEquals(500L, response.getReclaimedTokensToPool());
        assertEquals(1, response.getDeactivatedGraduatedStudentsCount());
        assertEquals(AccountStatus.DEACTIVATED, gradStudent.getAccountStatus());
        assertNotNull(gradStudent.getDeletedAt());
        assertEquals(1_000_500, response.getRemainingSchoolTokenPool());
    }

    @Test
    @DisplayName("US-SP7-18: Deactivating transferred student reclaims tokens and blocks login")
    void deactivateStudentAccount_Success() {
        User student = User.builder()
                .uid(UUID.randomUUID())
                .userName("lhp_hs_1005")
                .studentCode("HS1005")
                .fullName("Lê Hoàng Nam")
                .role(UserRole.SCHOOL_STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .school(school)
                .token(800)
                .build();

        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(userRepository.findById(student.getUid())).thenReturn(Optional.of(student));
        when(classStudentRepository.findByStudentUidAndStatus(student.getUid(), "ACTIVE"))
                .thenReturn(Collections.emptyList());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        DeactivateStudentRequest request = DeactivateStudentRequest.builder()
                .reason("TRANSFERRED_SCHOOL")
                .build();

        StudentResponse response = academicLifecycleService.deactivateStudentAccount(
                schoolAdminPrincipal, student.getUid(), request);

        assertFalse(response.getActive());
        assertEquals(0, response.getToken());
        assertEquals(AccountStatus.DEACTIVATED, student.getAccountStatus());
        assertEquals("TRANSFERRED_SCHOOL", student.getDeactivatedReason());
        assertNotNull(student.getDeletedAt());
        assertEquals(1_000_800, school.getUnallocatedTokenQuota());
    }
}
