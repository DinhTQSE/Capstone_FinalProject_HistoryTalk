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

import java.time.LocalDate;

public interface SystemDashboardService {

    default DashboardOverviewResponse getOverview() {
        return getOverview("ALL");
    }

    DashboardOverviewResponse getOverview(String scope);

    default DashboardUserAnalyticsResponse getUserAnalytics(LocalDate from, LocalDate to, String granularity) {
        return getUserAnalytics(from, to, granularity, "ALL");
    }

    DashboardUserAnalyticsResponse getUserAnalytics(LocalDate from, LocalDate to, String granularity, String scope);

    default DashboardContentSummaryResponse getContentSummary() {
        return getContentSummary("ALL");
    }

    DashboardContentSummaryResponse getContentSummary(String scope);

    default DashboardChatActivityResponse getChatActivity(LocalDate from, LocalDate to, String granularity) {
        return getChatActivity(from, to, granularity, "ALL");
    }

    DashboardChatActivityResponse getChatActivity(LocalDate from, LocalDate to, String granularity, String scope);

    DashboardSystemHealthResponse getSystemHealth();

    default DashboardRevenueResponse getRevenue(LocalDate from, LocalDate to, String granularity) {
        return getRevenue(from, to, granularity, "ALL");
    }

    DashboardRevenueResponse getRevenue(LocalDate from, LocalDate to, String granularity, String scope);

    default DashboardPaymentResponse getPayments(LocalDate from, LocalDate to, String granularity) {
        return getPayments(from, to, granularity, "ALL");
    }

    DashboardPaymentResponse getPayments(LocalDate from, LocalDate to, String granularity, String scope);

    DashboardTierAnalyticsResponse getTiers(LocalDate from, LocalDate to, String granularity);

    default DashboardQuizAnalyticsResponse getQuiz(LocalDate from, LocalDate to, String granularity) {
        return getQuiz(from, to, granularity, "ALL");
    }

    DashboardQuizAnalyticsResponse getQuiz(LocalDate from, LocalDate to, String granularity, String scope);

    default DashboardTokenUsageResponse getTokens(LocalDate from, LocalDate to, String granularity) {
        return getTokens(from, to, granularity, "ALL");
    }

    DashboardTokenUsageResponse getTokens(LocalDate from, LocalDate to, String granularity, String scope);
}
