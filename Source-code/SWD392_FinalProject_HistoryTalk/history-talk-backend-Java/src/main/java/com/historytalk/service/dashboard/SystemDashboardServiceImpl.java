package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.DashboardChatActivityResponse;
import com.historytalk.dto.dashboard.DashboardContentSummaryResponse;
import com.historytalk.dto.dashboard.DashboardOverviewResponse;
import com.historytalk.dto.dashboard.DashboardPaymentResponse;
import com.historytalk.dto.dashboard.DashboardQuizAnalyticsResponse;
import com.historytalk.dto.dashboard.DashboardRevenueResponse;
import com.historytalk.dto.dashboard.DashboardSystemHealthResponse;
import com.historytalk.dto.dashboard.DashboardTierAnalyticsResponse;
import com.historytalk.dto.dashboard.DashboardTokenUsageResponse;
import com.historytalk.dto.dashboard.DashboardUserAnalyticsResponse;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.PaymentTransactionStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.QuizAnswerDetailRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.dashboard.DashboardPeriodRevenueProjection;
import com.historytalk.repository.dashboard.DashboardPeriodCountProjection;
import com.historytalk.repository.dashboard.DashboardQuizTrendProjection;
import com.historytalk.repository.dashboard.DashboardStatusCountProjection;
import com.historytalk.repository.dashboard.DashboardTokenBalanceByTierProjection;
import com.historytalk.repository.dashboard.DashboardTokenBalanceSummaryProjection;
import com.historytalk.repository.dashboard.DashboardTokenSummaryProjection;
import com.historytalk.repository.dashboard.DashboardTokenTrendProjection;
import com.historytalk.repository.dashboard.DashboardTierRevenueProjection;
import com.historytalk.repository.dashboard.DashboardTierUsersProjection;
import com.historytalk.repository.dashboard.DashboardTopQuizProjection;
import com.historytalk.repository.dashboard.DashboardTopTokenUserProjection;
import com.historytalk.repository.dashboard.DashboardTopWrongQuestionProjection;
import com.historytalk.repository.dashboard.DashboardTransactionTrendProjection;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.PaymentTransactionRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.payment.UserTierRepository;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemDashboardServiceImpl implements SystemDashboardService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int DEFAULT_RANGE_DAYS = 29;
    private static final int MAX_RANGE_DAYS = 180;

    private final UserRepository userRepository;
    private final HistoricalContextRepository historicalContextRepository;
    private final CharacterRepository characterRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final MessageRepository messageRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final TierRepository tierRepository;
    private final UserTierRepository userTierRepository;
    private final QuizRepository quizRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final QuizAnswerDetailRepository quizAnswerDetailRepository;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<HealthEndpoint> healthEndpointProvider;

    @Override
    public DashboardOverviewResponse getOverview() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        DashboardSystemHealthResponse health = getSystemHealth();

        return DashboardOverviewResponse.builder()
                .users(DashboardOverviewResponse.UserOverview.builder()
                        .total(userRepository.countAllUsers())
                        .active(userRepository.countActiveUsers())
                        .inactive(userRepository.countInactiveUsers())
                        .deleted(userRepository.countDeletedUsers())
                        .newToday(userRepository.countCreatedBetween(todayStart, tomorrowStart))
                        .newThisMonth(userRepository.countCreatedBetween(monthStart, tomorrowStart))
                        .build())
                .roles(buildRoleOverview())
                .content(DashboardOverviewResponse.ContentOverview.builder()
                        .historicalContexts(historicalContextRepository.countCurrent())
                        .publishedHistoricalContexts(historicalContextRepository.countPublished())
                        .characters(characterRepository.countCurrent())
                        .publishedCharacters(characterRepository.countPublished())
                        .documents(documentRepository.countCurrent())
                        .build())
                .chat(DashboardOverviewResponse.ChatOverview.builder()
                        .sessions(chatSessionRepository.countCurrent())
                        .messages(messageRepository.countCurrent())
                        .messagesToday(messageRepository.countCreatedBetween(todayStart, tomorrowStart))
                        .build())
                .systemHealth(DashboardOverviewResponse.SystemHealthOverview.builder()
                        .status(health.getStatus())
                        .lastCheckedAt(health.getLastCheckedAt())
                        .build())
                .build();
    }

    @Override
    public DashboardUserAnalyticsResponse getUserAnalytics(LocalDate from, LocalDate to, String granularity) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);

        Map<String, Long> newUsersByPeriod = toPeriodMap(
                userRepository.countNewUsersByPeriod(range.fromDateTime(), range.toExclusive(), bucket));
        Map<String, Long> activeUsersByPeriod = toPeriodMap(
                userRepository.countActiveUsersByPeriod(range.fromDateTime(), range.toExclusive(), bucket));

        List<DashboardUserAnalyticsResponse.UserTrendPoint> trend = initializePeriods(range.from(), range.to(), bucket)
                .stream()
                .map(period -> DashboardUserAnalyticsResponse.UserTrendPoint.builder()
                        .date(period)
                        .newUsers(newUsersByPeriod.getOrDefault(period, 0L))
                        .activeUsers(activeUsersByPeriod.getOrDefault(period, 0L))
                        .build())
                .toList();

        return DashboardUserAnalyticsResponse.builder()
                .summary(DashboardUserAnalyticsResponse.UserSummary.builder()
                        .total(userRepository.countAllUsers())
                        .active(userRepository.countActiveUsers())
                        .inactive(userRepository.countInactiveUsers())
                        .deleted(userRepository.countDeletedUsers())
                        .recentlyActive(userRepository.countRecentlyActiveUsers(LocalDateTime.now().minusDays(7)))
                        .build())
                .byRole(List.of(
                        roleCount(UserRole.CUSTOMER),
                        roleCount(UserRole.CONTENT_ADMIN),
                        roleCount(UserRole.SYSTEM_ADMIN)
                ))
                .trend(trend)
                .build();
    }

    @Override
    public DashboardContentSummaryResponse getContentSummary() {
        return DashboardContentSummaryResponse.builder()
                .historicalContexts(DashboardContentSummaryResponse.InventoryStats.builder()
                        .total(historicalContextRepository.countCurrent())
                        .published(historicalContextRepository.countPublished())
                        .active(historicalContextRepository.countActive())
                        .build())
                .characters(DashboardContentSummaryResponse.InventoryStats.builder()
                        .total(characterRepository.countCurrent())
                        .published(characterRepository.countPublished())
                        .active(characterRepository.countActive())
                        .build())
                .documents(DashboardContentSummaryResponse.DocumentStats.builder()
                        .total(documentRepository.countCurrent())
                        .active(documentRepository.countActive())
                        .build())
                .build();
    }

    @Override
    public DashboardChatActivityResponse getChatActivity(LocalDate from, LocalDate to, String granularity) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        Map<String, Long> sessionsByPeriod = toPeriodMap(
                chatSessionRepository.countSessionsByPeriod(range.fromDateTime(), range.toExclusive(), bucket));
        Map<String, Long> messagesByPeriod = toPeriodMap(
                messageRepository.countMessagesByPeriod(range.fromDateTime(), range.toExclusive(), bucket));

        List<DashboardChatActivityResponse.ChatTrendPoint> trend = initializePeriods(range.from(), range.to(), bucket)
                .stream()
                .map(period -> DashboardChatActivityResponse.ChatTrendPoint.builder()
                        .date(period)
                        .sessions(sessionsByPeriod.getOrDefault(period, 0L))
                        .messages(messagesByPeriod.getOrDefault(period, 0L))
                        .build())
                .toList();

        return DashboardChatActivityResponse.builder()
                .summary(DashboardChatActivityResponse.ChatSummary.builder()
                        .sessions(chatSessionRepository.countCurrent())
                        .activeSessions(chatSessionRepository.countActive())
                        .messages(messageRepository.countCurrent())
                        .userMessages(messageRepository.countByAiFlag(false))
                        .aiMessages(messageRepository.countByAiFlag(true))
                        .sessionsToday(chatSessionRepository.countCreatedBetween(todayStart, tomorrowStart))
                        .messagesToday(messageRepository.countCreatedBetween(todayStart, tomorrowStart))
                        .build())
                .trend(trend)
                .build();
    }

    @Override
    public DashboardSystemHealthResponse getSystemHealth() {
        return DashboardSystemHealthResponse.builder()
                .status(resolveHealthStatus())
                .uptime(formatUptime())
                .jvmMemoryUsed(sumMetricValue("jvm.memory.used"))
                .jvmMemoryMax(sumMetricValue("jvm.memory.max"))
                .httpRequestCount(sumMetricCount("http.server.requests"))
                .httpErrorCount(sumHttpServerErrorCount())
                .lastCheckedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public DashboardRevenueResponse getRevenue(LocalDate from, LocalDate to, String granularity) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime yearStart = today.withDayOfYear(1).atStartOfDay();
        LocalDateTime nextYearStart = today.plusYears(1).withDayOfYear(1).atStartOfDay();

        long totalRevenue = toLong(paymentOrderRepository.sumAmountByStatus(PaymentOrderStatus.PAID));
        long paidOrders = paymentOrderRepository.countByDeletedAtIsNullAndStatus(PaymentOrderStatus.PAID);
        long averageOrderValue = paidOrders == 0 ? 0 : totalRevenue / paidOrders;

        Map<String, DashboardPeriodRevenueProjection> revenueByPeriod = toRevenuePeriodMap(
                paymentOrderRepository.sumPaidRevenueByPeriod(range.fromDateTime(), range.toExclusive(), bucket));

        List<DashboardRevenueResponse.RevenueTrendPoint> trend = initializePeriods(range.from(), range.to(), bucket)
                .stream()
                .map(period -> {
                    DashboardPeriodRevenueProjection row = revenueByPeriod.get(period);
                    return DashboardRevenueResponse.RevenueTrendPoint.builder()
                            .date(period)
                            .revenue(row == null ? 0L : toLong(row.getRevenue()))
                            .paidOrders(row == null ? 0L : nullSafe(row.getPaidOrders()))
                            .build();
                })
                .toList();

        return DashboardRevenueResponse.builder()
                .summary(DashboardRevenueResponse.RevenueSummary.builder()
                        .totalRevenue(totalRevenue)
                        .revenueToday(toLong(paymentOrderRepository.sumAmountByStatusAndPaidAtBetween(
                                PaymentOrderStatus.PAID, todayStart, tomorrowStart)))
                        .revenueThisMonth(toLong(paymentOrderRepository.sumAmountByStatusAndPaidAtBetween(
                                PaymentOrderStatus.PAID, monthStart, tomorrowStart)))
                        .revenueThisYear(toLong(paymentOrderRepository.sumAmountByStatusAndPaidAtBetween(
                                PaymentOrderStatus.PAID, yearStart, nextYearStart)))
                        .paidOrders(paidOrders)
                        .averageOrderValue(averageOrderValue)
                        .build())
                .ordersByStatus(buildOrderStatusCounts())
                .revenueByTier(paymentOrderRepository.sumPaidRevenueByTier(range.fromDateTime(), range.toExclusive())
                        .stream()
                        .map(this::toRevenueByTier)
                        .toList())
                .trend(trend)
                .build();
    }

    @Override
    public DashboardPaymentResponse getPayments(LocalDate from, LocalDate to, String granularity) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        Map<String, DashboardTransactionTrendProjection> transactionsByPeriod = toTransactionPeriodMap(
                paymentTransactionRepository.countTransactionsByPeriod(range.fromDateTime(), range.toExclusive(), bucket));

        long pendingOrders = paymentOrderRepository.countByDeletedAtIsNullAndStatus(PaymentOrderStatus.PENDING);
        long paidOrders = paymentOrderRepository.countByDeletedAtIsNullAndStatus(PaymentOrderStatus.PAID);
        long cancelledOrders = paymentOrderRepository.countByDeletedAtIsNullAndStatus(PaymentOrderStatus.CANCELLED);
        long expiredOrders = paymentOrderRepository.countByDeletedAtIsNullAndStatus(PaymentOrderStatus.EXPIRED);
        long failedOrders = paymentOrderRepository.countByDeletedAtIsNullAndStatus(PaymentOrderStatus.FAILED);

        List<DashboardPaymentResponse.TransactionTrendPoint> trend = initializePeriods(range.from(), range.to(), bucket)
                .stream()
                .map(period -> {
                    DashboardTransactionTrendProjection row = transactionsByPeriod.get(period);
                    return DashboardPaymentResponse.TransactionTrendPoint.builder()
                            .date(period)
                            .success(row == null ? 0L : nullSafe(row.getSuccess()))
                            .failed(row == null ? 0L : nullSafe(row.getFailed()))
                            .build();
                })
                .toList();

        return DashboardPaymentResponse.builder()
                .summary(DashboardPaymentResponse.PaymentSummary.builder()
                        .totalOrders(pendingOrders + paidOrders + cancelledOrders + expiredOrders + failedOrders)
                        .pendingOrders(pendingOrders)
                        .paidOrders(paidOrders)
                        .cancelledOrders(cancelledOrders)
                        .expiredOrders(expiredOrders)
                        .failedOrders(failedOrders)
                        .successfulTransactions(paymentTransactionRepository.countByDeletedAtIsNullAndStatus(
                                PaymentTransactionStatus.SUCCESS))
                        .failedTransactions(paymentTransactionRepository.countByDeletedAtIsNullAndStatus(
                                PaymentTransactionStatus.FAILED))
                        .build())
                .transactionTrend(trend)
                .build();
    }

    @Override
    public DashboardTierAnalyticsResponse getTiers(LocalDate from, LocalDate to, String granularity) {
        DateRange range = resolveDateRange(from, to);
        LocalDateTime now = LocalDateTime.now();
        long currentPaidUsers = tierRepository.countCurrentPaidCustomers();
        long currentFreeUsers = tierRepository.countCurrentFreeCustomers();
        long customerBase = currentPaidUsers + currentFreeUsers;

        return DashboardTierAnalyticsResponse.builder()
                .summary(DashboardTierAnalyticsResponse.TierSummary.builder()
                        .activeTiers(tierRepository.countByIsActiveTrueAndDeletedAtIsNull())
                        .currentPaidUsers(currentPaidUsers)
                        .currentFreeUsers(currentFreeUsers)
                        .activeSubscriptions(userTierRepository.countActiveSubscriptions(now))
                        .expiringSoonSubscriptions(userTierRepository.countExpiringSoonSubscriptions(
                                now, now.plusDays(7)))
                        .freeToPaidConversionRate(percentage(
                                paymentOrderRepository.countDistinctPaidCustomers(), customerBase))
                        .build())
                .usersByTier(tierRepository.countCustomerUsersByTier()
                        .stream()
                        .map(this::toUsersByTier)
                        .toList())
                .purchasesByTier(paymentOrderRepository.sumPaidRevenueByTier(range.fromDateTime(), range.toExclusive())
                        .stream()
                        .map(this::toPurchasesByTier)
                        .toList())
                .build();
    }

    @Override
    public DashboardQuizAnalyticsResponse getQuiz(LocalDate from, LocalDate to, String granularity) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        long startedSessions = quizSessionRepository.countStartedSessions();
        long completedSessions = quizSessionRepository.countCompletedSessions();

        Map<String, DashboardQuizTrendProjection> sessionsByPeriod = toQuizPeriodMap(
                quizSessionRepository.countSessionsByPeriod(range.fromDateTime(), range.toExclusive(), bucket));

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

        return DashboardQuizAnalyticsResponse.builder()
                .summary(DashboardQuizAnalyticsResponse.QuizSummary.builder()
                        .totalQuizzes(quizRepository.countAllQuizzes())
                        .publishedQuizzes(quizRepository.countPublishedQuizzes())
                        .draftQuizzes(quizRepository.countDraftQuizzes())
                        .deletedQuizzes(quizRepository.countDeletedQuizzes())
                        .startedSessions(startedSessions)
                        .completedSessions(completedSessions)
                        .completionRate(percentage(completedSessions, startedSessions))
                        .averageScorePercentage(nullSafe(quizSessionRepository.averageScorePercentage()))
                        .build())
                .sessionsTrend(trend)
                .topQuizzes(quizRepository.findTopQuizzesForDashboard(
                                range.fromDateTime(), range.toExclusive(), 5)
                        .stream()
                        .map(this::toTopQuiz)
                        .toList())
                .topWrongQuestions(quizAnswerDetailRepository.findTopWrongQuestionsForDashboard(
                                range.fromDateTime(), range.toExclusive(), 5)
                        .stream()
                        .map(this::toTopWrongQuestion)
                        .toList())
                .build();
    }

    @Override
    public DashboardTokenUsageResponse getTokens(LocalDate from, LocalDate to, String granularity) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);

        DashboardTokenSummaryProjection tokenSummary = messageRepository.sumTokensBetween(
                range.fromDateTime(), range.toExclusive());
        DashboardTokenBalanceSummaryProjection balanceSummary = userRepository.getTokenBalanceSummary();
        Map<String, DashboardTokenTrendProjection> tokensByPeriod = toTokenPeriodMap(
                messageRepository.sumTokensByPeriod(range.fromDateTime(), range.toExclusive(), bucket));

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

        return DashboardTokenUsageResponse.builder()
                .summary(DashboardTokenUsageResponse.TokenSummary.builder()
                        .promptTokens(tokenSummary == null ? 0L : nullSafe(tokenSummary.getPromptTokens()))
                        .completionTokens(tokenSummary == null ? 0L : nullSafe(tokenSummary.getCompletionTokens()))
                        .totalTokens(tokenSummary == null ? 0L : nullSafe(tokenSummary.getTotalTokens()))
                        .remainingTokens(balanceSummary == null ? 0L : nullSafe(balanceSummary.getRemainingTokens()))
                        .averageRemainingTokens(balanceSummary == null ? 0.0
                                : nullSafe(balanceSummary.getAverageRemainingTokens()))
                        .usersOutOfTokens(balanceSummary == null ? 0L
                                : nullSafe(balanceSummary.getUsersOutOfTokens()))
                        .estimatedCost(0L)
                        .build())
                .trend(trend)
                .tokenBalanceByTier(userRepository.countTokenBalanceByTier()
                        .stream()
                        .map(this::toTokenBalanceByTier)
                        .toList())
                .topUsersByTokenUsage(messageRepository.findTopTokenUsers(
                                range.fromDateTime(), range.toExclusive(), 10)
                        .stream()
                        .map(this::toTopTokenUser)
                        .toList())
                .build();
    }

    private DashboardOverviewResponse.RoleOverview buildRoleOverview() {
        return DashboardOverviewResponse.RoleOverview.builder()
                .customers(userRepository.countActiveUsersByRole(UserRole.CUSTOMER))
                .contentAdmins(userRepository.countActiveUsersByRole(UserRole.CONTENT_ADMIN))
                .systemAdmins(userRepository.countActiveUsersByRole(UserRole.SYSTEM_ADMIN))
                .build();
    }

    private DashboardUserAnalyticsResponse.RoleCount roleCount(UserRole role) {
        return DashboardUserAnalyticsResponse.RoleCount.builder()
                .role(role.name())
                .count(userRepository.countActiveUsersByRole(role))
                .build();
    }

    private String resolveHealthStatus() {
        HealthEndpoint endpoint = healthEndpointProvider.getIfAvailable();
        if (endpoint == null) {
            return "UP";
        }
        return endpoint.health().getStatus().getCode();
    }

    private long sumMetricValue(String metricName) {
        return Math.round(meterRegistry.find(metricName).meters().stream()
                .flatMap(meter -> java.util.stream.StreamSupport.stream(meter.measure().spliterator(), false))
                .filter(measurement -> measurement.getStatistic() == Statistic.VALUE)
                .mapToDouble(Measurement::getValue)
                .filter(value -> !Double.isNaN(value) && !Double.isInfinite(value))
                .sum());
    }

    private long sumMetricCount(String metricName) {
        return Math.round(meterRegistry.find(metricName).meters().stream()
                .flatMap(meter -> java.util.stream.StreamSupport.stream(meter.measure().spliterator(), false))
                .filter(measurement -> measurement.getStatistic() == Statistic.COUNT)
                .mapToDouble(Measurement::getValue)
                .filter(value -> !Double.isNaN(value) && !Double.isInfinite(value))
                .sum());
    }

    private long sumHttpServerErrorCount() {
        return Math.round(meterRegistry.find("http.server.requests").meters().stream()
                .filter(this::isHttpServerErrorMeter)
                .flatMap(meter -> java.util.stream.StreamSupport.stream(meter.measure().spliterator(), false))
                .filter(measurement -> measurement.getStatistic() == Statistic.COUNT)
                .mapToDouble(Measurement::getValue)
                .filter(value -> !Double.isNaN(value) && !Double.isInfinite(value))
                .sum());
    }

    private boolean isHttpServerErrorMeter(Meter meter) {
        String status = meter.getId().getTag("status");
        String outcome = meter.getId().getTag("outcome");
        return (status != null && status.startsWith("5"))
                || "SERVER_ERROR".equalsIgnoreCase(outcome);
    }

    private String formatUptime() {
        long startedAt = ManagementFactory.getRuntimeMXBean().getStartTime();
        Duration uptime = Duration.ofMillis(System.currentTimeMillis() - startedAt);
        long days = uptime.toDays();
        long hours = uptime.minusDays(days).toHours();
        long minutes = uptime.minusDays(days).minusHours(hours).toMinutes();
        long seconds = uptime.minusDays(days).minusHours(hours).minusMinutes(minutes).toSeconds();
        return String.format(Locale.ROOT, "%dd %dh %dm %ds", days, hours, minutes, seconds);
    }

    private DateRange resolveDateRange(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now() : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(DEFAULT_RANGE_DAYS) : from;

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new InvalidRequestException("from must be before or equal to to");
        }
        if (resolvedFrom.plusDays(MAX_RANGE_DAYS).isBefore(resolvedTo)) {
            throw new InvalidRequestException("Date range must not exceed " + MAX_RANGE_DAYS + " days");
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
        if (!List.of("day", "week", "month").contains(normalized)) {
            throw new InvalidRequestException("granularity must be day, week, or month");
        }
        return normalized;
    }

    private Map<String, Long> toPeriodMap(List<DashboardPeriodCountProjection> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (DashboardPeriodCountProjection row : rows) {
            result.put(row.getPeriod(), row.getCount() == null ? 0L : row.getCount());
        }
        return result;
    }

    private Map<String, DashboardPeriodRevenueProjection> toRevenuePeriodMap(
            List<DashboardPeriodRevenueProjection> rows) {
        Map<String, DashboardPeriodRevenueProjection> result = new LinkedHashMap<>();
        for (DashboardPeriodRevenueProjection row : rows) {
            result.put(row.getPeriod(), row);
        }
        return result;
    }

    private Map<String, DashboardTransactionTrendProjection> toTransactionPeriodMap(
            List<DashboardTransactionTrendProjection> rows) {
        Map<String, DashboardTransactionTrendProjection> result = new LinkedHashMap<>();
        for (DashboardTransactionTrendProjection row : rows) {
            result.put(row.getPeriod(), row);
        }
        return result;
    }

    private Map<String, DashboardQuizTrendProjection> toQuizPeriodMap(List<DashboardQuizTrendProjection> rows) {
        Map<String, DashboardQuizTrendProjection> result = new LinkedHashMap<>();
        for (DashboardQuizTrendProjection row : rows) {
            result.put(row.getPeriod(), row);
        }
        return result;
    }

    private Map<String, DashboardTokenTrendProjection> toTokenPeriodMap(List<DashboardTokenTrendProjection> rows) {
        Map<String, DashboardTokenTrendProjection> result = new LinkedHashMap<>();
        for (DashboardTokenTrendProjection row : rows) {
            result.put(row.getPeriod(), row);
        }
        return result;
    }

    private List<DashboardRevenueResponse.StatusCount> buildOrderStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PaymentOrderStatus status : PaymentOrderStatus.values()) {
            counts.put(status.name(), 0L);
        }
        for (DashboardStatusCountProjection row : paymentOrderRepository.countOrdersByStatus()) {
            counts.put(row.getStatus(), nullSafe(row.getCount()));
        }
        return counts.entrySet().stream()
                .map(entry -> DashboardRevenueResponse.StatusCount.builder()
                        .status(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private DashboardRevenueResponse.RevenueByTier toRevenueByTier(DashboardTierRevenueProjection row) {
        return DashboardRevenueResponse.RevenueByTier.builder()
                .tierId(row.getTierId())
                .tierTitle(row.getTierTitle())
                .revenue(toLong(row.getRevenue()))
                .paidOrders(nullSafe(row.getPaidOrders()))
                .build();
    }

    private DashboardTierAnalyticsResponse.PurchasesByTier toPurchasesByTier(DashboardTierRevenueProjection row) {
        return DashboardTierAnalyticsResponse.PurchasesByTier.builder()
                .tierId(row.getTierId())
                .tierTitle(row.getTierTitle())
                .revenue(toLong(row.getRevenue()))
                .paidOrders(nullSafe(row.getPaidOrders()))
                .build();
    }

    private DashboardTierAnalyticsResponse.UsersByTier toUsersByTier(DashboardTierUsersProjection row) {
        return DashboardTierAnalyticsResponse.UsersByTier.builder()
                .tierId(row.getTierId())
                .tierTitle(row.getTierTitle())
                .users(nullSafe(row.getUsers()))
                .build();
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
                .quizId(row.getQuizId())
                .quizTitle(row.getQuizTitle())
                .wrongAnswers(nullSafe(row.getWrongAnswers()))
                .totalAnswers(nullSafe(row.getTotalAnswers()))
                .wrongRate(nullSafe(row.getWrongRate()) * 100.0)
                .build();
    }

    private DashboardTokenUsageResponse.TokenBalanceByTier toTokenBalanceByTier(
            DashboardTokenBalanceByTierProjection row) {
        return DashboardTokenUsageResponse.TokenBalanceByTier.builder()
                .tierId(row.getTierId())
                .tierTitle(row.getTierTitle())
                .users(nullSafe(row.getUsers()))
                .remainingTokens(nullSafe(row.getRemainingTokens()))
                .averageRemainingTokens(nullSafe(row.getAverageRemainingTokens()))
                .usersOutOfTokens(nullSafe(row.getUsersOutOfTokens()))
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

    private List<String> initializePeriods(LocalDate from, LocalDate to, String bucket) {
        List<String> periods = new ArrayList<>();
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

    private record DateRange(LocalDate from, LocalDate to, LocalDateTime fromDateTime, LocalDateTime toExclusive) {
    }
}
