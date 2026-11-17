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
import com.historytalk.entity.enums.PaymentOrderType;
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
    private static final long ACTIVE_SESSION_WINDOW_MINUTES = 2L;
    private static final List<UserRole> B2C_ROLES = List.of(UserRole.CUSTOMER);
    private static final List<UserRole> B2B_ROLES = List.of(
            UserRole.SCHOOL_ADMIN, UserRole.TEACHER, UserRole.SCHOOL_STUDENT);
    private static final List<String> B2C_ROLE_NAMES = List.of(UserRole.CUSTOMER.name());
    private static final List<String> B2B_ROLE_NAMES = List.of(
            UserRole.SCHOOL_ADMIN.name(), UserRole.TEACHER.name(), UserRole.SCHOOL_STUDENT.name());

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
    public DashboardOverviewResponse getOverview(String scope) {
        DashboardScope resolvedScope = normalizeScope(scope);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        DashboardSystemHealthResponse health = getSystemHealth();

        List<DashboardOverviewResponse.TopCharacterOverview> topGlobalCharacters = messageRepository
                .findTopCharactersForDashboard(10)
                .stream()
                .map(this::toTopCharacterOverview)
                .toList();

        List<DashboardOverviewResponse.TopCharacterOverview> topLocalCharacters = messageRepository
                .findTopLocalCharactersForDashboard(10)
                .stream()
                .map(this::toTopCharacterOverview)
                .toList();

        List<DashboardOverviewResponse.TopCharacterOverview> topCharacters =
                resolvedScope == DashboardScope.B2B ? topLocalCharacters : topGlobalCharacters;

        DashboardOverviewResponse.UserOverview userOverview = switch (resolvedScope) {
            case B2C -> DashboardOverviewResponse.UserOverview.builder()
                    .total(userRepository.countActiveUsersByRoles(B2C_ROLES))
                    .active(userRepository.countActiveUsersByRoles(B2C_ROLES))
                    .inactive(userRepository.countInactiveUsers())
                    .deleted(userRepository.countDeletedUsersByRoles(B2C_ROLES))
                    .newToday(userRepository.countCreatedBetweenByRoles(todayStart, tomorrowStart, B2C_ROLES))
                    .newThisMonth(userRepository.countCreatedBetweenByRoles(monthStart, tomorrowStart, B2C_ROLES))
                    .build();
            case B2B -> DashboardOverviewResponse.UserOverview.builder()
                    .total(userRepository.countActiveUsersByRoles(B2B_ROLES))
                    .active(userRepository.countActiveUsersByRoles(B2B_ROLES))
                    .inactive(userRepository.countInactiveUsers())
                    .deleted(userRepository.countDeletedUsersByRoles(B2B_ROLES))
                    .newToday(userRepository.countCreatedBetweenByRoles(todayStart, tomorrowStart, B2B_ROLES))
                    .newThisMonth(userRepository.countCreatedBetweenByRoles(monthStart, tomorrowStart, B2B_ROLES))
                    .build();
            case ALL -> DashboardOverviewResponse.UserOverview.builder()
                    .total(userRepository.countAllUsers())
                    .active(userRepository.countActiveUsers())
                    .inactive(userRepository.countInactiveUsers())
                    .deleted(userRepository.countDeletedUsers())
                    .newToday(userRepository.countCreatedBetween(todayStart, tomorrowStart))
                    .newThisMonth(userRepository.countCreatedBetween(monthStart, tomorrowStart))
                    .build();
        };

        DashboardOverviewResponse.ContentOverview contentOverview = resolvedScope == DashboardScope.B2B
                ? DashboardOverviewResponse.ContentOverview.builder()
                        .historicalContexts(historicalContextRepository.countLocalCurrent())
                        .publishedHistoricalContexts(historicalContextRepository.countLocalPublished())
                        .characters(characterRepository.countLocalCurrent())
                        .publishedCharacters(characterRepository.countLocalPublished())
                        .documents(documentRepository.countCurrent())
                        .build()
                : DashboardOverviewResponse.ContentOverview.builder()
                        .historicalContexts(historicalContextRepository.countCurrent())
                        .publishedHistoricalContexts(historicalContextRepository.countPublished())
                        .characters(characterRepository.countCurrent())
                        .publishedCharacters(characterRepository.countPublished())
                        .documents(documentRepository.countCurrent())
                        .build();

        DashboardOverviewResponse.ChatOverview chatOverview = switch (resolvedScope) {
            case B2C -> DashboardOverviewResponse.ChatOverview.builder()
                    .sessions(chatSessionRepository.countCurrentByRoles(B2C_ROLES))
                    .messages(messageRepository.countCurrentByRoles(B2C_ROLES))
                    .messagesToday(messageRepository.countCreatedBetweenByRoles(todayStart, tomorrowStart, B2C_ROLES))
                    .build();
            case B2B -> DashboardOverviewResponse.ChatOverview.builder()
                    .sessions(chatSessionRepository.countCurrentByRoles(B2B_ROLES))
                    .messages(messageRepository.countCurrentByRoles(B2B_ROLES))
                    .messagesToday(messageRepository.countCreatedBetweenByRoles(todayStart, tomorrowStart, B2B_ROLES))
                    .build();
            case ALL -> DashboardOverviewResponse.ChatOverview.builder()
                    .sessions(chatSessionRepository.countCurrent())
                    .messages(messageRepository.countCurrent())
                    .messagesToday(messageRepository.countCreatedBetween(todayStart, tomorrowStart))
                    .build();
        };

        return DashboardOverviewResponse.builder()
                .users(userOverview)
                .roles(buildRoleOverview())
                .content(contentOverview)
                .chat(chatOverview)
                .topCharacters(topCharacters)
                .topGlobalCharacters(topGlobalCharacters)
                .topLocalCharacters(topLocalCharacters)
                .systemHealth(DashboardOverviewResponse.SystemHealthOverview.builder()
                        .status(health.getStatus())
                        .lastCheckedAt(health.getLastCheckedAt())
                        .build())
                .build();
    }

    @Override
    public DashboardUserAnalyticsResponse getUserAnalytics(LocalDate from, LocalDate to, String granularity, String scope) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        DashboardScope resolvedScope = normalizeScope(scope);

        Map<String, Long> newUsersByPeriod;
        Map<String, Long> activeUsersByPeriod;
        DashboardUserAnalyticsResponse.UserSummary summary;
        List<DashboardUserAnalyticsResponse.RoleCount> byRole;

        if (resolvedScope == DashboardScope.B2C) {
            newUsersByPeriod = toPeriodMap(
                    userRepository.countNewUsersByPeriodAndRoles(range.fromDateTime(), range.toExclusive(), bucket, B2C_ROLE_NAMES));
            activeUsersByPeriod = toPeriodMap(
                    userRepository.countActiveUsersByPeriodAndRoles(range.fromDateTime(), range.toExclusive(), bucket, B2C_ROLE_NAMES));
            summary = DashboardUserAnalyticsResponse.UserSummary.builder()
                    .total(userRepository.countActiveUsersByRoles(B2C_ROLES))
                    .active(userRepository.countActiveUsersByRoles(B2C_ROLES))
                    .inactive(userRepository.countInactiveUsers())
                    .deleted(userRepository.countDeletedUsersByRoles(B2C_ROLES))
                    .recentlyActive(userRepository.countRecentlyActiveUsersByRoles(LocalDateTime.now().minusDays(7), B2C_ROLES))
                    .build();
            byRole = List.of(roleCount(UserRole.CUSTOMER));
        } else if (resolvedScope == DashboardScope.B2B) {
            newUsersByPeriod = toPeriodMap(
                    userRepository.countNewUsersByPeriodAndRoles(range.fromDateTime(), range.toExclusive(), bucket, B2B_ROLE_NAMES));
            activeUsersByPeriod = toPeriodMap(
                    userRepository.countActiveUsersByPeriodAndRoles(range.fromDateTime(), range.toExclusive(), bucket, B2B_ROLE_NAMES));
            summary = DashboardUserAnalyticsResponse.UserSummary.builder()
                    .total(userRepository.countActiveUsersByRoles(B2B_ROLES))
                    .active(userRepository.countActiveUsersByRoles(B2B_ROLES))
                    .inactive(userRepository.countInactiveUsers())
                    .deleted(userRepository.countDeletedUsersByRoles(B2B_ROLES))
                    .recentlyActive(userRepository.countRecentlyActiveUsersByRoles(LocalDateTime.now().minusDays(7), B2B_ROLES))
                    .build();
            byRole = List.of(
                    roleCount(UserRole.SCHOOL_ADMIN),
                    roleCount(UserRole.TEACHER),
                    roleCount(UserRole.SCHOOL_STUDENT)
            );
        } else {
            newUsersByPeriod = toPeriodMap(
                    userRepository.countNewUsersByPeriod(range.fromDateTime(), range.toExclusive(), bucket));
            activeUsersByPeriod = toPeriodMap(
                    userRepository.countActiveUsersByPeriod(range.fromDateTime(), range.toExclusive(), bucket));
            summary = DashboardUserAnalyticsResponse.UserSummary.builder()
                    .total(userRepository.countAllUsers())
                    .active(userRepository.countActiveUsers())
                    .inactive(userRepository.countInactiveUsers())
                    .deleted(userRepository.countDeletedUsers())
                    .recentlyActive(userRepository.countRecentlyActiveUsers(LocalDateTime.now().minusDays(7)))
                    .build();
            byRole = List.of(
                    roleCount(UserRole.CUSTOMER),
                    roleCount(UserRole.SCHOOL_ADMIN),
                    roleCount(UserRole.TEACHER),
                    roleCount(UserRole.SCHOOL_STUDENT),
                    roleCount(UserRole.CONTENT_ADMIN),
                    roleCount(UserRole.SYSTEM_ADMIN)
            );
        }

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
    public DashboardContentSummaryResponse getContentSummary(String scope) {
        DashboardScope resolvedScope = normalizeScope(scope);

        DashboardContentSummaryResponse.InventoryStats globalContexts = DashboardContentSummaryResponse.InventoryStats.builder()
                .total(historicalContextRepository.countCurrent())
                .published(historicalContextRepository.countPublished())
                .active(historicalContextRepository.countActive())
                .build();
        DashboardContentSummaryResponse.InventoryStats globalCharacters = DashboardContentSummaryResponse.InventoryStats.builder()
                .total(characterRepository.countCurrent())
                .published(characterRepository.countPublished())
                .active(characterRepository.countActive())
                .build();
        DashboardContentSummaryResponse.InventoryStats localContexts = DashboardContentSummaryResponse.InventoryStats.builder()
                .total(historicalContextRepository.countLocalCurrent())
                .published(historicalContextRepository.countLocalPublished())
                .active(historicalContextRepository.countLocalActive())
                .build();
        DashboardContentSummaryResponse.InventoryStats localCharacters = DashboardContentSummaryResponse.InventoryStats.builder()
                .total(characterRepository.countLocalCurrent())
                .published(characterRepository.countLocalPublished())
                .active(characterRepository.countLocalActive())
                .build();

        return DashboardContentSummaryResponse.builder()
                .historicalContexts(resolvedScope == DashboardScope.B2B ? localContexts : globalContexts)
                .characters(resolvedScope == DashboardScope.B2B ? localCharacters : globalCharacters)
                .documents(DashboardContentSummaryResponse.DocumentStats.builder()
                        .total(documentRepository.countCurrent())
                        .active(documentRepository.countActive())
                        .build())
                .localHistoricalContexts(localContexts)
                .localCharacters(localCharacters)
                .build();
    }

    @Override
    public DashboardChatActivityResponse getChatActivity(LocalDate from, LocalDate to, String granularity, String scope) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        DashboardScope resolvedScope = normalizeScope(scope);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime activeCutoff = LocalDateTime.now().minusMinutes(ACTIVE_SESSION_WINDOW_MINUTES);

        Map<String, Long> sessionsByPeriod;
        Map<String, Long> messagesByPeriod;
        DashboardChatActivityResponse.ChatSummary summary;

        if (resolvedScope == DashboardScope.ALL) {
            sessionsByPeriod = toPeriodMap(
                    chatSessionRepository.countSessionsByPeriod(range.fromDateTime(), range.toExclusive(), bucket));
            messagesByPeriod = toPeriodMap(
                    messageRepository.countMessagesByPeriod(range.fromDateTime(), range.toExclusive(), bucket));
            summary = DashboardChatActivityResponse.ChatSummary.builder()
                    .sessions(chatSessionRepository.countCurrent())
                    .activeSessions(chatSessionRepository.countActiveSince(activeCutoff))
                    .messages(messageRepository.countCurrent())
                    .userMessages(messageRepository.countByAiFlag(false))
                    .aiMessages(messageRepository.countByAiFlag(true))
                    .sessionsToday(chatSessionRepository.countCreatedBetween(todayStart, tomorrowStart))
                    .messagesToday(messageRepository.countCreatedBetween(todayStart, tomorrowStart))
                    .build();
        } else {
            List<UserRole> roles = resolvedScope == DashboardScope.B2C ? B2C_ROLES : B2B_ROLES;
            List<String> roleNames = resolvedScope == DashboardScope.B2C ? B2C_ROLE_NAMES : B2B_ROLE_NAMES;
            sessionsByPeriod = toPeriodMap(
                    chatSessionRepository.countSessionsByPeriodAndRoles(range.fromDateTime(), range.toExclusive(), bucket, roleNames));
            messagesByPeriod = toPeriodMap(
                    messageRepository.countMessagesByPeriodAndRoles(range.fromDateTime(), range.toExclusive(), bucket, roleNames));
            summary = DashboardChatActivityResponse.ChatSummary.builder()
                    .sessions(chatSessionRepository.countCurrentByRoles(roles))
                    .activeSessions(chatSessionRepository.countActiveSinceByRoles(activeCutoff, roles))
                    .messages(messageRepository.countCurrentByRoles(roles))
                    .userMessages(messageRepository.countByAiFlagAndRoles(false, roles))
                    .aiMessages(messageRepository.countByAiFlagAndRoles(true, roles))
                    .sessionsToday(chatSessionRepository.countCreatedBetweenByRoles(todayStart, tomorrowStart, roles))
                    .messagesToday(messageRepository.countCreatedBetweenByRoles(todayStart, tomorrowStart, roles))
                    .build();
        }

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
    public DashboardRevenueResponse getRevenue(LocalDate from, LocalDate to, String granularity, String scope) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        DashboardScope resolvedScope = normalizeScope(scope);
        PaymentOrderType orderType = resolveOrderType(resolvedScope);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime yearStart = today.withDayOfYear(1).atStartOfDay();
        LocalDateTime nextYearStart = today.plusYears(1).withDayOfYear(1).atStartOfDay();

        long totalRevenue = orderType == null
                ? toLong(paymentOrderRepository.sumAmountByStatus(PaymentOrderStatus.PAID))
                : toLong(paymentOrderRepository.sumAmountByStatusAndOrderType(PaymentOrderStatus.PAID, orderType));
        long paidOrders = orderType == null
                ? paymentOrderRepository.countByDeletedAtIsNullAndStatus(PaymentOrderStatus.PAID)
                : paymentOrderRepository.countByDeletedAtIsNullAndStatusAndOrderType(PaymentOrderStatus.PAID, orderType);
        long averageOrderValue = paidOrders == 0 ? 0 : totalRevenue / paidOrders;

        Map<String, DashboardPeriodRevenueProjection> revenueByPeriod = toRevenuePeriodMap(
                orderType == null
                        ? paymentOrderRepository.sumPaidRevenueByPeriod(range.fromDateTime(), range.toExclusive(), bucket)
                        : paymentOrderRepository.sumPaidRevenueByPeriodAndOrderType(
                                range.fromDateTime(), range.toExclusive(), bucket, orderType.name()));

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

        long revenueToday = orderType == null
                ? toLong(paymentOrderRepository.sumAmountByStatusAndPaidAtBetween(
                        PaymentOrderStatus.PAID, todayStart, tomorrowStart))
                : toLong(paymentOrderRepository.sumAmountByStatusAndOrderTypeAndPaidAtBetween(
                        PaymentOrderStatus.PAID, orderType, todayStart, tomorrowStart));
        long revenueThisMonth = orderType == null
                ? toLong(paymentOrderRepository.sumAmountByStatusAndPaidAtBetween(
                        PaymentOrderStatus.PAID, monthStart, tomorrowStart))
                : toLong(paymentOrderRepository.sumAmountByStatusAndOrderTypeAndPaidAtBetween(
                        PaymentOrderStatus.PAID, orderType, monthStart, tomorrowStart));
        long revenueThisYear = orderType == null
                ? toLong(paymentOrderRepository.sumAmountByStatusAndPaidAtBetween(
                        PaymentOrderStatus.PAID, yearStart, nextYearStart))
                : toLong(paymentOrderRepository.sumAmountByStatusAndOrderTypeAndPaidAtBetween(
                        PaymentOrderStatus.PAID, orderType, yearStart, nextYearStart));

        List<DashboardRevenueResponse.RevenueByTier> revenueByTier = resolvedScope == DashboardScope.B2B
                ? List.of()
                : paymentOrderRepository.sumPaidRevenueByTier(range.fromDateTime(), range.toExclusive())
                        .stream()
                        .map(this::toRevenueByTier)
                        .toList();

        return DashboardRevenueResponse.builder()
                .summary(DashboardRevenueResponse.RevenueSummary.builder()
                        .totalRevenue(totalRevenue)
                        .revenueToday(revenueToday)
                        .revenueThisMonth(revenueThisMonth)
                        .revenueThisYear(revenueThisYear)
                        .paidOrders(paidOrders)
                        .averageOrderValue(averageOrderValue)
                        .build())
                .ordersByStatus(buildOrderStatusCounts(orderType))
                .revenueByTier(revenueByTier)
                .trend(trend)
                .build();
    }

    @Override
    public DashboardPaymentResponse getPayments(LocalDate from, LocalDate to, String granularity, String scope) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        DashboardScope resolvedScope = normalizeScope(scope);
        PaymentOrderType orderType = resolveOrderType(resolvedScope);

        Map<String, DashboardTransactionTrendProjection> transactionsByPeriod = toTransactionPeriodMap(
                orderType == null
                        ? paymentTransactionRepository.countTransactionsByPeriod(
                                range.fromDateTime(), range.toExclusive(), bucket)
                        : paymentTransactionRepository.countTransactionsByPeriodAndOrderType(
                                range.fromDateTime(), range.toExclusive(), bucket, orderType.name()));

        long pendingOrders = countOrders(PaymentOrderStatus.PENDING, orderType);
        long paidOrders = countOrders(PaymentOrderStatus.PAID, orderType);
        long cancelledOrders = countOrders(PaymentOrderStatus.CANCELLED, orderType);
        long expiredOrders = countOrders(PaymentOrderStatus.EXPIRED, orderType);
        long failedOrders = countOrders(PaymentOrderStatus.FAILED, orderType);

        long successfulTransactions = orderType == null
                ? paymentTransactionRepository.countByDeletedAtIsNullAndStatus(PaymentTransactionStatus.SUCCESS)
                : paymentTransactionRepository.countByStatusAndOrderType(
                        PaymentTransactionStatus.SUCCESS.name(), orderType.name());
        long failedTransactions = orderType == null
                ? paymentTransactionRepository.countByDeletedAtIsNullAndStatus(PaymentTransactionStatus.FAILED)
                : paymentTransactionRepository.countByStatusAndOrderType(
                        PaymentTransactionStatus.FAILED.name(), orderType.name());

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
                        .successfulTransactions(successfulTransactions)
                        .failedTransactions(failedTransactions)
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
    public DashboardQuizAnalyticsResponse getQuiz(LocalDate from, LocalDate to, String granularity, String scope) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        DashboardScope resolvedScope = normalizeScope(scope);

        Boolean isLocal = resolvedScope == DashboardScope.ALL ? null : (resolvedScope == DashboardScope.B2B);

        long totalQuizzes;
        long publishedQuizzes;
        long draftQuizzes;
        long startedSessions;
        long completedSessions;
        double averageScorePercentage;
        Map<String, DashboardQuizTrendProjection> sessionsByPeriod;

        if (isLocal == null) {
            totalQuizzes = quizRepository.countAllQuizzes();
            publishedQuizzes = quizRepository.countPublishedQuizzes();
            draftQuizzes = quizRepository.countDraftQuizzes();
            startedSessions = quizSessionRepository.countStartedSessions();
            completedSessions = quizSessionRepository.countCompletedSessions();
            averageScorePercentage = nullSafe(quizSessionRepository.averageScorePercentage());
            sessionsByPeriod = toQuizPeriodMap(
                    quizSessionRepository.countSessionsByPeriod(range.fromDateTime(), range.toExclusive(), bucket));
        } else if (Boolean.FALSE.equals(isLocal)) {
            totalQuizzes = quizRepository.countGlobalQuizzes();
            publishedQuizzes = quizRepository.countGlobalPublishedQuizzes();
            draftQuizzes = quizRepository.countGlobalDraftQuizzes();
            startedSessions = quizSessionRepository.countStartedSessionsByScope(false);
            completedSessions = quizSessionRepository.countCompletedSessionsByScope(false);
            averageScorePercentage = nullSafe(quizSessionRepository.averageScorePercentageByScope(false));
            sessionsByPeriod = toQuizPeriodMap(
                    quizSessionRepository.countSessionsByPeriodAndScope(
                            range.fromDateTime(), range.toExclusive(), bucket, false));
        } else {
            totalQuizzes = quizRepository.countLocalQuizzes();
            publishedQuizzes = quizRepository.countLocalPublishedQuizzes();
            draftQuizzes = quizRepository.countLocalDraftQuizzes();
            startedSessions = quizSessionRepository.countStartedSessionsByScope(true);
            completedSessions = quizSessionRepository.countCompletedSessionsByScope(true);
            averageScorePercentage = nullSafe(quizSessionRepository.averageScorePercentageByScope(true));
            sessionsByPeriod = toQuizPeriodMap(
                    quizSessionRepository.countSessionsByPeriodAndScope(
                            range.fromDateTime(), range.toExclusive(), bucket, true));
        }

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

        List<DashboardQuizAnalyticsResponse.TopWrongQuestion> topWrongQuestions = (isLocal == null
                ? quizAnswerDetailRepository.findTopWrongQuestionsForDashboard(range.fromDateTime(), range.toExclusive(), 5)
                : quizAnswerDetailRepository.findTopWrongQuestionsForDashboardByScope(
                        range.fromDateTime(), range.toExclusive(), isLocal, 5))
                .stream()
                .map(this::toTopWrongQuestion)
                .toList();

        return DashboardQuizAnalyticsResponse.builder()
                .summary(DashboardQuizAnalyticsResponse.QuizSummary.builder()
                        .totalQuizzes(totalQuizzes)
                        .publishedQuizzes(publishedQuizzes)
                        .draftQuizzes(draftQuizzes)
                        .deletedQuizzes(quizRepository.countDeletedQuizzes())
                        .startedSessions(startedSessions)
                        .completedSessions(completedSessions)
                        .completionRate(percentage(completedSessions, startedSessions))
                        .averageScorePercentage(averageScorePercentage)
                        .build())
                .sessionsTrend(trend)
                .topQuizzes(quizRepository.findTopQuizzesForDashboardByScope(
                                range.fromDateTime(), range.toExclusive(), isLocal, 5)
                        .stream()
                        .map(this::toTopQuiz)
                        .toList())
                .topWrongQuestions(topWrongQuestions)
                .build();
    }

    @Override
    public DashboardTokenUsageResponse getTokens(LocalDate from, LocalDate to, String granularity, String scope) {
        DateRange range = resolveDateRange(from, to);
        String bucket = normalizeGranularity(granularity);
        DashboardScope resolvedScope = normalizeScope(scope);

        DashboardTokenSummaryProjection tokenSummary;
        DashboardTokenBalanceSummaryProjection balanceSummary;
        Map<String, DashboardTokenTrendProjection> tokensByPeriod;
        List<DashboardTokenUsageResponse.TokenBalanceByTier> tokenBalanceByTier;
        List<DashboardTokenUsageResponse.TopTokenUser> topUsers;

        if (resolvedScope == DashboardScope.B2B) {
            tokenSummary = messageRepository.sumTokensBetweenByRoles(
                    range.fromDateTime(), range.toExclusive(), B2B_ROLE_NAMES);
            balanceSummary = userRepository.getTokenBalanceSummaryByRoles(B2B_ROLE_NAMES);
            tokensByPeriod = toTokenPeriodMap(messageRepository.sumTokensByPeriodByRoles(
                    range.fromDateTime(), range.toExclusive(), bucket, B2B_ROLE_NAMES));
            tokenBalanceByTier = List.of();
            topUsers = messageRepository.findTopTokenUsersByRoles(
                            range.fromDateTime(), range.toExclusive(), B2B_ROLE_NAMES, 10)
                    .stream()
                    .map(this::toTopTokenUser)
                    .toList();
        } else if (resolvedScope == DashboardScope.B2C) {
            tokenSummary = messageRepository.sumTokensBetweenByRoles(
                    range.fromDateTime(), range.toExclusive(), B2C_ROLE_NAMES);
            balanceSummary = userRepository.getTokenBalanceSummary();
            tokensByPeriod = toTokenPeriodMap(messageRepository.sumTokensByPeriodByRoles(
                    range.fromDateTime(), range.toExclusive(), bucket, B2C_ROLE_NAMES));
            tokenBalanceByTier = userRepository.countTokenBalanceByTier()
                    .stream()
                    .map(this::toTokenBalanceByTier)
                    .toList();
            topUsers = messageRepository.findTopTokenUsers(
                            range.fromDateTime(), range.toExclusive(), 10)
                    .stream()
                    .map(this::toTopTokenUser)
                    .toList();
        } else {
            tokenSummary = messageRepository.sumTokensBetween(
                    range.fromDateTime(), range.toExclusive());
            balanceSummary = userRepository.getTokenBalanceSummary();
            tokensByPeriod = toTokenPeriodMap(
                    messageRepository.sumTokensByPeriod(range.fromDateTime(), range.toExclusive(), bucket));
            tokenBalanceByTier = userRepository.countTokenBalanceByTier()
                    .stream()
                    .map(this::toTokenBalanceByTier)
                    .toList();
            topUsers = messageRepository.findTopTokenUsers(
                            range.fromDateTime(), range.toExclusive(), 10)
                    .stream()
                    .map(this::toTopTokenUser)
                    .toList();
        }

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
                .tokenBalanceByTier(tokenBalanceByTier)
                .topUsersByTokenUsage(topUsers)
                .build();
    }

    private DashboardOverviewResponse.RoleOverview buildRoleOverview() {
        return DashboardOverviewResponse.RoleOverview.builder()
                .customers(userRepository.countActiveUsersByRole(UserRole.CUSTOMER))
                .contentAdmins(userRepository.countActiveUsersByRole(UserRole.CONTENT_ADMIN))
                .systemAdmins(userRepository.countActiveUsersByRole(UserRole.SYSTEM_ADMIN))
                .schoolAdmins(userRepository.countActiveUsersByRole(UserRole.SCHOOL_ADMIN))
                .teachers(userRepository.countActiveUsersByRole(UserRole.TEACHER))
                .schoolStudents(userRepository.countActiveUsersByRole(UserRole.SCHOOL_STUDENT))
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
        if (!List.of("day", "week", "month").contains(normalized)) {
            throw new InvalidRequestException("granularity (đơn vị thời gian) phải là day, week hoặc month");
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

    private List<DashboardRevenueResponse.StatusCount> buildOrderStatusCounts(PaymentOrderType orderType) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PaymentOrderStatus status : PaymentOrderStatus.values()) {
            counts.put(status.name(), 0L);
        }
        List<DashboardStatusCountProjection> rows = orderType == null
                ? paymentOrderRepository.countOrdersByStatus()
                : paymentOrderRepository.countOrdersByStatusAndOrderType(orderType.name());
        for (DashboardStatusCountProjection row : rows) {
            counts.put(row.getStatus(), nullSafe(row.getCount()));
        }
        return counts.entrySet().stream()
                .map(entry -> DashboardRevenueResponse.StatusCount.builder()
                        .status(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private long countOrders(PaymentOrderStatus status, PaymentOrderType orderType) {
        return orderType == null
                ? paymentOrderRepository.countByDeletedAtIsNullAndStatus(status)
                : paymentOrderRepository.countByDeletedAtIsNullAndStatusAndOrderType(status, orderType);
    }

    private DashboardOverviewResponse.TopCharacterOverview toTopCharacterOverview(
            com.historytalk.repository.dashboard.DashboardTopCharacterProjection c) {
        return DashboardOverviewResponse.TopCharacterOverview.builder()
                .characterId(c.getCharacterId())
                .name(c.getName())
                .title(c.getTitle() != null ? c.getTitle() : "")
                .imageUrl(c.getImageUrl() != null ? c.getImageUrl() : "")
                .totalMessages(nullSafe(c.getTotalMessages()))
                .userMessages(nullSafe(c.getUserMessages()))
                .aiMessages(nullSafe(c.getAiMessages()))
                .build();
    }

    private DashboardScope normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return DashboardScope.ALL;
        }
        return switch (scope.trim().toUpperCase(Locale.ROOT)) {
            case "ALL" -> DashboardScope.ALL;
            case "B2C", "GLOBAL" -> DashboardScope.B2C;
            case "B2B", "LOCAL" -> DashboardScope.B2B;
            default -> throw new InvalidRequestException("scope phải là ALL, B2C, B2B, GLOBAL hoặc LOCAL");
        };
    }

    private PaymentOrderType resolveOrderType(DashboardScope scope) {
        return switch (scope) {
            case B2C -> PaymentOrderType.TIER_SUBSCRIPTION;
            case B2B -> PaymentOrderType.SCHOOL_TOKEN_TOPUP;
            case ALL -> null;
        };
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
                .questionContent(row.getQuestionContent())
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

    private enum DashboardScope {
        ALL, B2C, B2B
    }

    private record DateRange(LocalDate from, LocalDate to, LocalDateTime fromDateTime, LocalDateTime toExclusive) {
    }
}
