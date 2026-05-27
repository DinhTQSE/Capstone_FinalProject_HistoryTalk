package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.DashboardChatActivityResponse;
import com.historytalk.dto.dashboard.DashboardContentSummaryResponse;
import com.historytalk.dto.dashboard.DashboardOverviewResponse;
import com.historytalk.dto.dashboard.DashboardPaymentResponse;
import com.historytalk.dto.dashboard.DashboardQuizAnalyticsResponse;
import com.historytalk.dto.dashboard.DashboardRevenueResponse;
import com.historytalk.dto.dashboard.DashboardSystemHealthResponse;
import com.historytalk.dto.dashboard.DashboardTierAnalyticsResponse;
import com.historytalk.dto.dashboard.DashboardUserAnalyticsResponse;

import java.time.LocalDate;

public interface SystemDashboardService {

    DashboardOverviewResponse getOverview();

    DashboardUserAnalyticsResponse getUserAnalytics(LocalDate from, LocalDate to, String granularity);

    DashboardContentSummaryResponse getContentSummary();

    DashboardChatActivityResponse getChatActivity(LocalDate from, LocalDate to, String granularity);

    DashboardSystemHealthResponse getSystemHealth();

    DashboardRevenueResponse getRevenue(LocalDate from, LocalDate to, String granularity);

    DashboardPaymentResponse getPayments(LocalDate from, LocalDate to, String granularity);

    DashboardTierAnalyticsResponse getTiers(LocalDate from, LocalDate to, String granularity);

    DashboardQuizAnalyticsResponse getQuiz(LocalDate from, LocalDate to, String granularity);
}
