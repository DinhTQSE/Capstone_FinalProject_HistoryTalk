package com.historytalk.controller.dashboard;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.dashboard.DashboardChatActivityResponse;
import com.historytalk.dto.dashboard.DashboardContentSummaryResponse;
import com.historytalk.dto.dashboard.DashboardOverviewResponse;
import com.historytalk.dto.dashboard.DashboardPaymentResponse;
import com.historytalk.dto.dashboard.DashboardQuizAnalyticsResponse;
import com.historytalk.dto.dashboard.DashboardRevenueResponse;
import com.historytalk.dto.dashboard.DashboardSystemHealthResponse;
import com.historytalk.dto.dashboard.DashboardTierAnalyticsResponse;
import com.historytalk.dto.dashboard.DashboardUserAnalyticsResponse;
import com.historytalk.service.dashboard.SystemDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/system-admin/dashboard")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "System Dashboard", description = "System Admin dashboard APIs")
public class SystemDashboardController {

    private final SystemDashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Get dashboard overview", description = "Returns first-screen System Admin dashboard cards")
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview() {
        log.info("GET /api/v1/system-admin/dashboard/overview");
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getOverview(),
                "Dashboard overview retrieved successfully"
        ));
    }

    @GetMapping("/users")
    @Operation(summary = "Get user analytics", description = "Returns user summary, role distribution, and user trend")
    public ResponseEntity<ApiResponse<DashboardUserAnalyticsResponse>> getUserAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "day") String granularity) {
        log.info("GET /api/v1/system-admin/dashboard/users - from: {}, to: {}, granularity: {}",
                from, to, granularity);
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getUserAnalytics(from, to, granularity),
                "User analytics retrieved successfully"
        ));
    }

    @GetMapping("/content")
    @Operation(summary = "Get content summary", description = "Returns historical context, character, and document inventory")
    public ResponseEntity<ApiResponse<DashboardContentSummaryResponse>> getContentSummary() {
        log.info("GET /api/v1/system-admin/dashboard/content");
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getContentSummary(),
                "Content summary retrieved successfully"
        ));
    }

    @GetMapping("/chat-activity")
    @Operation(summary = "Get chat activity", description = "Returns chat session/message summary and trend")
    public ResponseEntity<ApiResponse<DashboardChatActivityResponse>> getChatActivity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "day") String granularity) {
        log.info("GET /api/v1/system-admin/dashboard/chat-activity - from: {}, to: {}, granularity: {}",
                from, to, granularity);
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getChatActivity(from, to, granularity),
                "Chat activity retrieved successfully"
        ));
    }

    @GetMapping("/system-health")
    @Operation(summary = "Get system health summary", description = "Returns lightweight backend health metrics for the in-app dashboard")
    public ResponseEntity<ApiResponse<DashboardSystemHealthResponse>> getSystemHealth() {
        log.info("GET /api/v1/system-admin/dashboard/system-health");
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getSystemHealth(),
                "System health retrieved successfully"
        ));
    }

    @GetMapping("/revenue")
    @Operation(summary = "Get revenue analytics", description = "Returns revenue summary, order status counts, tier revenue, and revenue trend")
    public ResponseEntity<ApiResponse<DashboardRevenueResponse>> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "day") String granularity) {
        log.info("GET /api/v1/system-admin/dashboard/revenue - from: {}, to: {}, granularity: {}",
                from, to, granularity);
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getRevenue(from, to, granularity),
                "Revenue analytics retrieved successfully"
        ));
    }

    @GetMapping("/payments")
    @Operation(summary = "Get payment analytics", description = "Returns payment order status and payment transaction trend")
    public ResponseEntity<ApiResponse<DashboardPaymentResponse>> getPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "day") String granularity) {
        log.info("GET /api/v1/system-admin/dashboard/payments - from: {}, to: {}, granularity: {}",
                from, to, granularity);
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getPayments(from, to, granularity),
                "Payment analytics retrieved successfully"
        ));
    }

    @GetMapping("/tiers")
    @Operation(summary = "Get tier analytics", description = "Returns tier usage, subscription, and purchase analytics")
    public ResponseEntity<ApiResponse<DashboardTierAnalyticsResponse>> getTiers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "day") String granularity) {
        log.info("GET /api/v1/system-admin/dashboard/tiers - from: {}, to: {}, granularity: {}",
                from, to, granularity);
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getTiers(from, to, granularity),
                "Tier analytics retrieved successfully"
        ));
    }

    @GetMapping("/quiz")
    @Operation(summary = "Get quiz analytics", description = "Returns quiz inventory, quiz session trend, top quizzes, and wrong-answer analytics")
    public ResponseEntity<ApiResponse<DashboardQuizAnalyticsResponse>> getQuiz(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "day") String granularity) {
        log.info("GET /api/v1/system-admin/dashboard/quiz - from: {}, to: {}, granularity: {}",
                from, to, granularity);
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getQuiz(from, to, granularity),
                "Quiz analytics retrieved successfully"
        ));
    }
}
