package com.historytalk.service.dashboard;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.dashboard.*;
import com.historytalk.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface AnalyticsDashboardService {

    /**
     * US-SP6-06: Thống kê Dashboard trường học cho School Admin.
     */
    SchoolAdminDashboardResponse getSchoolAdminDashboard(UUID schoolId);

    /**
     * Sprint 10: Các API Dashboard chi tiết cho School Admin đồng bộ cấu trúc với System Admin Dashboard
     * (chỉ thống kê dữ liệu Local của trường & Quản lý Quỹ Token trường).
     */
    DashboardOverviewResponse getSchoolAdminOverview(UUID schoolId);

    DashboardUserAnalyticsResponse getSchoolAdminUserAnalytics(
            UUID schoolId, LocalDate from, LocalDate to, String granularity);

    DashboardContentSummaryResponse getSchoolAdminContentSummary(UUID schoolId);

    DashboardChatActivityResponse getSchoolAdminChatActivity(
            UUID schoolId, LocalDate from, LocalDate to, String granularity);

    DashboardQuizAnalyticsResponse getSchoolAdminQuizAnalytics(
            UUID schoolId, LocalDate from, LocalDate to, String granularity);

    DashboardTokenUsageResponse getSchoolAdminTokenAnalytics(
            UUID schoolId, LocalDate from, LocalDate to, String granularity);

    /**
     * Thống kê chi tiết mức độ tiêu thụ Token của tất cả các lớp trong trường cho School Admin.
     */
    PaginatedResponse<ClassroomTokenUsageSummaryDto> getClassroomsTokenUsage(
            UUID schoolId, Integer academicYear, Integer gradeLevel, String status, String search, Pageable pageable);

    /**
     * Drill-down xem chi tiết tiêu thụ Token của từng học sinh trong một lớp học.
     */
    PaginatedResponse<StudentTokenUsageDetailDto> getClassroomStudentsTokenUsage(
            UUID classroomId, String search, Pageable pageable, UserPrincipal principal);

    /**
     * US-SP6-07: Thống kê Dashboard lớp học & mức độ tương tác cho Giáo viên.
     */
    TeacherClassroomDashboardResponse getTeacherClassroomDashboard(UUID classId, UserPrincipal principal);

    /**
     * Thống kê tổng quan các lớp học dành cho Giáo viên phụ trách nhiều lớp.
     */
    TeacherClassesOverviewResponse getTeacherClassesOverview(UserPrincipal principal);

    /**
     * US-SP6-08: Thống kê Dashboard học tập cá nhân cho Học sinh.
     */
    StudentPersonalDashboardResponse getStudentDashboard(UserPrincipal principal);

    /**
     * US-SP6-09: Thống kê Dashboard cá nhân cho Khách hàng B2C.
     */
    CustomerPersonalDashboardResponse getCustomerDashboard(UserPrincipal principal);

    /**
     * US-SP6-10: Thống kê Dashboard toàn hệ thống & JVM Health cho System Admin.
     */
    SystemAdminComprehensiveDashboardResponse getSystemAdminDashboard();
}
