package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.*;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.*;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.dashboard.DashboardTopWrongQuestionProjection;
import com.historytalk.repository.dashboard.DashboardUserTopCharacterProjection;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.utils.SecurityUtils;
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
    private final com.historytalk.repository.school.TokenBoostRequestRepository tokenBoostRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public SchoolAdminDashboardResponse getSchoolAdminDashboard(UUID schoolId) {
        UUID resolvedSchoolId = schoolId;
        if (resolvedSchoolId == null) {
            UserPrincipal resolvedPrincipal = resolvePrincipal(null);
            resolvedSchoolId = resolvedPrincipal.getSchoolId();
        }
        if (resolvedSchoolId == null) {
            throw new InvalidRequestException("Tài khoản Quản trị viên chưa được liên kết với trường học nào.");
        }

        UUID finalSchoolId = resolvedSchoolId;
        log.info("Fetching School Admin Dashboard for schoolId: {}", finalSchoolId);
        School school = schoolRepository.findById(finalSchoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with id: " + finalSchoolId));

        long totalClassrooms = classroomRepository.countBySchoolIdAndDeletedAtIsNull(finalSchoolId);
        long totalTeachers = userRepository.countBySchoolIdAndRole(finalSchoolId, UserRole.TEACHER);
        long totalStudents = userRepository.countBySchoolIdAndRole(finalSchoolId, UserRole.SCHOOL_STUDENT);

        int totalQuota = school.getTotalSchoolTokenQuota() != null ? school.getTotalSchoolTokenQuota() : 0;
        int unallocated = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        int allocated = Math.max(0, totalQuota - unallocated);
        long tokensUsedByStudents = messageRepository.sumTokensUsedBySchoolStudents(finalSchoolId);
        int defaultDailyQuota = school.getDefaultDailyStudentQuota() != null ? school.getDefaultDailyStudentQuota() : 1000;
        long pendingBoostRequests = 0L;
        if (tokenBoostRequestRepository != null) {
            pendingBoostRequests = tokenBoostRequestRepository.countBySchool_IdAndStatus(
                    finalSchoolId, com.historytalk.entity.enums.TokenBoostRequestStatus.PENDING)
                    + tokenBoostRequestRepository.countBySchool_IdAndStatus(
                    finalSchoolId, com.historytalk.entity.enums.TokenBoostRequestStatus.PENDING_ADMIN);
        }

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
                .packageType(school.getPackageType())
                .recommendedStudents(school.getPackageType() != null ? school.getPackageType().getRecommendedStudents() : null)
                .status(school.getStatus())
                .subscriptionStartDate(school.getSubscriptionStartDate())
                .subscriptionEndDate(school.getSubscriptionEndDate())
                .renewedForNextYear(school.getRenewedForNextYear())
                .nextPackageType(school.getNextPackageType())
                .totalClassrooms(totalClassrooms)
                .totalTeachers(totalTeachers)
                .totalStudents(totalStudents)
                .totalSchoolTokenQuota(totalQuota)
                .unallocatedTokenQuota(unallocated)
                .allocatedTokens(allocated)
                .tokensUsedByStudents(tokensUsedByStudents)
                .defaultDailyStudentQuota(defaultDailyQuota)
                .pendingTokenBoostRequests(pendingBoostRequests)
                .assignmentStats(assignmentStats)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherClassroomDashboardResponse getTeacherClassroomDashboard(UUID classId, UserPrincipal principal) {
        UserPrincipal resolvedPrincipal = resolvePrincipal(principal);
        UUID schoolId = resolvedPrincipal.getSchoolId();
        UUID teacherUid = UUID.fromString(resolvedPrincipal.getUid());
        log.info("Fetching Teacher Classroom Dashboard for classId: {} by teacher: {}", classId, teacherUid);

        Classroom classroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with id: " + classId));

        if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(teacherUid)) {
            throw new AccessDeniedException("Bạn không có quyền xem dữ liệu của lớp học này!");
        }

        School school = classroom.getSchool();
        int schoolDefaultDaily = (school != null && school.getDefaultDailyStudentQuota() != null && school.getDefaultDailyStudentQuota() > 0)
                ? school.getDefaultDailyStudentQuota()
                : 1000;
        int classDailyLimit = (classroom.getDailyTokenQuota() != null && classroom.getDailyTokenQuota() > 0)
                ? classroom.getDailyTokenQuota()
                : schoolDefaultDaily;
        int schoolPool = (school != null && school.getUnallocatedTokenQuota() != null)
                ? Math.max(0, school.getUnallocatedTokenQuota())
                : Integer.MAX_VALUE;
        long pendingBoostRequests = tokenBoostRequestRepository != null
                ? tokenBoostRequestRepository.countByClassroom_IdAndStatus(classId, com.historytalk.entity.enums.TokenBoostRequestStatus.PENDING)
                : 0L;

        // Lấy danh sách học sinh thuộc lớp
        List<UUID> studentUids = classStudentRepository.findEnrolledStudentUids(classId);
        long studentCount = studentUids.size();

        LocalDate today = LocalDate.now();
        List<TeacherClassroomDashboardResponse.StudentAnalyticsItem> studentItems = new ArrayList<>();
        if (!studentUids.isEmpty()) {
            List<User> students = userRepository.findAllById(studentUids);
            for (User student : students) {
                long quizzes = quizSessionRepository.countByUser_UidAndEndTimeIsNotNullAndDeletedAtIsNull(student.getUid());
                Double avgScore = quizSessionRepository.averageScorePercentageByUser(student.getUid());
                long aiMessages = messageRepository.countMessagesByUser(student.getUid());

                boolean isSameDay = student.getLastTokenResetAt() != null
                        && !student.getLastTokenResetAt().toLocalDate().isBefore(today);
                int usedToday = isSameDay && student.getDailyTokensUsed() != null
                        ? Math.max(0, student.getDailyTokensUsed())
                        : 0;
                int bonusToday = isSameDay && student.getDailyBonusTokens() != null
                        ? Math.max(0, student.getDailyBonusTokens())
                        : 0;
                int effectiveDailyLimit = classDailyLimit + bonusToday;
                int remainingToday = Math.min(Math.max(0, effectiveDailyLimit - usedToday), schoolPool);

                studentItems.add(TeacherClassroomDashboardResponse.StudentAnalyticsItem.builder()
                        .studentId(student.getUid())
                        .studentCode(student.getStudentCode())
                        .fullName(student.getFullName())
                        .dailyTokenLimit(classDailyLimit)
                        .dailyBonusTokens(bonusToday)
                        .dailyTokensUsed(usedToday)
                        .currentTokens(remainingToday)
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
                .status(classroom.getStatus() != null ? classroom.getStatus() : "ACTIVE")
                .dailyTokenLimit(classDailyLimit)
                .pendingTokenBoostRequests(pendingBoostRequests)
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
        UserPrincipal resolvedPrincipal = resolvePrincipal(principal);
        UUID studentUid = UUID.fromString(resolvedPrincipal.getUid());
        log.info("Fetching Student Personal Dashboard for studentUid: {}", studentUid);

        User student = userRepository.findById(studentUid)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentUid));

        School school = student.getSchool();
        String schoolName = school != null ? school.getName() : "";

        List<Classroom> enrolledClassrooms = classStudentRepository.findClassroomsByStudentUid(studentUid);
        int baseDailyLimit = (school != null && school.getDefaultDailyStudentQuota() != null && school.getDefaultDailyStudentQuota() > 0)
                ? school.getDefaultDailyStudentQuota()
                : 1000;
        for (Classroom c : enrolledClassrooms) {
            if ("ACTIVE".equalsIgnoreCase(c.getStatus()) && c.getDailyTokenQuota() != null && c.getDailyTokenQuota() > 0) {
                baseDailyLimit = c.getDailyTokenQuota();
                break;
            }
        }

        LocalDate today = LocalDate.now();
        boolean isSameDay = student.getLastTokenResetAt() != null
                && !student.getLastTokenResetAt().toLocalDate().isBefore(today);
        int bonusToday = isSameDay && student.getDailyBonusTokens() != null
                ? Math.max(0, student.getDailyBonusTokens())
                : 0;
        long queriedTokensUsedToday = messageRepository.sumTokensUsedByUserSince(studentUid, today.atStartOfDay());
        int trackedUsedToday = isSameDay && student.getDailyTokensUsed() != null
                ? Math.max(0, student.getDailyTokensUsed())
                : 0;
        long tokensUsedToday = Math.max(queriedTokensUsedToday, trackedUsedToday);

        int schoolPool = (school != null && school.getUnallocatedTokenQuota() != null)
                ? Math.max(0, school.getUnallocatedTokenQuota())
                : Integer.MAX_VALUE;
        int remainingDaily = (int) Math.max(0L, (baseDailyLimit + bonusToday) - tokensUsedToday);
        int currentTokens = Math.min(remainingDaily, schoolPool);

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

        List<StudentPersonalDashboardResponse.EnrolledClassroomItem> classroomItems = enrolledClassrooms.stream()
                .map(c -> StudentPersonalDashboardResponse.EnrolledClassroomItem.builder()
                        .classId(c.getId())
                        .className(c.getClassName())
                        .classCode(c.getClassCode())
                        .academicYear(c.getAcademicYear())
                        .status(c.getStatus() != null ? c.getStatus() : "ACTIVE")
                        .teacherName(c.getTeacher() != null ? c.getTeacher().getFullName() : "")
                        .build())
                .collect(Collectors.toList());

        return StudentPersonalDashboardResponse.builder()
                .studentId(student.getUid())
                .studentName(student.getFullName())
                .studentCode(student.getStudentCode())
                .schoolName(schoolName)
                .dailyTokenLimit(baseDailyLimit)
                .dailyBonusTokens(bonusToday)
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
        UserPrincipal resolvedPrincipal = resolvePrincipal(principal);
        UUID userUid = UUID.fromString(resolvedPrincipal.getUid());
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

        // 2. Token consumption summary (B2B Shared Pools vs B2C)
        long b2bAllocated = schoolRepository.sumTotalSchoolTokenQuota();
        long b2bRemainingInPools = schoolRepository.sumUnallocatedSchoolTokenQuota();
        long b2cConsumed = messageRepository.sumTokensUsedByCustomerUsers();
        long systemConsumed = messageRepository.sumAllTokensUsed();
        long b2bConsumed = Math.max(0L, systemConsumed - b2cConsumed);

        SystemAdminComprehensiveDashboardResponse.TokenConsumptionSummary tokenSummary =
                SystemAdminComprehensiveDashboardResponse.TokenConsumptionSummary.builder()
                        .totalTokensB2bAllocated(b2bAllocated)
                        .totalTokensB2bRemainingInPools(b2bRemainingInPools)
                        .totalTokensB2bConsumed(b2bConsumed)
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

        // 4. Platform Overview (including Active vs Expired Schools)
        long totalSchools = schoolRepository.count();
        long activeSchools = schoolRepository.countByStatusAndDeletedAtIsNull(com.historytalk.entity.enums.SchoolStatus.ACTIVE);
        long expiredSchools = schoolRepository.countByStatusAndDeletedAtIsNull(com.historytalk.entity.enums.SchoolStatus.EXPIRED);
        long totalUsers = userRepository.countAllUsers();
        long totalQuizzes = quizRepository.count();
        long totalRevenue = paymentOrderRepository.sumAmountByStatus(PaymentOrderStatus.PAID);

        SystemAdminComprehensiveDashboardResponse.PlatformOverview platformOverview =
                SystemAdminComprehensiveDashboardResponse.PlatformOverview.builder()
                        .totalSchools(totalSchools)
                        .activeSchools(activeSchools)
                        .expiredSchools(expiredSchools)
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

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) {
            return principal;
        }
        String userId = SecurityUtils.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
        return new UserPrincipal(user);
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
