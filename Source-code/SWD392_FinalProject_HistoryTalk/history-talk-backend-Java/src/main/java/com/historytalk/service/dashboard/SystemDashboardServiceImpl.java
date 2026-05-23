package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.DashboardChatActivityResponse;
import com.historytalk.dto.dashboard.DashboardContentSummaryResponse;
import com.historytalk.dto.dashboard.DashboardOverviewResponse;
import com.historytalk.dto.dashboard.DashboardSystemHealthResponse;
import com.historytalk.dto.dashboard.DashboardUserAnalyticsResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.dashboard.DashboardPeriodCountProjection;
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
