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
                .totalSchoolTokenQuota(100000)
                .unallocatedTokenQuota(40000)
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
                .token(5000)
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
    @DisplayName("US-SP6-06: getSchoolAdminDashboard returns accurate school statistics and token consumption")
    void getSchoolAdminDashboard_Success() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classroomRepository.countBySchoolIdAndDeletedAtIsNull(schoolId)).thenReturn(12L);
        when(userRepository.countBySchoolIdAndRole(schoolId, UserRole.TEACHER)).thenReturn(25L);
        when(userRepository.countBySchoolIdAndRole(schoolId, UserRole.SCHOOL_STUDENT)).thenReturn(450L);
        when(messageRepository.sumTokensUsedBySchoolStudents(schoolId)).thenReturn(15000L);

        SchoolAdminDashboardResponse response = dashboardService.getSchoolAdminDashboard(schoolId);

        assertNotNull(response);
        assertEquals(schoolId, response.getSchoolId());
        assertEquals("THPT Chu Văn An", response.getSchoolName());
        assertEquals("CVA", response.getSchoolCode());
        assertEquals(12L, response.getTotalClassrooms());
        assertEquals(25L, response.getTotalTeachers());
        assertEquals(450L, response.getTotalStudents());
        assertEquals(100000, response.getTotalSchoolTokenQuota());
        assertEquals(40000, response.getUnallocatedTokenQuota());
        assertEquals(60000, response.getAllocatedTokens());
        assertEquals(15000L, response.getTokensUsedByStudents());
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
    @DisplayName("US-SP6-07: getTeacherClassroomDashboard returns classroom metrics and student analytics")
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
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classId, schoolId))
                .thenReturn(Optional.of(classroom));
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
        assertEquals(1L, response.getStudentCount());
        assertEquals(42L, response.getTotalAiMessages());
        assertEquals(42.0, response.getAvgAiMessagesPerStudent());
        assertEquals(1, response.getStudents().size());

        TeacherClassroomDashboardResponse.StudentAnalyticsItem studentItem = response.getStudents().get(0);
        assertEquals(studentUser.getUid(), studentItem.getStudentId());
        assertEquals("Trần Quốc Dinh", studentItem.getFullName());
        assertEquals("HS1001", studentItem.getStudentCode());
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
    @DisplayName("US-SP6-08: getStudentDashboard returns student tokens, quiz progress, and enrolled classrooms")
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
                .teacher(teacherUser)
                .build();
        when(classStudentRepository.findClassroomsByStudentUid(studentUid)).thenReturn(List.of(classroom));

        StudentPersonalDashboardResponse response = dashboardService.getStudentDashboard(studentPrincipal);

        assertNotNull(response);
        assertEquals(studentUid, response.getStudentId());
        assertEquals("Trần Quốc Dinh", response.getStudentName());
        assertEquals("HS1001", response.getStudentCode());
        assertEquals("THPT Chu Văn An", response.getSchoolName());
        assertEquals(5000, response.getCurrentTokens());
        assertEquals(300L, response.getTokensUsedToday());
        assertEquals(8L, response.getTotalQuizzesAttempted());
        assertEquals(90.0, response.getAverageScorePercentage());
        assertEquals(1, response.getRecentQuizzes().size());
        assertEquals("Chiến dịch Điện Biên Phủ", response.getRecentQuizzes().get(0).getQuizTitle());
        assertEquals(1, response.getEnrolledClassrooms().size());
        assertEquals("10A1", response.getEnrolledClassrooms().get(0).getClassName());
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
        when(userRepository.countAllUsers()).thenReturn(2500L);
        when(quizRepository.count()).thenReturn(80L);
        when(paymentOrderRepository.sumAmountByStatus(PaymentOrderStatus.PAID)).thenReturn(45000000L);

        SystemAdminComprehensiveDashboardResponse response = dashboardService.getSystemAdminDashboard();

        assertNotNull(response);
        assertNotNull(response.getJvmHealth());
        assertTrue(response.getJvmHealth().getTotalMemoryMb() > 0);
        assertTrue(response.getJvmHealth().getActiveThreadCount() > 0);

        assertEquals(500000L, response.getTokenSummary().getTotalTokensB2bAllocated());
        assertEquals(120000L, response.getTokenSummary().getTotalTokensB2cConsumed());
        assertEquals(350000L, response.getTokenSummary().getTotalTokensSystemConsumed());

        assertEquals(1, response.getTopWrongQuestions().size());
        assertEquals("q-001", response.getTopWrongQuestions().get(0).getQuestionId());
        assertEquals(90.0, response.getTopWrongQuestions().get(0).getWrongRate());

        assertEquals(15L, response.getPlatformOverview().getTotalSchools());
        assertEquals(2500L, response.getPlatformOverview().getTotalUsers());
        assertEquals(80L, response.getPlatformOverview().getTotalQuizzes());
        assertEquals(45000000L, response.getPlatformOverview().getTotalRevenue());
    }
}
