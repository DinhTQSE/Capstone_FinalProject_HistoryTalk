package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.DashboardChatActivityResponse;
import com.historytalk.dto.dashboard.DashboardContentSummaryResponse;
import com.historytalk.dto.dashboard.DashboardOverviewResponse;
import com.historytalk.dto.dashboard.DashboardSystemHealthResponse;
import com.historytalk.dto.dashboard.DashboardUserAnalyticsResponse;

import java.time.LocalDate;

public interface SystemDashboardService {

    DashboardOverviewResponse getOverview();

    DashboardUserAnalyticsResponse getUserAnalytics(LocalDate from, LocalDate to, String granularity);

    DashboardContentSummaryResponse getContentSummary();

    DashboardChatActivityResponse getChatActivity(LocalDate from, LocalDate to, String granularity);

    DashboardSystemHealthResponse getSystemHealth();
}
