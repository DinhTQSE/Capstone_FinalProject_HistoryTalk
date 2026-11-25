package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.*;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.repository.dashboard.ClassroomTokenUsageProjection;
import com.historytalk.repository.dashboard.StudentTokenUsageDetailProjection;
import com.historytalk.repository.dashboard.AssignmentSubmissionStatsProjection;
import com.historytalk.repository.dashboard.StudentQuizSummaryProjection;
import com.historytalk.repository.dashboard.StudentMessageCountProjection;
import com.historytalk.repository.assignment.AssignmentSubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.PaymentOrderType;
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
import com.historytalk.repository.dashboard.DashboardPeriodCountProjection;
import com.historytalk.repository.dashboard.DashboardQuizTrendProjection;
import com.historytalk.repository.dashboard.DashboardTokenBalanceSummaryProjection;
import com.historytalk.repository.dashboard.DashboardTokenSummaryProjection;
import com.historytalk.repository.dashboard.DashboardTokenTrendProjection;
import com.historytalk.repository.dashboard.DashboardTopCharacterProjection;
import com.historytalk.repository.dashboard.DashboardTopQuizProjection;
import com.historytalk.repository.dashboard.DashboardTopTokenUserProjection;
import com.historytalk.repository.dashboard.DashboardTopWrongQuestionProjection;
import com.historytalk.repository.dashboard.DashboardUserTopCharacterProjection;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.utils.SecurityUtils;
import com.historytalk.service.school.token.TokenDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsDashboardServiceImpl implements AnalyticsDashboardService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int DEFAULT_RANGE_DAYS = 29;
    private static final int MAX_RANGE_DAYS = 180;
    private static final long ACTIVE_SESSION_WINDOW_MINUTES = 2L;
    private static final double TOKEN_UNIT_PRICE_VND = 0.15;

    private final SchoolRepository schoolRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final HistoricalContextRepository historicalContextRepository;
    private final CharacterRepository characterRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final QuizAnswerDetailRepository quizAnswerDetailRepository;
    private final MessageRepository messageRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserTierRepository userTierRepository;
    private final com.historytalk.repository.school.TokenBoostRequestRepository tokenBoostRequestRepository;
    private final com.historytalk.repository.assignment.AssignmentSubmissionRepository assignmentSubmissionRepository;

    @Override
    @Transactional(readOnly = true)
    public SchoolAdminDashboardResponse getSchoolAdminDashboard(UUID schoolId) {
        UUID finalSchoolId = resolveSchoolIdOrThrow(schoolId);
        log.info("Fetching School Admin Dashboard for schoolId: {}", finalSchoolId);
        School school = schoolRepository.findById(finalSchoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + finalSchoolId));

        long totalClassrooms = classroomRepository.countBySchoolIdAndDeletedAtIsNull(finalSchoolId);
        long totalTeachers = userRepository.countBySchoolIdAndRole(finalSchoolId, UserRole.TEACHER);
        long totalStudents = userRepository.countBySchoolIdAndRole(finalSchoolId, UserRole.SCHOOL_STUDENT);

        int totalQuota = school.getTotalSchoolTokenQuota() != null ? school.getTotalSchoolTokenQuota() : 0;
        int unallocated = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        int allocated = Math.max(0, totalQuota - unallocated);
        double quotaUsagePercentage = totalQuota > 0
                ? Math.round((allocated * 100.0 / totalQuota) * 10.0) / 10.0
                : 0.0;
        long tokensUsedByStudents = messageRepository.sumTokensUsedBySchoolStudents(finalSchoolId);
        long tokensUsedByTeachers = messageRepository.sumTokensUsedBySchoolTeachers(finalSchoolId);
        long tokensUsedTotal = tokensUsedByStudents + tokensUsedByTeachers;
        double studentPercent = tokensUsedTotal > 0 ? Math.round((tokensUsedByStudents * 100.0 / tokensUsedTotal) * 10.0) / 10.0 : 0.0;
        double teacherPercent = tokensUsedTotal > 0 ? Math.round((tokensUsedByTeachers * 100.0 / tokensUsedTotal) * 10.0) / 10.0 : 0.0;

        LocalDateTime todayStart = TokenDay.startOfToday();
        List<ClassroomTokenUsageProjection> topClassProjections = classroomRepository.findTopClassroomsByTokenUsage(finalSchoolId, todayStart, 5);
        List<ClassroomTokenUsageSummaryDto> topClassrooms = (topClassProjections != null) ? topClassProjections.stream()
                .map(p -> ClassroomTokenUsageSummaryDto.builder()
                        .classroomId(p.getClassroomId())
                        .className(p.getClassName())
                        .classCode(p.getClassCode())
                        .gradeLevel(p.getGradeLevel())
                        .academicYear(p.getAcademicYear())
                        .status(p.getStatus())
                        .teacherName(p.getTeacherName())
                        .teacherUid(p.getTeacherUid())
                        .studentCount(p.getStudentCount() != null ? p.getStudentCount() : 0L)
                        .tokensUsedTotal(p.getTokensUsedTotal() != null ? p.getTokensUsedTotal() : 0L)
                        .tokensUsedToday(p.getTokensUsedToday() != null ? p.getTokensUsedToday() : 0L)
                        .percentageOfSchoolUsage(tokensUsedTotal > 0 && p.getTokensUsedTotal() != null
                                ? Math.round((p.getTokensUsedTotal() * 100.0 / tokensUsedTotal) * 10.0) / 10.0
                                : 0.0)
                        .build())
                .toList() : new ArrayList<>();

        int defaultDailyQuota = school.getDefaultDailyStudentQuota() != null ? school.getDefaultDailyStudentQuota() : 1000;
        int defaultDailyTeacherQuota = school.getDefaultDailyTeacherQuota() != null ? school.getDefaultDailyTeacherQuota() : 5000;
        long pendingBoostRequests = countPendingBoostRequests(finalSchoolId);

        long topupPaidOrders = paymentOrderRepository != null
                ? paymentOrderRepository.countByDeletedAtIsNullAndSchool_IdAndStatusAndOrderType(
                        finalSchoolId, PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP)
                : 0L;
        long topupTokensPurchased = paymentOrderRepository != null
                ? toLong(paymentOrderRepository.sumTopupTokensBySchoolIdAndStatusAndOrderType(
                        finalSchoolId, PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP))
                : 0L;
        long topupAmountPaid = paymentOrderRepository != null
                ? toLong(paymentOrderRepository.sumAmountBySchoolIdAndStatusAndOrderType(
                        finalSchoolId, PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP))
                : 0L;

        SchoolAdminDashboardResponse.AssignmentStats assignmentStats = buildSchoolAssignmentStats(finalSchoolId);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDateTime utcTodayStart = today.atStartOfDay();
        LocalDateTime utcTomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime utcMonthStart = today.withDayOfMonth(1).atStartOfDay();

        DashboardOverviewResponse.UserOverview userOverview = buildSchoolUserOverview(
                finalSchoolId, utcTodayStart, utcTomorrowStart, utcMonthStart);
        DashboardOverviewResponse.RoleOverview roleOverview = buildSchoolRoleOverview(finalSchoolId);
        DashboardContentSummaryResponse contentSummary = buildSchoolContentSummaryInternal(finalSchoolId);
        DashboardOverviewResponse.ChatOverview chatOverview = buildSchoolChatOverview(
                finalSchoolId, utcTodayStart, utcTomorrowStart);
        List<DashboardOverviewResponse.TopCharacterOverview> topLocalCharacters = buildSchoolTopLocalCharacters(finalSchoolId);
        DashboardQuizAnalyticsResponse.QuizSummary quizSummary = buildSchoolQuizSummary(finalSchoolId);

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
                .quotaUsagePercentage(quotaUsagePercentage)
                .tokensUsedByStudents(tokensUsedByStudents)
                .tokensUsedByTeachers(tokensUsedByTeachers)
                .tokensUsedTotal(tokensUsedTotal)
                .studentTokenUsagePercentage(studentPercent)
                .teacherTokenUsagePercentage(teacherPercent)
                .topClassroomsByTokenUsage(topClassrooms)
                .defaultDailyStudentQuota(defaultDailyQuota)
                .defaultDailyTeacherQuota(defaultDailyTeacherQuota)
                .pendingTokenBoostRequests(pendingBoostRequests)
                .topupPaidOrders(topupPaidOrders)
                .topupTokensPurchased(topupTokensPurchased)
                .topupAmountPaid(topupAmountPaid)
                .assignmentStats(assignmentStats)
                .users(userOverview)
                .roles(roleOverview)
                .content(contentSummary)
                .chat(chatOverview)
                .topLocalCharacters(topLocalCharacters)
                .quiz(quizSummary)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getSchoolAdminOverview(UUID schoolId) {
        UUID finalSchoolId = resolveSchoolIdOrThrow(schoolId);
        log.info("Fetching School Admin Overview Dashboard for schoolId: {}", finalSchoolId);
        School school = schoolRepository.findById(finalSchoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + finalSchoolId));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        DashboardOverviewResponse.UserOverview userOverview = buildSchoolUserOverview(
                finalSchoolId, todayStart, tomorrowStart, monthStart);
        DashboardOverviewResponse.RoleOverview roleOverview = buildSchoolRoleOverview(finalSchoolId);

        long localContexts = historicalContextRepository != null
                ? historicalContextRepository.countBySchoolIdCurrent(finalSchoolId)
                : 0L;
        long publishedLocalContexts = historicalContextRepository != null
                ? historicalContextRepository.countBySchoolIdPublished(finalSchoolId)
                : 0L;
        long localChars = characterRepository != null
                ? characterRepository.countBySchoolIdCurrent(finalSchoolId)
                : 0L;
        long publishedLocalChars = characterRepository != null
                ? characterRepository.countBySchoolIdPublished(finalSchoolId)
                : 0L;
        long localDocs = documentRepository != null
                ? documentRepository.countBySchoolIdCurrent(finalSchoolId)
                : 0L;

        DashboardOverviewResponse.ContentOverview contentOverview = DashboardOverviewResponse.ContentOverview.builder()
                .historicalContexts(localContexts)
                .publishedHistoricalContexts(publishedLocalContexts)
                .characters(localChars)
                .publishedCharacters(publishedLocalChars)
                .documents(localDocs)
                .build();

        DashboardOverviewResponse.ChatOverview chatOverview = buildSchoolChatOverview(
                finalSchoolId, todayStart, tomorrowStart);

        // School Admin chỉ thống kê nhân vật Local của trường, không thống kê nhân vật Global
        List<DashboardOverviewResponse.TopCharacterOverview> topLocalCharacters = buildSchoolTopLocalCharacters(finalSchoolId);

        long totalClassrooms = classroomRepository != null
                ? classroomRepository.countBySchoolIdAndDeletedAtIsNull(finalSchoolId)
                : 0L;
        long totalTeachers = userRepository.countBySchoolIdAndRole(finalSchoolId, UserRole.TEACHER);
        long totalStudents = userRepository.countBySchoolIdAndRole(finalSchoolId, UserRole.SCHOOL_STUDENT);

        int totalQuota = school.getTotalSchoolTokenQuota() != null ? school.getTotalSchoolTokenQuota() : 0;
        int unallocated = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        int allocated = Math.max(0, totalQuota - unallocated);
        double quotaUsagePercentage = totalQuota > 0
                ? Math.round((allocated * 100.0 / totalQuota) * 10.0) / 10.0
                : 0.0;
        long tokensUsedByStudents = messageRepository.sumTokensUsedBySchoolStudents(finalSchoolId);
        long tokensUsedByTeachers = messageRepository.sumTokensUsedBySchoolTeachers(finalSchoolId);
        long tokensUsedTotal = tokensUsedByStudents + tokensUsedByTeachers;
        double studentPercent = tokensUsedTotal > 0
                ? Math.round((tokensUsedByStudents * 100.0 / tokensUsedTotal) * 10.0) / 10.0
                : 0.0;
        double teacherPercent = tokensUsedTotal > 0
                ? Math.round((tokensUsedByTeachers * 100.0 / tokensUsedTotal) * 10.0) / 10.0
                : 0.0;

        int defaultDailyStudentQuota = school.getDefaultDailyStudentQuota() != null ? school.getDefaultDailyStudentQuota() : 5000;
        int defaultDailyTeacherQuota = school.getDefaultDailyTeacherQuota() != null ? school.getDefaultDailyTeacherQuota() : 5000;
        long pendingBoostRequests = countPendingBoostRequests(finalSchoolId);

        long topupPaidOrders = paymentOrderRepository != null
                ? paymentOrderRepository.countByDeletedAtIsNullAndSchool_IdAndStatusAndOrderType(
                        finalSchoolId, PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP)
                : 0L;
        long topupTokensPurchased = paymentOrderRepository != null
                ? toLong(paymentOrderRepository.sumTopupTokensBySchoolIdAndStatusAndOrderType(
                        finalSchoolId, PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP))
                : 0L;
        long topupAmountPaid = paymentOrderRepository != null
                ? toLong(paymentOrderRepository.sumAmountBySchoolIdAndStatusAndOrderType(
                        finalSchoolId, PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP))
                : 0L;

        DashboardOverviewResponse.SchoolTokenOverview schoolTokenOverview = DashboardOverviewResponse.SchoolTokenOverview.builder()
                .schoolId(school.getId())
                .schoolName(school.getName())
                .schoolCode(school.getSchoolCode())
                .packageType(school.getPackageType())
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
                .quotaUsagePercentage(quotaUsagePercentage)
                .tokensUsedByStudents(tokensUsedByStudents)
                .tokensUsedByTeachers(tokensUsedByTeachers)
                .tokensUsedTotal(tokensUsedTotal)
                .studentTokenUsagePercentage(studentPercent)
                .teacherTokenUsagePercentage(teacherPercent)
                .defaultDailyStudentQuota(defaultDailyStudentQuota)
                .defaultDailyTeacherQuota(defaultDailyTeacherQuota)
                .pendingTokenBoostRequests(pendingBoostRequests)
                .topupPaidOrders(topupPaidOrders)
                .topupTokensPurchased(topupTokensPurchased)
                .topupAmountPaid(topupAmountPaid)
                .assignmentStats(buildSchoolAssignmentStats(finalSchoolId))
                .build();

        return DashboardOverviewResponse.builder()
                .users(userOverview)
                .roles(roleOverview)
                .content(contentOverview)
                .chat(chatOverview)
                .topCharacters(topLocalCharacters)
                .topGlobalCharacters(List.of())
                .topLocalCharacters(topLocalCharacters)
                .systemHealth(DashboardOverviewResponse.SystemHealthOverview.builder()
                        .status("UP")
                        .lastCheckedAt(LocalDateTime.now(ZoneOffset.UTC))
                        .build())
                .schoolTokenOverview(schoolTokenOverview)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardUserAnalyticsResponse getSchoolAdminUserAnalytics(
            UUID schoolId, LocalDate from, LocalDate to, String granularity) {
        UUID finalSchoolId = resolveSchoolIdOrThrow(schoolId);
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        validateSchoolExists(finalSchoolId);

        Map<String, Long> newUsersByPeriod = toPeriodMap(
                userRepository.countNewUsersByPeriodAndSchoolId(
                        range.fromDateTime(), range.toExclusive(), bucket, finalSchoolId));
        Map<String, Long> activeUsersByPeriod = toPeriodMap(
                userRepository.countActiveUsersByPeriodAndSchoolId(
                        range.fromDateTime(), range.toExclusive(), bucket, finalSchoolId));

        DashboardUserAnalyticsResponse.UserSummary summary = DashboardUserAnalyticsResponse.UserSummary.builder()
                .total(userRepository.countAllUsersBySchoolId(finalSchoolId))
                .active(userRepository.countActiveUsersBySchoolId(finalSchoolId))
                .inactive(userRepository.countInactiveUsersBySchoolId(finalSchoolId))
                .deleted(userRepository.countDeletedUsersBySchoolId(finalSchoolId))
                .recentlyActive(userRepository.countRecentlyActiveUsersBySchoolId(
                        finalSchoolId, LocalDateTime.now(ZoneOffset.UTC).minusDays(7)))
                .build();

        List<DashboardUserAnalyticsResponse.RoleCount> byRole = List.of(
                DashboardUserAnalyticsResponse.RoleCount.builder()
                        .role(UserRole.SCHOOL_ADMIN.name())
                        .count(userRepository.countActiveUsersBySchoolIdAndRole(finalSchoolId, UserRole.SCHOOL_ADMIN))
                        .build(),
                DashboardUserAnalyticsResponse.RoleCount.builder()
                        .role(UserRole.TEACHER.name())
                        .count(userRepository.countActiveUsersBySchoolIdAndRole(finalSchoolId, UserRole.TEACHER))
                        .build(),
                DashboardUserAnalyticsResponse.RoleCount.builder()
                        .role(UserRole.SCHOOL_STUDENT.name())
                        .count(userRepository.countActiveUsersBySchoolIdAndRole(finalSchoolId, UserRole.SCHOOL_STUDENT))
                        .build()
        );

        List<DashboardUserAnalyticsResponse.UserTrendPoint> trend = initializePeriods(range.from(), range.to(), bucket)
                .stream()
                .map(period -> DashboardUserAnalyticsResponse.UserTrendPoint.builder()
                        .date(period)
                        .newUsers(newUsersByPeriod.getOrDefault(period, 0L))
                        .activeUsers(activeUsersByPeriod.getOrDefault(period, 0L))
                        .build())
                .toList();

        return DashboardUserAnalyticsResponse.builder()
                .summary(summary)
                .byRole(byRole)
                .trend(trend)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardContentSummaryResponse getSchoolAdminContentSummary(UUID schoolId) {
        UUID finalSchoolId = resolveSchoolIdOrThrow(schoolId);
        validateSchoolExists(finalSchoolId);
        return buildSchoolContentSummaryInternal(finalSchoolId);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardChatActivityResponse getSchoolAdminChatActivity(
            UUID schoolId, LocalDate from, LocalDate to, String granularity) {
        UUID finalSchoolId = resolveSchoolIdOrThrow(schoolId);
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        validateSchoolExists(finalSchoolId);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime activeCutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(ACTIVE_SESSION_WINDOW_MINUTES);

        Map<String, Long> sessionsByPeriod = chatSessionRepository != null
                ? toPeriodMap(chatSessionRepository.countSessionsByPeriodAndSchoolId(
                        range.fromDateTime(), range.toExclusive(), bucket, finalSchoolId))
                : Map.of();
        Map<String, Long> messagesByPeriod = toPeriodMap(
                messageRepository.countMessagesByPeriodAndSchoolId(
                        range.fromDateTime(), range.toExclusive(), bucket, finalSchoolId));

        DashboardChatActivityResponse.ChatSummary summary = DashboardChatActivityResponse.ChatSummary.builder()
                .sessions(chatSessionRepository != null ? chatSessionRepository.countCurrentBySchoolId(finalSchoolId) : 0L)
                .activeSessions(chatSessionRepository != null ? chatSessionRepository.countActiveSinceBySchoolId(activeCutoff, finalSchoolId) : 0L)
                .messages(messageRepository.countCurrentBySchoolId(finalSchoolId))
                .userMessages(messageRepository.countByAiFlagAndSchoolId(false, finalSchoolId))
                .aiMessages(messageRepository.countByAiFlagAndSchoolId(true, finalSchoolId))
                .sessionsToday(chatSessionRepository != null
                        ? chatSessionRepository.countCreatedBetweenBySchoolId(todayStart, tomorrowStart, finalSchoolId)
                        : 0L)
                .messagesToday(messageRepository.countCreatedBetweenBySchoolId(todayStart, tomorrowStart, finalSchoolId))
                .build();

        List<DashboardChatActivityResponse.ChatTrendPoint> trend = initializePeriods(range.from(), range.to(), bucket)
                .stream()
                .map(period -> DashboardChatActivityResponse.ChatTrendPoint.builder()
                        .date(period)
                        .sessions(sessionsByPeriod.getOrDefault(period, 0L))
                        .messages(messagesByPeriod.getOrDefault(period, 0L))
                        .build())
                .toList();

        return DashboardChatActivityResponse.builder()
                .summary(summary)
                .trend(trend)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardQuizAnalyticsResponse getSchoolAdminQuizAnalytics(
            UUID schoolId, LocalDate from, LocalDate to, String granularity) {
        UUID finalSchoolId = resolveSchoolIdOrThrow(schoolId);
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        validateSchoolExists(finalSchoolId);

        DashboardQuizAnalyticsResponse.QuizSummary summary = buildSchoolQuizSummary(finalSchoolId);

        Map<String, DashboardQuizTrendProjection> sessionsByPeriod = quizSessionRepository != null
                ? toQuizPeriodMap(quizSessionRepository.countSessionsByPeriodAndSchoolId(
                        range.fromDateTime(), range.toExclusive(), bucket, finalSchoolId))
                : Map.of();

        List<DashboardQuizAnalyticsResponse.QuizSessionTrendPoint> trend = initializePeriods(
                range.from(), range.to(), bucket)
                .stream()
                .map(period -> {
                    DashboardQuizTrendProjection row = sessionsByPeriod.get(period);
                    return DashboardQuizAnalyticsResponse.QuizSessionTrendPoint.builder()
                            .date(period)
                            .started(row == null ? 0L : nullSafe(row.getStarted()))
                            .completed(row == null ? 0L : nullSafe(row.getCompleted()))
                            .build();
                })
                .toList();

        List<DashboardQuizAnalyticsResponse.TopQuiz> topQuizzes = quizRepository != null
                ? quizRepository.findTopQuizzesForDashboardBySchoolId(
                                range.fromDateTime(), range.toExclusive(), finalSchoolId, 5)
                        .stream()
                        .map(this::toTopQuiz)
                        .toList()
                : List.of();

        List<DashboardQuizAnalyticsResponse.TopWrongQuestion> topWrongQuestions = quizAnswerDetailRepository != null
                ? quizAnswerDetailRepository.findTopWrongQuestionsForDashboardBySchoolId(
                                range.fromDateTime(), range.toExclusive(), finalSchoolId, 5)
                        .stream()
                        .map(this::toTopWrongQuestion)
                        .toList()
                : List.of();

        return DashboardQuizAnalyticsResponse.builder()
                .summary(summary)
                .sessionsTrend(trend)
                .topQuizzes(topQuizzes)
                .topWrongQuestions(topWrongQuestions)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardTokenUsageResponse getSchoolAdminTokenAnalytics(
            UUID schoolId, LocalDate from, LocalDate to, String granularity) {
        UUID finalSchoolId = resolveSchoolIdOrThrow(schoolId);
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);

        School school = schoolRepository.findById(finalSchoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + finalSchoolId));

        DashboardTokenSummaryProjection tokenSummary = messageRepository.sumTokensBetweenBySchoolId(
                range.fromDateTime(), range.toExclusive(), finalSchoolId);
        DashboardTokenBalanceSummaryProjection balanceSummary =
                userRepository.getTokenBalanceSummaryBySchoolId(finalSchoolId);
        Map<String, DashboardTokenTrendProjection> tokensByPeriod = toTokenPeriodMap(
                messageRepository.sumTokensByPeriodBySchoolId(
                        range.fromDateTime(), range.toExclusive(), bucket, finalSchoolId));

        List<DashboardTokenUsageResponse.TokenTrendPoint> trend = initializePeriods(range.from(), range.to(), bucket)
                .stream()
                .map(period -> {
                    DashboardTokenTrendProjection row = tokensByPeriod.get(period);
                    return DashboardTokenUsageResponse.TokenTrendPoint.builder()
                            .date(period)
                            .promptTokens(row == null ? 0L : nullSafe(row.getPromptTokens()))
                            .completionTokens(row == null ? 0L : nullSafe(row.getCompletionTokens()))
                            .totalTokens(row == null ? 0L : nullSafe(row.getTotalTokens()))
                            .build();
                })
                .toList();

        List<DashboardTokenUsageResponse.TopTokenUser> topUsers = messageRepository
                .findTopTokenUsersBySchoolId(range.fromDateTime(), range.toExclusive(), finalSchoolId, 10)
                .stream()
                .map(this::toTopTokenUser)
                .toList();

        int totalQuota = school.getTotalSchoolTokenQuota() != null ? school.getTotalSchoolTokenQuota() : 0;
        int unallocated = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        int allocated = Math.max(0, totalQuota - unallocated);
        double quotaUsagePercentage = totalQuota > 0
                ? Math.round((allocated * 100.0 / totalQuota) * 10.0) / 10.0
                : 0.0;

        long tokensUsedByStudents = messageRepository.sumTokensUsedBySchoolStudents(finalSchoolId);
        long tokensUsedByTeachers = messageRepository.sumTokensUsedBySchoolTeachers(finalSchoolId);
        long tokensUsedTotal = tokensUsedByStudents + tokensUsedByTeachers;
        double studentPercent = tokensUsedTotal > 0
                ? Math.round((tokensUsedByStudents * 100.0 / tokensUsedTotal) * 10.0) / 10.0
                : 0.0;
        double teacherPercent = tokensUsedTotal > 0
                ? Math.round((tokensUsedByTeachers * 100.0 / tokensUsedTotal) * 10.0) / 10.0
                : 0.0;

        int defaultDailyStudentQuota = school.getDefaultDailyStudentQuota() != null ? school.getDefaultDailyStudentQuota() : 5000;
        int defaultDailyTeacherQuota = school.getDefaultDailyTeacherQuota() != null ? school.getDefaultDailyTeacherQuota() : 5000;
        long pendingBoostRequests = countPendingBoostRequests(finalSchoolId);

        long topupPaidOrders = paymentOrderRepository != null
                ? paymentOrderRepository.countByDeletedAtIsNullAndSchool_IdAndStatusAndOrderType(
                        finalSchoolId, PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP)
                : 0L;
        long topupTokensPurchased = paymentOrderRepository != null
                ? toLong(paymentOrderRepository.sumTopupTokensBySchoolIdAndStatusAndOrderType(
                        finalSchoolId, PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP))
                : 0L;
        long topupAmountPaid = paymentOrderRepository != null
                ? toLong(paymentOrderRepository.sumAmountBySchoolIdAndStatusAndOrderType(
                        finalSchoolId, PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP))
                : 0L;

        DashboardTokenUsageResponse.SchoolTokenManagement schoolTokenManagement =
                DashboardTokenUsageResponse.SchoolTokenManagement.builder()
                        .schoolId(school.getId())
                        .schoolName(school.getName())
                        .schoolCode(school.getSchoolCode())
                        .packageType(school.getPackageType())
                        .status(school.getStatus())
                        .subscriptionStartDate(school.getSubscriptionStartDate())
                        .subscriptionEndDate(school.getSubscriptionEndDate())
                        .renewedForNextYear(school.getRenewedForNextYear())
                        .nextPackageType(school.getNextPackageType())
                        .totalSchoolTokenQuota(totalQuota)
                        .unallocatedTokenQuota(unallocated)
                        .allocatedTokens(allocated)
                        .quotaUsagePercentage(quotaUsagePercentage)
                        .tokensUsedByStudents(tokensUsedByStudents)
                        .tokensUsedByTeachers(tokensUsedByTeachers)
                        .tokensUsedTotal(tokensUsedTotal)
                        .studentTokenUsagePercentage(studentPercent)
                        .teacherTokenUsagePercentage(teacherPercent)
                        .defaultDailyStudentQuota(defaultDailyStudentQuota)
                        .defaultDailyTeacherQuota(defaultDailyTeacherQuota)
                        .pendingTokenBoostRequests(pendingBoostRequests)
                        .topupPaidOrders(topupPaidOrders)
                        .topupTokensPurchased(topupTokensPurchased)
                        .topupAmountPaid(topupAmountPaid)
                        .build();

        DashboardTokenSummaryProjection studentPeriodTokens = messageRepository.sumTokensBetweenBySchoolIdAndRole(
                range.fromDateTime(), range.toExclusive(), finalSchoolId, UserRole.SCHOOL_STUDENT.name());
        DashboardTokenSummaryProjection teacherPeriodTokens = messageRepository.sumTokensBetweenBySchoolIdAndRole(
                range.fromDateTime(), range.toExclusive(), finalSchoolId, UserRole.TEACHER.name());

        long periodPrompt = tokenSummary == null ? 0L : nullSafe(tokenSummary.getPromptTokens());
        long periodCompletion = tokenSummary == null ? 0L : nullSafe(tokenSummary.getCompletionTokens());
        long periodTotal = tokenSummary == null ? 0L : nullSafe(tokenSummary.getTotalTokens());

        long studentPeriodTotal = studentPeriodTokens == null ? 0L : nullSafe(studentPeriodTokens.getTotalTokens());
        long teacherPeriodTotal = teacherPeriodTokens == null ? 0L : nullSafe(teacherPeriodTokens.getTotalTokens());

        List<DashboardTokenUsageResponse.RoleTokenUsage> tokenUsageByRole = List.of(
                DashboardTokenUsageResponse.RoleTokenUsage.builder()
                        .role(UserRole.SCHOOL_STUDENT.name())
                        .users(userRepository.countActiveUsersBySchoolIdAndRole(finalSchoolId, UserRole.SCHOOL_STUDENT))
                        .promptTokens(studentPeriodTokens == null ? 0L : nullSafe(studentPeriodTokens.getPromptTokens()))
                        .completionTokens(studentPeriodTokens == null ? 0L : nullSafe(studentPeriodTokens.getCompletionTokens()))
                        .totalTokens(studentPeriodTotal)
                        .usagePercentage(periodTotal > 0
                                ? Math.round((studentPeriodTotal * 100.0 / periodTotal) * 10.0) / 10.0
                                : studentPercent)
                        .defaultDailyQuota(defaultDailyStudentQuota)
                        .build(),
                DashboardTokenUsageResponse.RoleTokenUsage.builder()
                        .role(UserRole.TEACHER.name())
                        .users(userRepository.countActiveUsersBySchoolIdAndRole(finalSchoolId, UserRole.TEACHER))
                        .promptTokens(teacherPeriodTokens == null ? 0L : nullSafe(teacherPeriodTokens.getPromptTokens()))
                        .completionTokens(teacherPeriodTokens == null ? 0L : nullSafe(teacherPeriodTokens.getCompletionTokens()))
                        .totalTokens(teacherPeriodTotal)
                        .usagePercentage(periodTotal > 0
                                ? Math.round((teacherPeriodTotal * 100.0 / periodTotal) * 10.0) / 10.0
                                : teacherPercent)
                        .defaultDailyQuota(defaultDailyTeacherQuota)
                        .build()
        );

        LocalDateTime todayStart = TokenDay.startOfToday();
        List<ClassroomTokenUsageProjection> topClassProjections = classroomRepository != null
                ? classroomRepository.findTopClassroomsByTokenUsage(finalSchoolId, todayStart, 5)
                : List.of();
        List<ClassroomTokenUsageSummaryDto> topClassrooms = (topClassProjections != null) ? topClassProjections.stream()
                .map(p -> ClassroomTokenUsageSummaryDto.builder()
                        .classroomId(p.getClassroomId())
                        .className(p.getClassName())
                        .classCode(p.getClassCode())
                        .gradeLevel(p.getGradeLevel())
                        .academicYear(p.getAcademicYear())
                        .status(p.getStatus())
                        .teacherName(p.getTeacherName())
                        .teacherUid(p.getTeacherUid())
                        .studentCount(p.getStudentCount() != null ? p.getStudentCount() : 0L)
                        .tokensUsedTotal(p.getTokensUsedTotal() != null ? p.getTokensUsedTotal() : 0L)
                        .tokensUsedToday(p.getTokensUsedToday() != null ? p.getTokensUsedToday() : 0L)
                        .percentageOfSchoolUsage(tokensUsedTotal > 0 && p.getTokensUsedTotal() != null
                                ? Math.round((p.getTokensUsedTotal() * 100.0 / tokensUsedTotal) * 10.0) / 10.0
                                : 0.0)
                        .build())
                .toList() : List.of();

        long estimatedCost = Math.round(periodTotal * TOKEN_UNIT_PRICE_VND);

        return DashboardTokenUsageResponse.builder()
                .summary(DashboardTokenUsageResponse.TokenSummary.builder()
                        .promptTokens(periodPrompt)
                        .completionTokens(periodCompletion)
                        .totalTokens(periodTotal)
                        .remainingTokens(unallocated)
                        .averageRemainingTokens(balanceSummary == null ? 0.0
                                : nullSafe(balanceSummary.getAverageRemainingTokens()))
                        .usersOutOfTokens(balanceSummary == null ? 0L
                                : nullSafe(balanceSummary.getUsersOutOfTokens()))
                        .estimatedCost(estimatedCost)
                        .build())
                .trend(trend)
                .tokenBalanceByTier(List.of())
                .topUsersByTokenUsage(topUsers)
                .schoolTokenManagement(schoolTokenManagement)
                .tokenUsageByRole(tokenUsageByRole)
                .topClassroomsByTokenUsage(topClassrooms)
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học với ID: " + classId));

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

        List<TeacherClassroomDashboardResponse.StudentAnalyticsItem> studentItems = new ArrayList<>();
        long totalAiMessages = 0L;
        if (!studentUids.isEmpty()) {
            List<User> students = userRepository.findAllById(studentUids);

            Map<UUID, StudentQuizSummaryProjection> quizStatsMap = quizSessionRepository
                    .findQuizSummariesByStudentUids(studentUids).stream()
                    .collect(Collectors.toMap(StudentQuizSummaryProjection::getStudentUid, Function.identity(), (k1, k2) -> k1));

            Map<UUID, Long> messageCountMap = messageRepository
                    .countMessagesGroupByStudentUids(studentUids).stream()
                    .collect(Collectors.toMap(StudentMessageCountProjection::getStudentUid, StudentMessageCountProjection::getMessageCount, (k1, k2) -> k1));
            for (User student : students) {
                StudentQuizSummaryProjection quizStat = quizStatsMap.get(student.getUid());
                long quizzes = quizStat != null && quizStat.getQuizzesAttempted() != null ? quizStat.getQuizzesAttempted() : 0L;
                Double avgScore = quizStat != null ? quizStat.getAverageScorePercentage() : 0.0;
                long aiMessages = messageCountMap.getOrDefault(student.getUid(), 0L);
                totalAiMessages += aiMessages;

                boolean isSameDay = !TokenDay.isNewDay(student.getLastTokenResetAt());
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


        double avgAiMessages = studentCount > 0 ? (double) totalAiMessages / studentCount : 0.0;

        com.historytalk.repository.dashboard.AssignmentSubmissionStatsProjection classStats =
                assignmentSubmissionRepository.getSubmissionStatsByClassroomId(classId);
        long classTotal = (classStats != null && classStats.getTotalSubmissions() != null) ? classStats.getTotalSubmissions() : 0L;
        long classSubmitted = (classStats != null && classStats.getSubmittedCount() != null) ? classStats.getSubmittedCount() : 0L;
        long classLate = (classStats != null && classStats.getLateSubmittedCount() != null) ? classStats.getLateSubmittedCount() : 0L;
        long classPending = (classStats != null)
                ? (classStats.getNotStartedCount() != null ? classStats.getNotStartedCount() : 0L)
                  + (classStats.getInProgressCount() != null ? classStats.getInProgressCount() : 0L)
                : 0L;

        double onTimeRate = classTotal > 0 ? Math.round((classSubmitted * 100.0 / classTotal) * 10.0) / 10.0 : 0.0;
        double lateRate = classTotal > 0 ? Math.round((classLate * 100.0 / classTotal) * 10.0) / 10.0 : 0.0;
        double pendingRate = classTotal > 0 ? Math.round((classPending * 100.0 / classTotal) * 10.0) / 10.0 : 0.0;

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
                        .onTimeRate(onTimeRate)
                        .lateRate(lateRate)
                        .pendingRate(pendingRate)
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh với ID: " + studentUid));

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

        boolean isSameDay = !TokenDay.isNewDay(student.getLastTokenResetAt());
        int bonusToday = isSameDay && student.getDailyBonusTokens() != null
                ? Math.max(0, student.getDailyBonusTokens())
                : 0;
        long queriedTokensUsedToday = messageRepository.sumTokensUsedByUserSince(studentUid, TokenDay.startOfToday());
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
        Map<UUID, Long> studentQuizQuestionCounts = batchCountQuestionsForSessions(recentSessions);
        List<StudentPersonalDashboardResponse.RecentQuizItem> recentQuizItems = recentSessions.stream()
                .map(s -> StudentPersonalDashboardResponse.RecentQuizItem.builder()
                        .sessionId(s.getSessionId())
                        .quizTitle(s.getQuiz() != null ? s.getQuiz().getTitle() : "")
                        .percentage(calculateSessionPercentage(s, studentQuizQuestionCounts))
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + userUid));

        Optional<UserTier> activeTier = userTierRepository.findCurrentActiveByUid(userUid, LocalDateTime.now(ZoneOffset.UTC));
        String currentTier = activeTier.map(ut -> ut.getTier() != null ? ut.getTier().getTitle() : "FREE").orElse("FREE");
        LocalDateTime tierExpiresAt = activeTier.map(UserTier::getEndTime).orElse(null);
        int currentTokens = user.getToken() != null ? user.getToken() : 0;

        long totalQuizzes = quizSessionRepository.countByUser_UidAndEndTimeIsNotNullAndDeletedAtIsNull(userUid);
        Double avgScore = quizSessionRepository.averageScorePercentageByUser(userUid);

        List<QuizSession> recentSessions = quizSessionRepository.findRecentCompletedByUser(userUid, PageRequest.of(0, 5));
        Map<UUID, Long> customerQuizQuestionCounts = batchCountQuestionsForSessions(recentSessions);
        List<CustomerPersonalDashboardResponse.RecentQuizItem> recentQuizItems = recentSessions.stream()
                .map(s -> CustomerPersonalDashboardResponse.RecentQuizItem.builder()
                        .sessionId(s.getSessionId())
                        .quizTitle(s.getQuiz() != null ? s.getQuiz().getTitle() : "")
                        .percentage(calculateSessionPercentage(s, customerQuizQuestionCounts))
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
        LocalDateTime oneYearAgo = LocalDateTime.now(ZoneOffset.UTC).minusYears(1);
        List<DashboardTopWrongQuestionProjection> wrongProjections =
                quizAnswerDetailRepository.findTopWrongQuestionsForDashboard(oneYearAgo, LocalDateTime.now(ZoneOffset.UTC), 10);
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

    private Map<UUID, Long> batchCountQuestionsForSessions(List<QuizSession> sessions) {
        if (questionRepository == null || sessions == null || sessions.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> quizIds = sessions.stream()
                .map(QuizSession::getQuiz)
                .filter(Objects::nonNull)
                .map(com.historytalk.entity.quiz.Quiz::getQuizId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (quizIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = questionRepository.countActiveByQuizIds(quizIds);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.stream().collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> ((Number) row[1]).longValue(),
                (a, b) -> a));
    }

    private double calculateSessionPercentage(QuizSession session, Map<UUID, Long> questionCounts) {
        if (session.getScore() == null) return 0.0;
        UUID quizId = session.getQuiz() != null ? session.getQuiz().getQuizId() : null;
        if (quizId != null && questionCounts != null && questionCounts.containsKey(quizId)) {
            long count = questionCounts.get(quizId);
            if (count == 0) return 0.0;
            return Math.round((session.getScore() * 100.0 / count) * 10.0) / 10.0;
        }
        return calculateSessionPercentage(session);
    }

    private double calculateSessionPercentage(QuizSession session) {
        if (session.getScore() == null) return 0.0;
        int questionCount = (session.getQuiz() != null && session.getQuiz().getQuestions() != null)
                ? session.getQuiz().getQuestions().size()
                : 10;
        if (questionCount == 0) return 0.0;
        return Math.round((session.getScore() * 100.0 / questionCount) * 10.0) / 10.0;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ClassroomTokenUsageSummaryDto> getClassroomsTokenUsage(
            UUID schoolId, Integer academicYear, Integer gradeLevel, String status, String search, Pageable pageable) {
        UUID resolvedSchoolId = schoolId;
        if (resolvedSchoolId == null) {
            UserPrincipal resolvedPrincipal = resolvePrincipal(null);
            resolvedSchoolId = resolvedPrincipal.getSchoolId();
        }
        if (resolvedSchoolId == null) {
            throw new InvalidRequestException("Tài khoản Quản trị viên chưa được liên kết với trường học nào.");
        }
        if (!schoolRepository.existsById(resolvedSchoolId)) {
            throw new ResourceNotFoundException("Không tìm thấy trường học với ID: " + resolvedSchoolId);
        }

        LocalDateTime todayStart = TokenDay.startOfToday();
        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        long schoolTotal = messageRepository.sumTokensUsedBySchoolStudents(resolvedSchoolId)
                + messageRepository.sumTokensUsedBySchoolTeachers(resolvedSchoolId);

        Pageable safePageable = (pageable != null)
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : PageRequest.of(0, 10);
        Page<ClassroomTokenUsageProjection> page = classroomRepository.findClassroomTokenUsageList(
                resolvedSchoolId, todayStart, academicYear, gradeLevel, status, trimmedSearch, safePageable);

        List<ClassroomTokenUsageSummaryDto> content = page.getContent().stream()
                .map(p -> ClassroomTokenUsageSummaryDto.builder()
                        .classroomId(p.getClassroomId())
                        .className(p.getClassName())
                        .classCode(p.getClassCode())
                        .gradeLevel(p.getGradeLevel())
                        .academicYear(p.getAcademicYear())
                        .status(p.getStatus())
                        .teacherName(p.getTeacherName())
                        .teacherUid(p.getTeacherUid())
                        .studentCount(p.getStudentCount() != null ? p.getStudentCount() : 0L)
                        .tokensUsedTotal(p.getTokensUsedTotal() != null ? p.getTokensUsedTotal() : 0L)
                        .tokensUsedToday(p.getTokensUsedToday() != null ? p.getTokensUsedToday() : 0L)
                        .percentageOfSchoolUsage(schoolTotal > 0 && p.getTokensUsedTotal() != null
                                ? Math.round((p.getTokensUsedTotal() * 100.0 / schoolTotal) * 10.0) / 10.0
                                : 0.0)
                        .build())
                .toList();

        return PaginatedResponse.<ClassroomTokenUsageSummaryDto>builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<StudentTokenUsageDetailDto> getClassroomStudentsTokenUsage(
            UUID classroomId, String search, Pageable pageable, UserPrincipal principal) {
        UserPrincipal resolved = resolvePrincipal(principal);
        UUID schoolId = resolved.getSchoolId();

        Classroom classroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học với ID: " + classroomId));

        if (resolved.getRole() == UserRole.TEACHER) {
            UUID teacherUid = UUID.fromString(resolved.getUid());
            if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(teacherUid)) {
                throw new AccessDeniedException("Bạn không được phân công phụ trách lớp học này!");
            }
        }

        School school = classroom.getSchool();
        int baseDailyLimit = (classroom.getDailyTokenQuota() != null && classroom.getDailyTokenQuota() > 0)
                ? classroom.getDailyTokenQuota()
                : ((school != null && school.getDefaultDailyStudentQuota() != null && school.getDefaultDailyStudentQuota() > 0)
                    ? school.getDefaultDailyStudentQuota()
                    : 1000);
        int schoolPool = (school != null && school.getUnallocatedTokenQuota() != null)
                ? Math.max(0, school.getUnallocatedTokenQuota())
                : 0;

        LocalDateTime todayStart = TokenDay.startOfToday();
        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Pageable safePageable = (pageable != null)
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : PageRequest.of(0, 20);
        Page<StudentTokenUsageDetailProjection> page = classStudentRepository.findStudentTokenUsageInClassroom(
                classroomId, todayStart, trimmedSearch, safePageable);

        List<StudentTokenUsageDetailDto> content = page.getContent().stream()
                .map(p -> {
                    long bonus = p.getDailyBonusTokens() != null ? p.getDailyBonusTokens() : 0L;
                    long usedToday = p.getTokensUsedToday() != null ? p.getTokensUsedToday() : 0L;
                    long effectiveDailyLimit = baseDailyLimit + bonus;
                    long remainingDailyLimit = Math.max(0, effectiveDailyLimit - usedToday);
                    long effectiveRemainingToday = Math.min(remainingDailyLimit, schoolPool);

                    return StudentTokenUsageDetailDto.builder()
                            .studentId(p.getStudentId())
                            .studentCode(p.getStudentCode())
                            .fullName(p.getFullName())
                            .userName(p.getUserName())
                            .email(p.getEmail())
                            .tokensUsedTotal(p.getTokensUsedTotal() != null ? p.getTokensUsedTotal() : 0L)
                            .tokensUsedToday(usedToday)
                            .dailyTokenLimit(baseDailyLimit)
                            .dailyBonusTokens(bonus)
                            .effectiveRemainingToday(effectiveRemainingToday)
                            .messageCount(p.getMessageCount() != null ? p.getMessageCount() : 0L)
                            .lastActiveAt(p.getLastActiveAt())
                            .build();
                })
                .toList();

        return PaginatedResponse.<StudentTokenUsageDetailDto>builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherClassesOverviewResponse getTeacherClassesOverview(UserPrincipal principal) {
        UserPrincipal resolved = resolvePrincipal(principal);
        UUID teacherUid = UUID.fromString(resolved.getUid());
        UUID schoolId = resolved.getSchoolId();

        User teacher = userRepository.findById(teacherUid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo viên với ID: " + teacherUid));

        LocalDateTime todayStart = TokenDay.startOfToday();
        List<ClassroomTokenUsageProjection> projections = classroomRepository.findClassroomsTokenUsageForTeacher(
                schoolId, teacherUid, todayStart);

        long totalTokensUsed = (projections != null) ? projections.stream()
                .mapToLong(p -> p.getTokensUsedTotal() != null ? p.getTokensUsedTotal() : 0L)
                .sum() : 0L;

        long totalStudents = (projections != null) ? projections.stream()
                .mapToLong(p -> p.getStudentCount() != null ? p.getStudentCount() : 0L)
                .sum() : 0L;

        List<ClassroomTokenUsageSummaryDto> classroomDtos = (projections != null) ? projections.stream()
                .map(p -> ClassroomTokenUsageSummaryDto.builder()
                        .classroomId(p.getClassroomId())
                        .className(p.getClassName())
                        .classCode(p.getClassCode())
                        .gradeLevel(p.getGradeLevel())
                        .academicYear(p.getAcademicYear())
                        .status(p.getStatus())
                        .teacherName(teacher.getFullName())
                        .teacherUid(teacherUid)
                        .studentCount(p.getStudentCount() != null ? p.getStudentCount() : 0L)
                        .tokensUsedTotal(p.getTokensUsedTotal() != null ? p.getTokensUsedTotal() : 0L)
                        .tokensUsedToday(p.getTokensUsedToday() != null ? p.getTokensUsedToday() : 0L)
                        .percentageOfSchoolUsage(totalTokensUsed > 0 && p.getTokensUsedTotal() != null
                                ? Math.round((p.getTokensUsedTotal() * 100.0 / totalTokensUsed) * 10.0) / 10.0
                                : 0.0)
                        .build())
                .toList() : new ArrayList<>();

        return TeacherClassesOverviewResponse.builder()
                .teacherUid(teacherUid)
                .teacherName(teacher.getFullName())
                .totalClassroomsManaged(projections != null ? projections.size() : 0)
                .totalStudentsManaged(totalStudents)
                .totalTokensUsedByAllClasses(totalTokensUsed)
                .classrooms(classroomDtos)
                .build();
    }

    private UUID resolveSchoolIdOrThrow(UUID schoolId) {
        UUID resolvedSchoolId = schoolId;
        if (resolvedSchoolId == null) {
            UserPrincipal resolvedPrincipal = resolvePrincipal(null);
            resolvedSchoolId = resolvedPrincipal.getSchoolId();
        }
        if (resolvedSchoolId == null) {
            throw new InvalidRequestException("Tài khoản Quản trị viên chưa được liên kết với trường học nào.");
        }
        return resolvedSchoolId;
    }

    private void validateSchoolExists(UUID schoolId) {
        if (!schoolRepository.existsById(schoolId)) {
            throw new ResourceNotFoundException("Không tìm thấy trường học với ID: " + schoolId);
        }
    }

    private long countPendingBoostRequests(UUID schoolId) {
        if (tokenBoostRequestRepository == null) {
            return 0L;
        }
        return tokenBoostRequestRepository.countBySchool_IdAndStatus(
                schoolId, com.historytalk.entity.enums.TokenBoostRequestStatus.PENDING)
                + tokenBoostRequestRepository.countBySchool_IdAndStatus(
                schoolId, com.historytalk.entity.enums.TokenBoostRequestStatus.PENDING_ADMIN);
    }

    private SchoolAdminDashboardResponse.AssignmentStats buildSchoolAssignmentStats(UUID schoolId) {
        AssignmentSubmissionStatsProjection stats = assignmentSubmissionRepository != null
                ? assignmentSubmissionRepository.getSubmissionStatsBySchoolId(schoolId)
                : null;
        long totalSubmissions = (stats != null && stats.getTotalSubmissions() != null) ? stats.getTotalSubmissions() : 0L;
        long submittedCount = (stats != null && stats.getSubmittedCount() != null) ? stats.getSubmittedCount() : 0L;
        long lateCount = (stats != null && stats.getLateSubmittedCount() != null) ? stats.getLateSubmittedCount() : 0L;
        long notStartedCount = (stats != null && stats.getNotStartedCount() != null) ? stats.getNotStartedCount() : 0L;

        double completedRate = totalSubmissions > 0
                ? Math.round((submittedCount * 100.0 / totalSubmissions) * 10.0) / 10.0
                : 0.0;
        double lateSubmittedRate = totalSubmissions > 0
                ? Math.round((lateCount * 100.0 / totalSubmissions) * 10.0) / 10.0
                : 0.0;
        double notStartedRate = totalSubmissions > 0
                ? Math.round((notStartedCount * 100.0 / totalSubmissions) * 10.0) / 10.0
                : 0.0;
        return SchoolAdminDashboardResponse.AssignmentStats.builder()
                .totalSubmissions(totalSubmissions)
                .completedRate(completedRate)
                .lateSubmittedRate(lateSubmittedRate)
                .notStartedRate(notStartedRate)
                .build();
    }

    private DashboardOverviewResponse.UserOverview buildSchoolUserOverview(
            UUID schoolId, LocalDateTime todayStart, LocalDateTime tomorrowStart, LocalDateTime monthStart) {
        return DashboardOverviewResponse.UserOverview.builder()
                .total(userRepository.countAllUsersBySchoolId(schoolId))
                .active(userRepository.countActiveUsersBySchoolId(schoolId))
                .inactive(userRepository.countInactiveUsersBySchoolId(schoolId))
                .deleted(userRepository.countDeletedUsersBySchoolId(schoolId))
                .newToday(userRepository.countCreatedBetweenBySchoolId(schoolId, todayStart, tomorrowStart))
                .newThisMonth(userRepository.countCreatedBetweenBySchoolId(schoolId, monthStart, tomorrowStart))
                .build();
    }

    private DashboardOverviewResponse.RoleOverview buildSchoolRoleOverview(UUID schoolId) {
        return DashboardOverviewResponse.RoleOverview.builder()
                .customers(0L)
                .contentAdmins(0L)
                .systemAdmins(0L)
                .schoolAdmins(userRepository.countActiveUsersBySchoolIdAndRole(schoolId, UserRole.SCHOOL_ADMIN))
                .teachers(userRepository.countBySchoolIdAndRole(schoolId, UserRole.TEACHER))
                .schoolStudents(userRepository.countBySchoolIdAndRole(schoolId, UserRole.SCHOOL_STUDENT))
                .build();
    }

    private DashboardContentSummaryResponse buildSchoolContentSummaryInternal(UUID schoolId) {
        DashboardContentSummaryResponse.InventoryStats localContexts =
                DashboardContentSummaryResponse.InventoryStats.builder()
                        .total(historicalContextRepository != null ? historicalContextRepository.countBySchoolIdCurrent(schoolId) : 0L)
                        .published(historicalContextRepository != null ? historicalContextRepository.countBySchoolIdPublished(schoolId) : 0L)
                        .active(historicalContextRepository != null ? historicalContextRepository.countBySchoolIdActive(schoolId) : 0L)
                        .build();
        DashboardContentSummaryResponse.InventoryStats localCharacters =
                DashboardContentSummaryResponse.InventoryStats.builder()
                        .total(characterRepository != null ? characterRepository.countBySchoolIdCurrent(schoolId) : 0L)
                        .published(characterRepository != null ? characterRepository.countBySchoolIdPublished(schoolId) : 0L)
                        .active(characterRepository != null ? characterRepository.countBySchoolIdActive(schoolId) : 0L)
                        .build();
        DashboardContentSummaryResponse.DocumentStats localDocuments =
                DashboardContentSummaryResponse.DocumentStats.builder()
                        .total(documentRepository != null ? documentRepository.countBySchoolIdCurrent(schoolId) : 0L)
                        .active(documentRepository != null ? documentRepository.countBySchoolIdActive(schoolId) : 0L)
                        .build();

        return DashboardContentSummaryResponse.builder()
                .historicalContexts(localContexts)
                .characters(localCharacters)
                .documents(localDocuments)
                .localHistoricalContexts(localContexts)
                .localCharacters(localCharacters)
                .build();
    }

    private DashboardOverviewResponse.ChatOverview buildSchoolChatOverview(
            UUID schoolId, LocalDateTime todayStart, LocalDateTime tomorrowStart) {
        return DashboardOverviewResponse.ChatOverview.builder()
                .sessions(chatSessionRepository != null ? chatSessionRepository.countCurrentBySchoolId(schoolId) : 0L)
                .messages(messageRepository.countCurrentBySchoolId(schoolId))
                .messagesToday(messageRepository.countCreatedBetweenBySchoolId(todayStart, tomorrowStart, schoolId))
                .build();
    }

    private List<DashboardOverviewResponse.TopCharacterOverview> buildSchoolTopLocalCharacters(UUID schoolId) {
        List<DashboardTopCharacterProjection> rows = messageRepository.findTopLocalCharactersBySchoolId(schoolId, 10);
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(c -> DashboardOverviewResponse.TopCharacterOverview.builder()
                        .characterId(c.getCharacterId())
                        .name(c.getName())
                        .title(c.getTitle() != null ? c.getTitle() : "")
                        .imageUrl(c.getImageUrl() != null ? c.getImageUrl() : "")
                        .totalMessages(nullSafe(c.getTotalMessages()))
                        .userMessages(nullSafe(c.getUserMessages()))
                        .aiMessages(nullSafe(c.getAiMessages()))
                        .build())
                .toList();
    }

    private DashboardQuizAnalyticsResponse.QuizSummary buildSchoolQuizSummary(UUID schoolId) {
        long totalQuizzes = quizRepository != null ? quizRepository.countBySchoolIdAllQuizzes(schoolId) : 0L;
        long publishedQuizzes = quizRepository != null ? quizRepository.countBySchoolIdPublishedQuizzes(schoolId) : 0L;
        long draftQuizzes = quizRepository != null ? quizRepository.countBySchoolIdDraftQuizzes(schoolId) : 0L;
        long deletedQuizzes = quizRepository != null ? quizRepository.countBySchoolIdDeletedQuizzes(schoolId) : 0L;
        long startedSessions = quizSessionRepository != null ? quizSessionRepository.countStartedSessionsBySchoolId(schoolId) : 0L;
        long completedSessions = quizSessionRepository != null ? quizSessionRepository.countCompletedSessionsBySchoolId(schoolId) : 0L;
        double avgScore = quizSessionRepository != null
                ? nullSafe(quizSessionRepository.averageScorePercentageBySchoolId(schoolId))
                : 0.0;

        return DashboardQuizAnalyticsResponse.QuizSummary.builder()
                .totalQuizzes(totalQuizzes)
                .publishedQuizzes(publishedQuizzes)
                .draftQuizzes(draftQuizzes)
                .deletedQuizzes(deletedQuizzes)
                .startedSessions(startedSessions)
                .completedSessions(completedSessions)
                .completionRate(percentage(completedSessions, startedSessions))
                .averageScorePercentage(avgScore)
                .build();
    }

    private DateRange resolveDateRange(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(DEFAULT_RANGE_DAYS) : from;

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new InvalidRequestException("từ (from) phải trước hoặc bằng đến (to)");
        }
        if (resolvedFrom.plusDays(MAX_RANGE_DAYS).isBefore(resolvedTo)) {
            throw new InvalidRequestException("Khoảng thời gian không được vượt quá " + MAX_RANGE_DAYS + " days");
        }

        return new DateRange(
                resolvedFrom,
                resolvedTo,
                resolvedFrom.atStartOfDay(),
                resolvedTo.plusDays(1).atStartOfDay()
        );
    }

    private String normalizeGranularity(String granularity) {
        if (granularity == null || granularity.isBlank()) {
            return "day";
        }
        String normalized = granularity.toLowerCase(Locale.ROOT).trim();
        if (!List.of("day", "week", "month", "year").contains(normalized)) {
            throw new InvalidRequestException("granularity (đơn vị thời gian) phải là day, week, month hoặc year");
        }
        return normalized;
    }

    private List<String> initializePeriods(LocalDate from, LocalDate to, String bucket) {
        List<String> periods = new ArrayList<>();
        if ("year".equals(bucket)) {
            for (int year = from.getYear(); year <= to.getYear(); year++) {
                periods.add(LocalDate.of(year, 1, 1).format(PERIOD_FORMATTER));
            }
            return periods;
        }

        if ("month".equals(bucket)) {
            YearMonth cursor = YearMonth.from(from);
            YearMonth end = YearMonth.from(to);
            while (!cursor.isAfter(end)) {
                periods.add(cursor.atDay(1).format(PERIOD_FORMATTER));
                cursor = cursor.plusMonths(1);
            }
            return periods;
        }

        if ("week".equals(bucket)) {
            LocalDate cursor = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate end = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            while (!cursor.isAfter(end)) {
                periods.add(cursor.format(PERIOD_FORMATTER));
                cursor = cursor.plusWeeks(1);
            }
            return periods;
        }

        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            periods.add(cursor.format(PERIOD_FORMATTER));
            cursor = cursor.plusDays(1);
        }
        return periods;
    }

    private Map<String, Long> toPeriodMap(List<DashboardPeriodCountProjection> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (DashboardPeriodCountProjection row : rows) {
            result.put(row.getPeriod(), row.getCount() == null ? 0L : row.getCount());
        }
        return result;
    }

    private Map<String, DashboardQuizTrendProjection> toQuizPeriodMap(List<DashboardQuizTrendProjection> rows) {
        Map<String, DashboardQuizTrendProjection> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (DashboardQuizTrendProjection row : rows) {
            result.put(row.getPeriod(), row);
        }
        return result;
    }

    private Map<String, DashboardTokenTrendProjection> toTokenPeriodMap(List<DashboardTokenTrendProjection> rows) {
        Map<String, DashboardTokenTrendProjection> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (DashboardTokenTrendProjection row : rows) {
            result.put(row.getPeriod(), row);
        }
        return result;
    }

    private DashboardQuizAnalyticsResponse.TopQuiz toTopQuiz(DashboardTopQuizProjection row) {
        return DashboardQuizAnalyticsResponse.TopQuiz.builder()
                .quizId(row.getQuizId())
                .title(row.getTitle())
                .level(row.getLevel())
                .startedSessions(nullSafe(row.getStartedSessions()))
                .completedSessions(nullSafe(row.getCompletedSessions()))
                .averageScorePercentage(nullSafe(row.getAverageScorePercentage()))
                .build();
    }

    private DashboardQuizAnalyticsResponse.TopWrongQuestion toTopWrongQuestion(
            DashboardTopWrongQuestionProjection row) {
        return DashboardQuizAnalyticsResponse.TopWrongQuestion.builder()
                .questionId(row.getQuestionId())
                .questionContent(row.getQuestionContent())
                .quizId(row.getQuizId())
                .quizTitle(row.getQuizTitle())
                .wrongAnswers(nullSafe(row.getWrongAnswers()))
                .totalAnswers(nullSafe(row.getTotalAnswers()))
                .wrongRate(nullSafe(row.getWrongRate()) * 100.0)
                .build();
    }

    private DashboardTokenUsageResponse.TopTokenUser toTopTokenUser(DashboardTopTokenUserProjection row) {
        return DashboardTokenUsageResponse.TopTokenUser.builder()
                .uid(row.getUid())
                .userName(row.getUserName())
                .email(row.getEmail())
                .tierId(row.getTierId())
                .tierTitle(row.getTierTitle())
                .promptTokens(nullSafe(row.getPromptTokens()))
                .completionTokens(nullSafe(row.getCompletionTokens()))
                .totalTokens(nullSafe(row.getTotalTokens()))
                .remainingTokens(nullSafe(row.getRemainingTokens()))
                .build();
    }

    private long toLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    private double nullSafe(Double value) {
        return value == null ? 0.0 : value;
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return numerator * 100.0 / denominator;
    }

    private record DateRange(LocalDate from, LocalDate to, LocalDateTime fromDateTime, LocalDateTime toExclusive) {
    }

}
