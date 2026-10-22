package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.*;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.*;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.dashboard.DashboardTopWrongQuestionProjection;
import com.historytalk.repository.dashboard.DashboardUserTopCharacterProjection;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsDashboardServiceImplTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizSessionRepository quizSessionRepository;

    @Mock
    private QuizAnswerDetailRepository quizAnswerDetailRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private UserTierRepository userTierRepository;

    @Mock
    private com.historytalk.repository.school.TokenBoostRequestRepository tokenBoostRequestRepository;

    @Mock
    private com.historytalk.repository.assignment.AssignmentSubmissionRepository assignmentSubmissionRepository;

    @InjectMocks
    private AnalyticsDashboardServiceImpl dashboardService;

    private UUID schoolId;
    private School school;
    private User teacherUser;
    private User studentUser;
    private User customerUser;
    private UserPrincipal teacherPrincipal;
    private UserPrincipal studentPrincipal;
    private UserPrincipal customerPrincipal;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        school = School.builder()
                .id(schoolId)
                .name("THPT Chu Văn An")
                .schoolCode("CVA")
                .packageType(com.historytalk.entity.enums.EnterprisePackage.ENTERPRISE_SMALL)
                .status(com.historytalk.entity.enums.SchoolStatus.ACTIVE)
                .subscriptionStartDate(java.time.LocalDate.of(2025, 9, 1))
                .subscriptionEndDate(java.time.LocalDate.of(2026, 5, 31))
                .renewedForNextYear(true)
                .nextPackageType(com.historytalk.entity.enums.EnterprisePackage.ENTERPRISE_MEDIUM)
                .totalSchoolTokenQuota(100000)
                .unallocatedTokenQuota(40000)
                .defaultDailyStudentQuota(1000)
                .build();

        teacherUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_gv_01")
                .fullName("Nguyễn Thị Mai")
                .role(UserRole.TEACHER)
                .school(school)
                .build();

        studentUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_hs_01")
                .fullName("Trần Quốc Dinh")
                .studentCode("HS1001")
                .role(UserRole.SCHOOL_STUDENT)
                .school(school)
                .dailyTokensUsed(300)
                .dailyBonusTokens(500)
                .lastTokenResetAt(LocalDateTime.now())
                .token(1200)
                .build();

        customerUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("customer_b2c")
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .token(1500)
                .build();

        teacherPrincipal = new UserPrincipal(teacherUser);
        studentPrincipal = new UserPrincipal(studentUser);
        customerPrincipal = new UserPrincipal(customerUser);
    }

    @Test
    @DisplayName("US-SP6-06: getSchoolAdminDashboard returns accurate school statistics, subscription cycle, and shared token pool")
    void getSchoolAdminDashboard_Success() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.countBySchoolIdAndDeletedAtIsNull(schoolId)).thenReturn(12L);
        when(userRepository.countBySchoolIdAndRole(schoolId, UserRole.TEACHER)).thenReturn(25L);
        when(userRepository.countBySchoolIdAndRole(schoolId, UserRole.SCHOOL_STUDENT)).thenReturn(450L);
        when(messageRepository.sumTokensUsedBySchoolStudents(schoolId)).thenReturn(15000L);
        when(messageRepository.sumTokensUsedBySchoolTeachers(schoolId)).thenReturn(5000L);
        when(classroomRepository.findTopClassroomsByTokenUsage(eq(schoolId), any(LocalDateTime.class), eq(5)))
                .thenReturn(Collections.emptyList());
        when(tokenBoostRequestRepository.countBySchool_IdAndStatus(
                schoolId, com.historytalk.entity.enums.TokenBoostRequestStatus.PENDING)).thenReturn(3L);

        com.historytalk.repository.dashboard.AssignmentSubmissionStatsProjection stats =
                mock(com.historytalk.repository.dashboard.AssignmentSubmissionStatsProjection.class);
        when(stats.getTotalSubmissions()).thenReturn(120L);
        when(stats.getSubmittedCount()).thenReturn(90L);
        when(stats.getLateSubmittedCount()).thenReturn(18L);
        when(stats.getNotStartedCount()).thenReturn(12L);
        when(assignmentSubmissionRepository.getSubmissionStatsBySchoolId(schoolId)).thenReturn(stats);

        SchoolAdminDashboardResponse response = dashboardService.getSchoolAdminDashboard(schoolId);

        assertNotNull(response);
        assertEquals(schoolId, response.getSchoolId());
        assertEquals("THPT Chu Văn An", response.getSchoolName());
        assertEquals("CVA", response.getSchoolCode());
        assertEquals(com.historytalk.entity.enums.EnterprisePackage.ENTERPRISE_SMALL, response.getPackageType());
        assertEquals(350, response.getRecommendedStudents());
        assertEquals(com.historytalk.entity.enums.SchoolStatus.ACTIVE, response.getStatus());
        assertEquals(java.time.LocalDate.of(2025, 9, 1), response.getSubscriptionStartDate());
        assertEquals(java.time.LocalDate.of(2026, 5, 31), response.getSubscriptionEndDate());
        assertTrue(response.getRenewedForNextYear());
        assertEquals(12L, response.getTotalClassrooms());
        assertEquals(25L, response.getTotalTeachers());
        assertEquals(450L, response.getTotalStudents());
        assertEquals(100000, response.getTotalSchoolTokenQuota());
        assertEquals(40000, response.getUnallocatedTokenQuota());
        assertEquals(60000, response.getAllocatedTokens());
        assertEquals(15000L, response.getTokensUsedByStudents());
        assertEquals(5000L, response.getTokensUsedByTeachers());
        assertEquals(20000L, response.getTokensUsedTotal());
        assertEquals(75.0, response.getStudentTokenUsagePercentage());
        assertEquals(25.0, response.getTeacherTokenUsagePercentage());
        assertEquals(1000, response.getDefaultDailyStudentQuota());
        assertEquals(3L, response.getPendingTokenBoostRequests());
        assertNotNull(response.getAssignmentStats());
        assertEquals(120L, response.getAssignmentStats().getTotalSubmissions());
        assertEquals(75.0, response.getAssignmentStats().getCompletedRate());
        assertEquals(15.0, response.getAssignmentStats().getLateSubmittedRate());
        assertEquals(10.0, response.getAssignmentStats().getNotStartedRate());
    }

    @Test
    @DisplayName("getClassroomsTokenUsage: Lấy danh sách thống kê token theo lớp thành công")
    void getClassroomsTokenUsage_Success() {
        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(messageRepository.sumTokensUsedBySchoolStudents(schoolId)).thenReturn(15000L);
        when(messageRepository.sumTokensUsedBySchoolTeachers(schoolId)).thenReturn(5000L);

        com.historytalk.repository.dashboard.ClassroomTokenUsageProjection proj = mock(com.historytalk.repository.dashboard.ClassroomTokenUsageProjection.class);
        UUID cId = UUID.randomUUID();
        when(proj.getClassroomId()).thenReturn(cId);
        when(proj.getClassName()).thenReturn("10A1");
        when(proj.getClassCode()).thenReturn("CVA-10A1-2026");
        when(proj.getGradeLevel()).thenReturn(10);
        when(proj.getAcademicYear()).thenReturn(2026);
        when(proj.getStatus()).thenReturn("ACTIVE");
        when(proj.getTeacherName()).thenReturn("Thầy A");
        when(proj.getStudentCount()).thenReturn(35L);
        when(proj.getTokensUsedTotal()).thenReturn(10000L);
        when(proj.getTokensUsedToday()).thenReturn(500L);

        org.springframework.data.domain.Page<com.historytalk.repository.dashboard.ClassroomTokenUsageProjection> page =
                new org.springframework.data.domain.PageImpl<>(List.of(proj), org.springframework.data.domain.PageRequest.of(0, 10), 1);

        when(classroomRepository.findClassroomTokenUsageList(
                eq(schoolId), any(LocalDateTime.class), any(), any(), any(), any(), any()))
                .thenReturn(page);

        com.historytalk.dto.PaginatedResponse<ClassroomTokenUsageSummaryDto> response =
                dashboardService.getClassroomsTokenUsage(schoolId, 2026, 10, "ACTIVE", null, org.springframework.data.domain.PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(1L, response.getTotalElements());
        assertEquals("10A1", response.getContent().get(0).getClassName());
        assertEquals(10000L, response.getContent().get(0).getTokensUsedTotal());
        assertEquals(50.0, response.getContent().get(0).getPercentageOfSchoolUsage());

        org.mockito.ArgumentCaptor<Pageable> classPageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(classroomRepository).findClassroomTokenUsageList(
                eq(schoolId), any(LocalDateTime.class), eq(2026), eq(10), eq("ACTIVE"), any(), classPageableCaptor.capture());
        assertFalse(classPageableCaptor.getValue().getSort().isSorted());
    }

    @Test
    @DisplayName("getClassroomsTokenUsage: When schoolId is null, resolves from security context and succeeds without throwing null id")
    void getClassroomsTokenUsage_NullSchoolId_ResolvesFromSecurityContext() {
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        com.historytalk.security.AuthenticatedPrincipal ap = new com.historytalk.security.AuthenticatedPrincipal("admin@school.com", teacherUser.getUid().toString(), "SCHOOL_ADMIN");
        when(auth.getPrincipal()).thenReturn(ap);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findById(teacherUser.getUid())).thenReturn(Optional.of(teacherUser));
        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(messageRepository.sumTokensUsedBySchoolStudents(schoolId)).thenReturn(1000L);
        when(messageRepository.sumTokensUsedBySchoolTeachers(schoolId)).thenReturn(500L);

        when(classroomRepository.findClassroomTokenUsageList(eq(schoolId), any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(Collections.emptyList()));

        com.historytalk.dto.PaginatedResponse<ClassroomTokenUsageSummaryDto> res =
                dashboardService.getClassroomsTokenUsage(null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10));

        assertNotNull(res);
        verify(schoolRepository).existsById(schoolId);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getClassroomStudentsTokenUsage: Lấy chi tiết token học sinh trong lớp thành công")
    void getClassroomStudentsTokenUsage_Success() {
        Classroom classroom = Classroom.builder()
                .id(UUID.randomUUID())
                .school(school)
                .teacher(teacherUser)
                .dailyTokenQuota(2000)
                .build();
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroom.getId(), schoolId))
                .thenReturn(Optional.of(classroom));

        com.historytalk.repository.dashboard.StudentTokenUsageDetailProjection sproj = mock(com.historytalk.repository.dashboard.StudentTokenUsageDetailProjection.class);
        UUID sId = UUID.randomUUID();
        when(sproj.getStudentId()).thenReturn(sId);
        when(sproj.getStudentCode()).thenReturn("HS001");
        when(sproj.getFullName()).thenReturn("Nguyễn Văn An");
        when(sproj.getTokensUsedTotal()).thenReturn(5000L);
        when(sproj.getTokensUsedToday()).thenReturn(300L);
        when(sproj.getDailyBonusTokens()).thenReturn(200);
        when(sproj.getMessageCount()).thenReturn(12L);

        org.springframework.data.domain.Page<com.historytalk.repository.dashboard.StudentTokenUsageDetailProjection> page =
                new org.springframework.data.domain.PageImpl<>(List.of(sproj), org.springframework.data.domain.PageRequest.of(0, 20), 1);

        when(classStudentRepository.findStudentTokenUsageInClassroom(
                eq(classroom.getId()), any(LocalDateTime.class), any(), any()))
                .thenReturn(page);

        com.historytalk.dto.PaginatedResponse<StudentTokenUsageDetailDto> response =
                dashboardService.getClassroomStudentsTokenUsage(classroom.getId(), null,
                        org.springframework.data.domain.PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "tokensUsedTotal")),
                        teacherPrincipal);

        assertNotNull(response);
        assertEquals(1L, response.getTotalElements());
        StudentTokenUsageDetailDto dto = response.getContent().get(0);
        assertEquals("HS001", dto.getStudentCode());
        assertEquals(5000L, dto.getTokensUsedTotal());
        assertEquals(300L, dto.getTokensUsedToday());
        assertEquals(2000L, dto.getDailyTokenLimit());
        assertEquals(200L, dto.getDailyBonusTokens());
        // Remaining = min((2000 + 200 - 300), unallocated 40000) = 1900
        assertEquals(1900L, dto.getEffectiveRemainingToday());

        org.mockito.ArgumentCaptor<Pageable> studentPageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(classStudentRepository).findStudentTokenUsageInClassroom(eq(classroom.getId()), any(LocalDateTime.class), any(), studentPageableCaptor.capture());
        assertFalse(studentPageableCaptor.getValue().getSort().isSorted());
    }

    @Test
    @DisplayName("getTeacherClassesOverview: Lấy tổng quan các lớp của giáo viên thành công")
    void getTeacherClassesOverview_Success() {
        when(userRepository.findById(teacherUser.getUid())).thenReturn(Optional.of(teacherUser));

        com.historytalk.repository.dashboard.ClassroomTokenUsageProjection proj = mock(com.historytalk.repository.dashboard.ClassroomTokenUsageProjection.class);
        when(proj.getClassroomId()).thenReturn(UUID.randomUUID());
        when(proj.getClassName()).thenReturn("10A1");
        when(proj.getStudentCount()).thenReturn(30L);
        when(proj.getTokensUsedTotal()).thenReturn(8000L);

        when(classroomRepository.findClassroomsTokenUsageForTeacher(eq(schoolId), eq(teacherUser.getUid()), any(LocalDateTime.class)))
                .thenReturn(List.of(proj));

        TeacherClassesOverviewResponse response = dashboardService.getTeacherClassesOverview(teacherPrincipal);

        assertNotNull(response);
        assertEquals(teacherUser.getUid(), response.getTeacherUid());
        assertEquals(1, response.getTotalClassroomsManaged());
        assertEquals(30L, response.getTotalStudentsManaged());
        assertEquals(8000L, response.getTotalTokensUsedByAllClasses());
        assertEquals("10A1", response.getClassrooms().get(0).getClassName());
    }

    @Test
    @DisplayName("US-SP6-06: getSchoolAdminDashboard throws ResourceNotFoundException when school does not exist")
    void getSchoolAdminDashboard_Throws_WhenSchoolNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(schoolRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                dashboardService.getSchoolAdminDashboard(unknownId));
    }

    @Test
    @DisplayName("US-SP6-07: getTeacherClassroomDashboard returns classroom metrics, daily token limit, and student analytics")
    void getTeacherClassroomDashboard_Success() {
        UUID classId = UUID.randomUUID();
        Classroom classroom = Classroom.builder()
                .id(classId)
                .school(school)
                .teacher(teacherUser)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .gradeLevel(10)
                .academicYear(2026)
                .status("ACTIVE")
                .dailyTokenQuota(1500)
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classId, schoolId))
                .thenReturn(Optional.of(classroom));
        when(tokenBoostRequestRepository.countByClassroom_IdAndStatus(
                classId, com.historytalk.entity.enums.TokenBoostRequestStatus.PENDING)).thenReturn(2L);
        when(classStudentRepository.findEnrolledStudentUids(classId))
                .thenReturn(List.of(studentUser.getUid()));
        when(userRepository.findAllById(List.of(studentUser.getUid())))
                .thenReturn(List.of(studentUser));
        when(quizSessionRepository.countByUser_UidAndEndTimeIsNotNullAndDeletedAtIsNull(studentUser.getUid()))
                .thenReturn(5L);
        when(quizSessionRepository.averageScorePercentageByUser(studentUser.getUid()))
                .thenReturn(85.5);
        when(messageRepository.countMessagesByUser(studentUser.getUid()))
                .thenReturn(42L);
        when(messageRepository.countMessagesByStudentUids(List.of(studentUser.getUid())))
                .thenReturn(42L);

        TeacherClassroomDashboardResponse response = dashboardService.getTeacherClassroomDashboard(classId, teacherPrincipal);

        assertNotNull(response);
        assertEquals(classId, response.getClassId());
        assertEquals("10A1", response.getClassName());
        assertEquals("CVA-10A1-2026", response.getClassCode());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(1500, response.getDailyTokenLimit());
        assertEquals(2L, response.getPendingTokenBoostRequests());
        assertEquals(1L, response.getStudentCount());
        assertEquals(42L, response.getTotalAiMessages());
        assertEquals(42.0, response.getAvgAiMessagesPerStudent());
        assertEquals(1, response.getStudents().size());

        TeacherClassroomDashboardResponse.StudentAnalyticsItem studentItem = response.getStudents().get(0);
        assertEquals(studentUser.getUid(), studentItem.getStudentId());
        assertEquals("Trần Quốc Dinh", studentItem.getFullName());
        assertEquals("HS1001", studentItem.getStudentCode());
        assertEquals(1500, studentItem.getDailyTokenLimit());
        assertEquals(500, studentItem.getDailyBonusTokens());
        assertEquals(300, studentItem.getDailyTokensUsed());
        assertEquals(1700, studentItem.getCurrentTokens());
        assertEquals(5L, studentItem.getQuizzesAttempted());
        assertEquals(85.5, studentItem.getAverageScorePercentage());
        assertEquals(42L, studentItem.getAiMessagesCount());
    }

    @Test
    @DisplayName("US-SP6-07: getTeacherClassroomDashboard throws AccessDeniedException when teacher does not own class")
    void getTeacherClassroomDashboard_Throws_WhenTeacherDoesNotOwnClass() {
        UUID classId = UUID.randomUUID();
        User otherTeacher = User.builder().uid(UUID.randomUUID()).school(school).build();
        Classroom classroom = Classroom.builder()
                .id(classId)
                .school(school)
                .teacher(otherTeacher)
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classId, schoolId))
                .thenReturn(Optional.of(classroom));

        assertThrows(AccessDeniedException.class, () ->
                dashboardService.getTeacherClassroomDashboard(classId, teacherPrincipal));
    }

    @Test
    @DisplayName("US-SP6-08: getStudentDashboard returns student daily token allowance, quiz progress, and enrolled classrooms")
    void getStudentDashboard_Success() {
        UUID studentUid = studentUser.getUid();
        when(userRepository.findById(studentUid)).thenReturn(Optional.of(studentUser));
        when(messageRepository.sumTokensUsedByUserSince(eq(studentUid), any())).thenReturn(300L);
        when(quizSessionRepository.countByUser_UidAndEndTimeIsNotNullAndDeletedAtIsNull(studentUid)).thenReturn(8L);
        when(quizSessionRepository.averageScorePercentageByUser(studentUid)).thenReturn(90.0);

        Quiz quiz = Quiz.builder().title("Chiến dịch Điện Biên Phủ").build();
        QuizSession session = QuizSession.builder()
                .sessionId(UUID.randomUUID())
                .quiz(quiz)
                .score(9.0f)
                .endTime(LocalDateTime.now())
                .build();

        when(quizSessionRepository.findRecentCompletedByUser(eq(studentUid), any(Pageable.class)))
                .thenReturn(List.of(session));

        Classroom classroom = Classroom.builder()
                .id(UUID.randomUUID())
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .academicYear(2026)
                .status("ACTIVE")
                .dailyTokenQuota(1000)
                .teacher(teacherUser)
                .build();
        when(classStudentRepository.findClassroomsByStudentUid(studentUid)).thenReturn(List.of(classroom));

        StudentPersonalDashboardResponse response = dashboardService.getStudentDashboard(studentPrincipal);

        assertNotNull(response);
        assertEquals(studentUid, response.getStudentId());
        assertEquals("Trần Quốc Dinh", response.getStudentName());
        assertEquals("HS1001", response.getStudentCode());
        assertEquals("THPT Chu Văn An", response.getSchoolName());
        assertEquals(1000, response.getDailyTokenLimit());
        assertEquals(500, response.getDailyBonusTokens());
        assertEquals(1200, response.getCurrentTokens());
        assertEquals(300L, response.getTokensUsedToday());
        assertEquals(8L, response.getTotalQuizzesAttempted());
        assertEquals(90.0, response.getAverageScorePercentage());
        assertEquals(1, response.getRecentQuizzes().size());
        assertEquals("Chiến dịch Điện Biên Phủ", response.getRecentQuizzes().get(0).getQuizTitle());
        assertEquals(1, response.getEnrolledClassrooms().size());
        assertEquals("10A1", response.getEnrolledClassrooms().get(0).getClassName());
        assertEquals(2026, response.getEnrolledClassrooms().get(0).getAcademicYear());
        assertEquals("ACTIVE", response.getEnrolledClassrooms().get(0).getStatus());
        assertEquals("Nguyễn Thị Mai", response.getEnrolledClassrooms().get(0).getTeacherName());
    }

    @Test
    @DisplayName("US-SP6-09: getCustomerDashboard returns B2C active tier, era distribution, and payment history")
    void getCustomerDashboard_Success() {
        UUID userUid = customerUser.getUid();
        when(userRepository.findById(userUid)).thenReturn(Optional.of(customerUser));

        Tier tier = Tier.builder().title("PLUS").build();
        UserTier userTier = UserTier.builder()
                .tier(tier)
                .endTime(LocalDateTime.now().plusMonths(1))
                .build();
        when(userTierRepository.findCurrentActiveByUid(eq(userUid), any())).thenReturn(Optional.of(userTier));

        when(quizSessionRepository.countByUser_UidAndEndTimeIsNotNullAndDeletedAtIsNull(userUid)).thenReturn(12L);
        when(quizSessionRepository.averageScorePercentageByUser(userUid)).thenReturn(78.4);
        when(quizSessionRepository.findRecentCompletedByUser(eq(userUid), any(Pageable.class))).thenReturn(Collections.emptyList());

        List<Object[]> eraResults = new ArrayList<>();
        eraResults.add(new Object[]{"MODERN", 7L});
        eraResults.add(new Object[]{"MEDIEVAL", 5L});
        when(quizSessionRepository.countEraDistributionByUser(userUid)).thenReturn(eraResults);

        when(messageRepository.countMessagesByUser(userUid)).thenReturn(105L);

        DashboardUserTopCharacterProjection topChar = mock(DashboardUserTopCharacterProjection.class);
        when(topChar.getCharacterId()).thenReturn(UUID.randomUUID().toString());
        when(topChar.getName()).thenReturn("Trần Hưng Đạo");
        when(topChar.getMessageCount()).thenReturn(50L);
        when(messageRepository.findTopCharactersForUser(userUid, 5)).thenReturn(List.of(topChar));

        PaymentOrder order = PaymentOrder.builder()
                .orderCode(123456L)
                .amount(99000)
                .tier(tier)
                .status(PaymentOrderStatus.PAID)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentOrderRepository.findByUser_UidOrderByCreatedAtDesc(userUid)).thenReturn(List.of(order));

        CustomerPersonalDashboardResponse response = dashboardService.getCustomerDashboard(customerPrincipal);

        assertNotNull(response);
        assertEquals(userUid, response.getUid());
        assertEquals("PLUS", response.getCurrentTier());
        assertNotNull(response.getTierExpiresAt());
        assertEquals(1500, response.getCurrentTokens());
        assertEquals(12L, response.getTotalQuizzesAttempted());
        assertEquals(78.4, response.getAverageScorePercentage());
        assertEquals(2, response.getEraDistribution().size());
        assertEquals(7L, response.getEraDistribution().get("MODERN"));
        assertEquals(105L, response.getTotalAiMessages());
        assertEquals(1, response.getTopCharacters().size());
        assertEquals("Trần Hưng Đạo", response.getTopCharacters().get(0).getCharacterName());
        assertEquals(1, response.getPaymentHistory().size());
        assertEquals(123456L, response.getPaymentHistory().get(0).getOrderCode());
        assertEquals("PAID", response.getPaymentHistory().get(0).getStatus());
    }

    @Test
    @DisplayName("US-SP6-10: getSystemAdminDashboard returns JVM metrics, platform totals, and top wrong questions")
    void getSystemAdminDashboard_Success() {
        when(schoolRepository.sumTotalSchoolTokenQuota()).thenReturn(500000L);
        when(schoolRepository.sumUnallocatedSchoolTokenQuota()).thenReturn(270000L);
        when(messageRepository.sumTokensUsedByCustomerUsers()).thenReturn(120000L);
        when(messageRepository.sumAllTokensUsed()).thenReturn(350000L);

        DashboardTopWrongQuestionProjection wrongQ = mock(DashboardTopWrongQuestionProjection.class);
        when(wrongQ.getQuestionId()).thenReturn("q-001");
        when(wrongQ.getQuizTitle()).thenReturn("Lịch sử Việt Nam thế kỷ 10");
        when(wrongQ.getWrongAnswers()).thenReturn(45L);
        when(wrongQ.getTotalAnswers()).thenReturn(50L);
        when(wrongQ.getWrongRate()).thenReturn(0.9);

        when(quizAnswerDetailRepository.findTopWrongQuestionsForDashboard(any(), any(), eq(10)))
                .thenReturn(List.of(wrongQ));

        when(schoolRepository.count()).thenReturn(15L);
        when(schoolRepository.countByStatusAndDeletedAtIsNull(com.historytalk.entity.enums.SchoolStatus.ACTIVE)).thenReturn(13L);
        when(schoolRepository.countByStatusAndDeletedAtIsNull(com.historytalk.entity.enums.SchoolStatus.EXPIRED)).thenReturn(2L);
        when(userRepository.countAllUsers()).thenReturn(2500L);
        when(quizRepository.count()).thenReturn(80L);
        when(paymentOrderRepository.sumAmountByStatus(PaymentOrderStatus.PAID)).thenReturn(45000000L);

        SystemAdminComprehensiveDashboardResponse response = dashboardService.getSystemAdminDashboard();

        assertNotNull(response);
        assertNotNull(response.getJvmHealth());
        assertTrue(response.getJvmHealth().getTotalMemoryMb() > 0);
        assertTrue(response.getJvmHealth().getActiveThreadCount() > 0);

        assertEquals(500000L, response.getTokenSummary().getTotalTokensB2bAllocated());
        assertEquals(270000L, response.getTokenSummary().getTotalTokensB2bRemainingInPools());
        assertEquals(230000L, response.getTokenSummary().getTotalTokensB2bConsumed());
        assertEquals(120000L, response.getTokenSummary().getTotalTokensB2cConsumed());
        assertEquals(350000L, response.getTokenSummary().getTotalTokensSystemConsumed());

        assertEquals(1, response.getTopWrongQuestions().size());
        assertEquals("q-001", response.getTopWrongQuestions().get(0).getQuestionId());
        assertEquals(90.0, response.getTopWrongQuestions().get(0).getWrongRate());

        assertEquals(15L, response.getPlatformOverview().getTotalSchools());
        assertEquals(13L, response.getPlatformOverview().getActiveSchools());
        assertEquals(2L, response.getPlatformOverview().getExpiredSchools());
        assertEquals(2500L, response.getPlatformOverview().getTotalUsers());
        assertEquals(80L, response.getPlatformOverview().getTotalQuizzes());
        assertEquals(45000000L, response.getPlatformOverview().getTotalRevenue());
    }
}
