package com.historytalk.controller.dashboard;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedApiResponse;
import com.historytalk.dto.PaginatedResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.RequestParam;
import com.historytalk.dto.dashboard.*;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.dashboard.AnalyticsDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Role-Based Dashboards & Analytics", description = "5 System Dashboards for School Admin, Teacher, Student, Customer, and System Admin (US-SP6-06 to US-SP6-10)")
public class AnalyticsDashboardController {

    private final AnalyticsDashboardService analyticsDashboardService;

    @GetMapping("/school-admin/dashboard")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "School Admin Dashboard", description = "Returns school scale, token quota allocation vs student consumption, and assignment completion rate (US-SP6-06)")
    public ResponseEntity<ApiResponse<SchoolAdminDashboardResponse>> getSchoolAdminDashboard(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/v1/school-admin/dashboard by user: {}", principal != null ? principal.getUsername() : "security-context");
        SchoolAdminDashboardResponse response = analyticsDashboardService.getSchoolAdminDashboard(principal != null ? principal.getSchoolId() : null);
        return ResponseEntity.ok(ApiResponse.success(response, "School Admin dashboard retrieved successfully"));
    }

    @GetMapping("/teacher/dashboard/classrooms/{classId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Teacher Classroom Analytics Dashboard", description = "Returns student gradebook, AI engagement analytics, and assignment submission rates for the specified classroom (US-SP6-07)")
    public ResponseEntity<ApiResponse<TeacherClassroomDashboardResponse>> getTeacherClassroomDashboard(
            @PathVariable UUID classId,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/v1/teacher/dashboard/classrooms/{} by teacher: {}", classId, principal != null ? principal.getUsername() : "security-context");
        TeacherClassroomDashboardResponse response = analyticsDashboardService.getTeacherClassroomDashboard(classId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Teacher classroom dashboard retrieved successfully"));
    }

    @GetMapping("/student/dashboard")
    @PreAuthorize("hasRole('SCHOOL_STUDENT')")
    @Operation(summary = "Student Personal Dashboard", description = "Returns student token balance, quiz performance, enrolled classrooms, and upcoming assignments (US-SP6-08)")
    public ResponseEntity<ApiResponse<StudentPersonalDashboardResponse>> getStudentDashboard(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/v1/student/dashboard by student: {}", principal != null ? principal.getUsername() : "security-context");
        StudentPersonalDashboardResponse response = analyticsDashboardService.getStudentDashboard(principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Student dashboard retrieved successfully"));
    }

    @GetMapping("/customer/dashboard")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Customer B2C Personal Dashboard", description = "Returns subscription tier, token balance, quiz learning history, top AI characters, and PayOS transaction history (US-SP6-09)")
    public ResponseEntity<ApiResponse<CustomerPersonalDashboardResponse>> getCustomerDashboard(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/v1/customer/dashboard by customer: {}", principal != null ? principal.getUsername() : "security-context");
        CustomerPersonalDashboardResponse response = analyticsDashboardService.getCustomerDashboard(principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Customer dashboard retrieved successfully"));
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "System Admin Comprehensive Dashboard", description = "Returns JVM health metrics, platform-wide token consumption, top wrong quiz questions, and platform revenue overview (US-SP6-10)")
    public ResponseEntity<ApiResponse<SystemAdminComprehensiveDashboardResponse>> getSystemAdminDashboard() {
        log.info("GET /api/v1/admin/dashboard");
        SystemAdminComprehensiveDashboardResponse response = analyticsDashboardService.getSystemAdminDashboard();
        return ResponseEntity.ok(ApiResponse.success(response, "System Admin dashboard retrieved successfully"));
    }

    @GetMapping("/school-admin/analytics/classrooms")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "School Admin Classroom Token Analytics", description = "Returns token consumption breakdown by classroom in the school with filters and pagination")
    public ResponseEntity<PaginatedApiResponse<ClassroomTokenUsageSummaryDto>> getClassroomsTokenUsage(
            @RequestParam(required = false) Integer academicYear,
            @RequestParam(required = false) Integer gradeLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @ParameterObject
            @PageableDefault(size = 10)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/v1/school-admin/analytics/classrooms by school admin: {}", principal != null ? principal.getUsername() : "security-context");
        PaginatedResponse<ClassroomTokenUsageSummaryDto> response = analyticsDashboardService.getClassroomsTokenUsage(
                principal != null ? principal.getSchoolId() : null, academicYear, gradeLevel, status, search, pageable);
        return ResponseEntity.ok(PaginatedApiResponse.of(response, "Classroom token analytics retrieved successfully"));
    }

    @GetMapping("/school-admin/analytics/classrooms/{classroomId}/students")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @Operation(summary = "Classroom Students Token Usage Drill-Down", description = "Returns individual student token usage details for the specified classroom")
    public ResponseEntity<PaginatedApiResponse<StudentTokenUsageDetailDto>> getClassroomStudentsTokenUsage(
            @PathVariable UUID classroomId,
            @RequestParam(required = false) String search,
            @ParameterObject
            @PageableDefault(size = 20)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/v1/school-admin/analytics/classrooms/{}/students by user: {}", classroomId, principal != null ? principal.getUsername() : "security-context");
        PaginatedResponse<StudentTokenUsageDetailDto> response = analyticsDashboardService.getClassroomStudentsTokenUsage(
                classroomId, search, pageable, principal);
        return ResponseEntity.ok(PaginatedApiResponse.of(response, "Classroom students token usage retrieved successfully"));
    }

    @GetMapping("/teacher/dashboard/classes-overview")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Teacher Classes Overview Analytics", description = "Returns summary of token usage across all classrooms managed by the teacher")
    public ResponseEntity<ApiResponse<TeacherClassesOverviewResponse>> getTeacherClassesOverview(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/v1/teacher/dashboard/classes-overview by teacher: {}", principal != null ? principal.getUsername() : "security-context");
        TeacherClassesOverviewResponse response = analyticsDashboardService.getTeacherClassesOverview(principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Teacher classes overview retrieved successfully"));
    }

}
