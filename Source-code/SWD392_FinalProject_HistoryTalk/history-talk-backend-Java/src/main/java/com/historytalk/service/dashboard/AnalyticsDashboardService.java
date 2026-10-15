package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.*;
import com.historytalk.security.UserPrincipal;

import java.util.UUID;

public interface AnalyticsDashboardService {

    /**
     * US-SP6-06: Thống kê Dashboard trường học cho School Admin.
     */
    SchoolAdminDashboardResponse getSchoolAdminDashboard(UUID schoolId);

    /**
     * US-SP6-07: Thống kê Dashboard lớp học & mức độ tương tác cho Giáo viên.
     */
    TeacherClassroomDashboardResponse getTeacherClassroomDashboard(UUID classId, UserPrincipal principal);

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
