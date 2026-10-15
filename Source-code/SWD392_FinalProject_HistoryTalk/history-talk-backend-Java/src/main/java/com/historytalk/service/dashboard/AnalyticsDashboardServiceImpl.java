package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.*;
import com.historytalk.entity.classroom.ClassStudent;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.UserTier;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsDashboardServiceImpl implements AnalyticsDashboardService {

    private final SchoolRepository schoolRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final QuizAnswerDetailRepository quizAnswerDetailRepository;
    private final MessageRepository messageRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserTierRepository userTierRepository;

    @Override
    @Transactional(readOnly = true)
    public SchoolAdminDashboardResponse getSchoolAdminDashboard(UUID schoolId) {
        log.info("Fetching School Admin Dashboard for schoolId: {}", schoolId);
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with id: " + schoolId));

        long totalClassrooms = classroomRepository.countBySchoolIdAndDeletedAtIsNull(schoolId);
        long totalTeachers = userRepository.countBySchoolIdAndRole(schoolId, UserRole.TEACHER);
        long totalStudents = userRepository.countBySchoolIdAndRole(schoolId, UserRole.SCHOOL_STUDENT);

        int totalQuota = school.getTotalSchoolTokenQuota() != null ? school.getTotalSchoolTokenQuota() : 0;
        int unallocated = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        int allocated = Math.max(0, totalQuota - unallocated);
        long tokensUsedByStudents = messageRepository.sumTokensUsedBySchoolStudents(schoolId);

        // Assignment progress distribution
        SchoolAdminDashboardResponse.AssignmentStats assignmentStats = SchoolAdminDashboardResponse.AssignmentStats.builder()
                .totalSubmissions(0L)
                .completedRate(0.0)
                .lateSubmittedRate(0.0)
                .notStartedRate(0.0)
                .build();

        return SchoolAdminDashboardResponse.builder()
                .schoolId(school.getId())
                .schoolName(school.getName())
                .schoolCode(school.getSchoolCode())
                .totalClassrooms(totalClassrooms)
                .totalTeachers(totalTeachers)
                .totalStudents(totalStudents)
                .totalSchoolTokenQuota(totalQuota)
                .unallocatedTokenQuota(unallocated)
                .allocatedTokens(allocated)
                .tokensUsedByStudents(tokensUsedByStudents)
                .assignmentStats(assignmentStats)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherClassroomDashboardResponse getTeacherClassroomDashboard(UUID classId, UserPrincipal principal) {
        UUID schoolId = principal.getSchoolId();
        UUID teacherUid = UUID.fromString(principal.getUid());
        log.info("Fetching Teacher Classroom Dashboard for classId: {} by teacher: {}", classId, teacherUid);

        Classroom classroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with id: " + classId));

        if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(teacherUid)) {
            throw new AccessDeniedException("Bạn không có quyền xem dữ liệu của lớp học này!");
        }

        // Lấy danh sách học sinh thuộc lớp
        List<UUID> studentUids = classStudentRepository.findEnrolledStudentUids(classId);
        long studentCount = studentUids.size();

        List<TeacherClassroomDashboardResponse.StudentAnalyticsItem> studentItems = new ArrayList<>();
        if (!studentUids.isEmpty()) {
            List<User> students = userRepository.findAllById(studentUids);
            for (User student : students) {
                long quizzes = quizSessionRepository.countByUser_UidAndEndTimeIsNotNullAndDeletedAtIsNull(student.getUid());
                Double avgScore = quizSessionRepository.averageScorePercentageByUser(student.getUid());
                long aiMessages = messageRepository.countMessagesByUser(student.getUid());

                studentItems.add(TeacherClassroomDashboardResponse.StudentAnalyticsItem.builder()
                        .studentId(student.getUid())
                        .studentCode(student.getStudentCode())
                        .fullName(student.getFullName())
                        .currentTokens(student.getToken() != null ? student.getToken() : 0)
                        .quizzesAttempted(quizzes)
                        .averageScorePercentage(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0)
                        .aiMessagesCount(aiMessages)
                        .build());
            }
        }

        long totalAiMessages = studentUids.isEmpty() ? 0L : messageRepository.countMessagesByStudentUids(studentUids);
        double avgAiMessages = studentCount > 0 ? (double) totalAiMessages / studentCount : 0.0;

        return TeacherClassroomDashboardResponse.builder()
                .classId(classroom.getId())
                .className(classroom.getClassName())
                .classCode(classroom.getClassCode())
                .gradeLevel(classroom.getGradeLevel() != null ? classroom.getGradeLevel() : 0)
                .academicYear(classroom.getAcademicYear() != null ? classroom.getAcademicYear() : 0)
                .studentCount(studentCount)
                .totalAiMessages(totalAiMessages)
                .avgAiMessagesPerStudent(Math.round(avgAiMessages * 10.0) / 10.0)
                .assignmentRates(TeacherClassroomDashboardResponse.AssignmentRates.builder()
                        .onTimeRate(0.0)
                        .lateRate(0.0)
                        .pendingRate(0.0)
                        .build())
                .students(studentItems)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentPersonalDashboardResponse getStudentDashboard(UserPrincipal principal) {
        UUID studentUid = UUID.fromString(principal.getUid());
        log.info("Fetching Student Personal Dashboard for studentUid: {}", studentUid);

        User student = userRepository.findById(studentUid)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentUid));

        String schoolName = student.getSchool() != null ? student.getSchool().getName() : "";
        int currentTokens = student.getToken() != null ? student.getToken() : 0;
        long tokensUsedToday = messageRepository.sumTokensUsedByUserSince(studentUid, LocalDate.now().atStartOfDay());

        long totalQuizzes = quizSessionRepository.countByUser_UidAndEndTimeIsNotNullAndDeletedAtIsNull(studentUid);
        Double avgScore = quizSessionRepository.averageScorePercentageByUser(studentUid);

        List<QuizSession> recentSessions = quizSessionRepository.findRecentCompletedByUser(studentUid, PageRequest.of(0, 5));
        List<StudentPersonalDashboardResponse.RecentQuizItem> recentQuizItems = recentSessions.stream()
                .map(s -> StudentPersonalDashboardResponse.RecentQuizItem.builder()
                        .sessionId(s.getSessionId())
                        .quizTitle(s.getQuiz() != null ? s.getQuiz().getTitle() : "")
                        .percentage(calculateSessionPercentage(s))
                        .completedAt(s.getEndTime())
                        .build())
                .collect(Collectors.toList());

        List<Classroom> enrolledClassrooms = classStudentRepository.findClassroomsByStudentUid(studentUid);
        List<StudentPersonalDashboardResponse.EnrolledClassroomItem> classroomItems = enrolledClassrooms.stream()
                .map(c -> StudentPersonalDashboardResponse.EnrolledClassroomItem.builder()
                        .classId(c.getId())
                        .className(c.getClassName())
                        .classCode(c.getClassCode())
                        .teacherName(c.getTeacher() != null ? c.getTeacher().getFullName() : "")
                        .build())
                .collect(Collectors.toList());

        return StudentPersonalDashboardResponse.builder()
                .studentId(student.getUid())
                .studentName(student.getFullName())
                .studentCode(student.getStudentCode())
                .schoolName(schoolName)
                .currentTokens(currentTokens)
                .tokensUsedToday(tokensUsedToday)
                .totalQuizzesAttempted(totalQuizzes)
                .averageScorePercentage(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0)
                .recentQuizzes(recentQuizItems)
                .enrolledClassrooms(classroomItems)
                .upcomingAssignments(Collections.emptyList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerPersonalDashboardResponse getCustomerDashboard(UserPrincipal principal) {
        UUID userUid = UUID.fromString(principal.getUid());
        log.info("Fetching Customer B2C Dashboard for userUid: {}", userUid);

        User user = userRepository.findById(userUid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userUid));

        Optional<UserTier> activeTier = userTierRepository.findCurrentActiveByUid(userUid, LocalDateTime.now());
        String currentTier = activeTier.map(ut -> ut.getTier() != null ? ut.getTier().getTitle() : "FREE").orElse("FREE");
        LocalDateTime tierExpiresAt = activeTier.map(UserTier::getEndTime).orElse(null);
        int currentTokens = user.getToken() != null ? user.getToken() : 0;

        long totalQuizzes = quizSessionRepository.countByUser_UidAndEndTimeIsNotNullAndDeletedAtIsNull(userUid);
        Double avgScore = quizSessionRepository.averageScorePercentageByUser(userUid);

        List<QuizSession> recentSessions = quizSessionRepository.findRecentCompletedByUser(userUid, PageRequest.of(0, 5));
        List<CustomerPersonalDashboardResponse.RecentQuizItem> recentQuizItems = recentSessions.stream()
                .map(s -> CustomerPersonalDashboardResponse.RecentQuizItem.builder()
                        .sessionId(s.getSessionId())
                        .quizTitle(s.getQuiz() != null ? s.getQuiz().getTitle() : "")
                        .percentage(calculateSessionPercentage(s))
                        .completedAt(s.getEndTime())
                        .build())
                .collect(Collectors.toList());

        // Era distribution
        Map<String, Long> eraDistribution = new HashMap<>();
        List<Object[]> rawEras = quizSessionRepository.countEraDistributionByUser(userUid);
        for (Object[] row : rawEras) {
            String era = row[0] != null ? row[0].toString() : "UNKNOWN";
            Long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            eraDistribution.put(era, count);
        }

        long totalAiMessages = messageRepository.countMessagesByUser(userUid);
        List<DashboardUserTopCharacterProjection> topCharProjections = messageRepository.findTopCharactersForUser(userUid, 5);
        List<CustomerPersonalDashboardResponse.TopCharacterItem> topCharacters = topCharProjections.stream()
                .map(c -> CustomerPersonalDashboardResponse.TopCharacterItem.builder()
                        .characterId(c.getCharacterId())
                        .characterName(c.getName())
                        .messageCount(c.getMessageCount() != null ? c.getMessageCount() : 0L)
                        .build())
                .collect(Collectors.toList());

        List<PaymentOrder> orders = paymentOrderRepository.findByUser_UidOrderByCreatedAtDesc(userUid);
        List<CustomerPersonalDashboardResponse.PaymentTransactionItem> paymentHistory = orders.stream()
                .limit(5)
                .map(o -> CustomerPersonalDashboardResponse.PaymentTransactionItem.builder()
                        .orderCode(o.getOrderCode())
                        .amount(o.getAmount())
                        .tierName(o.getTier() != null ? o.getTier().getTitle() : "")
                        .status(o.getStatus() != null ? o.getStatus().name() : "")
                        .createdAt(o.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return CustomerPersonalDashboardResponse.builder()
                .uid(user.getUid())
                .userName(user.getUserName())
                .email(user.getEmail())
                .currentTier(currentTier)
                .currentTokens(currentTokens)
                .tierExpiresAt(tierExpiresAt)
                .totalQuizzesAttempted(totalQuizzes)
                .averageScorePercentage(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0)
                .eraDistribution(eraDistribution)
                .recentQuizzes(recentQuizItems)
                .totalAiMessages(totalAiMessages)
                .topCharacters(topCharacters)
                .paymentHistory(paymentHistory)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SystemAdminComprehensiveDashboardResponse getSystemAdminDashboard() {
        log.info("Fetching System Admin Comprehensive Dashboard");

        // 1. JVM Health Metrics
        Runtime rt = Runtime.getRuntime();
        long totalMemoryMb = rt.totalMemory() / (1024 * 1024);
        long freeMemoryMb = rt.freeMemory() / (1024 * 1024);
        long usedMemoryMb = totalMemoryMb - freeMemoryMb;
        long maxMemoryMb = rt.maxMemory() / (1024 * 1024);
        double memoryUsage = totalMemoryMb > 0 ? (double) usedMemoryMb * 100.0 / totalMemoryMb : 0.0;
        int activeThreads = Thread.activeCount();
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        double cpuUsage = 0.0;
        try {
            var osBean = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            if (osBean != null) {
                double load = osBean.getCpuLoad();
                if (load >= 0) {
                    cpuUsage = Math.round(load * 1000.0) / 10.0;
                }
            }
        } catch (Exception ignored) {
        }

        SystemAdminComprehensiveDashboardResponse.JvmHealthMetrics jvmHealth =
                SystemAdminComprehensiveDashboardResponse.JvmHealthMetrics.builder()
                        .cpuUsagePercentage(cpuUsage)
                        .totalMemoryMb(totalMemoryMb)
                        .usedMemoryMb(usedMemoryMb)
                        .freeMemoryMb(freeMemoryMb)
                        .maxMemoryMb(maxMemoryMb)
                        .memoryUsagePercentage(Math.round(memoryUsage * 10.0) / 10.0)
                        .activeThreadCount(activeThreads)
                        .uptimeSeconds(uptimeSeconds)
                        .build();

        // 2. Token consumption summary
        long b2bAllocated = schoolRepository.sumTotalSchoolTokenQuota();
        long b2cConsumed = messageRepository.sumTokensUsedByCustomerUsers();
        long systemConsumed = messageRepository.sumAllTokensUsed();

        SystemAdminComprehensiveDashboardResponse.TokenConsumptionSummary tokenSummary =
                SystemAdminComprehensiveDashboardResponse.TokenConsumptionSummary.builder()
                        .totalTokensB2bAllocated(b2bAllocated)
                        .totalTokensB2cConsumed(b2cConsumed)
                        .totalTokensSystemConsumed(systemConsumed)
                        .build();

        // 3. Top 10 Wrong Questions
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<DashboardTopWrongQuestionProjection> wrongProjections =
                quizAnswerDetailRepository.findTopWrongQuestionsForDashboard(oneYearAgo, LocalDateTime.now(), 10);
        List<SystemAdminComprehensiveDashboardResponse.TopWrongQuestionItem> topWrong = wrongProjections.stream()
                .map(q -> SystemAdminComprehensiveDashboardResponse.TopWrongQuestionItem.builder()
                        .questionId(q.getQuestionId())
                        .quizTitle(q.getQuizTitle())
                        .wrongAnswers(q.getWrongAnswers() != null ? q.getWrongAnswers() : 0L)
                        .totalAnswers(q.getTotalAnswers() != null ? q.getTotalAnswers() : 0L)
                        .wrongRate(q.getWrongRate() != null ? Math.round(q.getWrongRate() * 1000.0) / 10.0 : 0.0)
                        .build())
                .collect(Collectors.toList());

        // 4. Platform Overview
        long totalSchools = schoolRepository.count();
        long totalUsers = userRepository.countAllUsers();
        long totalQuizzes = quizRepository.count();
        long totalRevenue = paymentOrderRepository.sumAmountByStatus(PaymentOrderStatus.PAID);

        SystemAdminComprehensiveDashboardResponse.PlatformOverview platformOverview =
                SystemAdminComprehensiveDashboardResponse.PlatformOverview.builder()
                        .totalSchools(totalSchools)
                        .totalUsers(totalUsers)
                        .totalQuizzes(totalQuizzes)
                        .totalRevenue(totalRevenue)
                        .build();

        return SystemAdminComprehensiveDashboardResponse.builder()
                .jvmHealth(jvmHealth)
                .tokenSummary(tokenSummary)
                .topWrongQuestions(topWrong)
                .platformOverview(platformOverview)
                .build();
    }

    private double calculateSessionPercentage(QuizSession session) {
        if (session.getScore() == null) return 0.0;
        int questionCount = (session.getQuiz() != null && session.getQuiz().getQuestions() != null)
                ? session.getQuiz().getQuestions().size()
                : 10;
        if (questionCount == 0) return 0.0;
        return Math.round((session.getScore() * 100.0 / questionCount) * 10.0) / 10.0;
    }
}
