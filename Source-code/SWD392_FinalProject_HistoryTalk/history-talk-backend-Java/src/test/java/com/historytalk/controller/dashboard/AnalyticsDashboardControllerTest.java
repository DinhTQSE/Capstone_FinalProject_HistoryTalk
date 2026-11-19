package com.historytalk.controller.dashboard;

import com.historytalk.dto.dashboard.*;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.dashboard.AnalyticsDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsDashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnalyticsDashboardService analyticsDashboardService;

    @InjectMocks
    private AnalyticsDashboardController controller;

    private UserPrincipal adminPrincipal;
    private UUID schoolId;
    private UUID classId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        classId = UUID.randomUUID();

        School school = School.builder()
                .id(schoolId)
                .name("THPT Chu Văn An")
                .schoolCode("CVA")
                .build();

        User user = User.builder()
                .uid(UUID.randomUUID())
                .userName("test_user")
                .school(school)
                .role(UserRole.SCHOOL_ADMIN)
                .build();

        adminPrincipal = new UserPrincipal(user);

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return adminPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/dashboard - Success returns 200 OK and metrics")
    void getSchoolAdminDashboard_Success() throws Exception {
        SchoolAdminDashboardResponse response = SchoolAdminDashboardResponse.builder()
                .schoolId(schoolId)
                .schoolName("THPT Chu Văn An")
                .schoolCode("CVA")
                .totalClassrooms(15L)
                .totalTeachers(30L)
                .totalStudents(600L)
                .totalSchoolTokenQuota(100000)
                .unallocatedTokenQuota(30000)
                .allocatedTokens(70000)
                .tokensUsedByStudents(25000L)
                .build();

        when(analyticsDashboardService.getSchoolAdminDashboard(schoolId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/school-admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.schoolName").value("THPT Chu Văn An"))
                .andExpect(jsonPath("$.data.totalClassrooms").value(15))
                .andExpect(jsonPath("$.data.totalStudents").value(600))
                .andExpect(jsonPath("$.data.allocatedTokens").value(70000));
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/dashboard/overview - Success returns 200 OK with local characters & school token overview")
    void getSchoolAdminOverview_Success() throws Exception {
        DashboardOverviewResponse response = DashboardOverviewResponse.builder()
                .users(DashboardOverviewResponse.UserOverview.builder().total(631L).active(631L).build())
                .roles(DashboardOverviewResponse.RoleOverview.builder().schoolAdmins(1L).teachers(30L).schoolStudents(600L).build())
                .content(DashboardOverviewResponse.ContentOverview.builder().historicalContexts(3L).characters(4L).documents(5L).build())
                .chat(DashboardOverviewResponse.ChatOverview.builder().sessions(50L).messages(200L).messagesToday(20L).build())
                .topCharacters(java.util.List.of(
                        DashboardOverviewResponse.TopCharacterOverview.builder()
                                .characterId(UUID.randomUUID().toString())
                                .name("Nhân vật Địa phương")
                                .totalMessages(40L)
                                .build()
                ))
                .topGlobalCharacters(java.util.List.of())
                .topLocalCharacters(java.util.List.of(
                        DashboardOverviewResponse.TopCharacterOverview.builder()
                                .characterId(UUID.randomUUID().toString())
                                .name("Nhân vật Địa phương")
                                .totalMessages(40L)
                                .build()
                ))
                .schoolTokenOverview(DashboardOverviewResponse.SchoolTokenOverview.builder()
                        .schoolId(schoolId)
                        .schoolName("THPT Chu Văn An")
                        .totalSchoolTokenQuota(100000)
                        .unallocatedTokenQuota(30000)
                        .allocatedTokens(70000)
                        .build())
                .build();

        when(analyticsDashboardService.getSchoolAdminOverview(schoolId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/school-admin/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.users.total").value(631))
                .andExpect(jsonPath("$.data.roles.schoolStudents").value(600))
                .andExpect(jsonPath("$.data.topGlobalCharacters").isEmpty())
                .andExpect(jsonPath("$.data.topLocalCharacters[0].name").value("Nhân vật Địa phương"))
                .andExpect(jsonPath("$.data.schoolTokenOverview.totalSchoolTokenQuota").value(100000));
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/dashboard/users - Success returns 200 OK")
    void getSchoolAdminUserAnalytics_Success() throws Exception {
        DashboardUserAnalyticsResponse response = DashboardUserAnalyticsResponse.builder()
                .summary(DashboardUserAnalyticsResponse.UserSummary.builder().total(631L).active(631L).build())
                .byRole(java.util.List.of(
                        DashboardUserAnalyticsResponse.RoleCount.builder().role("SCHOOL_STUDENT").count(600L).build()
                ))
                .trend(Collections.emptyList())
                .build();

        when(analyticsDashboardService.getSchoolAdminUserAnalytics(eq(schoolId), any(), any(), eq("day")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/school-admin/dashboard/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.total").value(631))
                .andExpect(jsonPath("$.data.byRole[0].role").value("SCHOOL_STUDENT"));
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/dashboard/content - Success returns 200 OK")
    void getSchoolAdminContentSummary_Success() throws Exception {
        DashboardContentSummaryResponse response = DashboardContentSummaryResponse.builder()
                .historicalContexts(DashboardContentSummaryResponse.InventoryStats.builder().total(3L).published(2L).active(2L).build())
                .characters(DashboardContentSummaryResponse.InventoryStats.builder().total(4L).published(3L).active(3L).build())
                .documents(DashboardContentSummaryResponse.DocumentStats.builder().total(6L).active(6L).build())
                .build();

        when(analyticsDashboardService.getSchoolAdminContentSummary(schoolId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/school-admin/dashboard/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.historicalContexts.total").value(3))
                .andExpect(jsonPath("$.data.characters.total").value(4));
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/dashboard/chat-activity - Success returns 200 OK")
    void getSchoolAdminChatActivity_Success() throws Exception {
        DashboardChatActivityResponse response = DashboardChatActivityResponse.builder()
                .summary(DashboardChatActivityResponse.ChatSummary.builder().sessions(20L).activeSessions(2L).messages(80L).build())
                .trend(Collections.emptyList())
                .build();

        when(analyticsDashboardService.getSchoolAdminChatActivity(eq(schoolId), any(), any(), eq("day")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/school-admin/dashboard/chat-activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.activeSessions").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/dashboard/quiz - Success returns 200 OK")
    void getSchoolAdminQuizAnalytics_Success() throws Exception {
        DashboardQuizAnalyticsResponse response = DashboardQuizAnalyticsResponse.builder()
                .summary(DashboardQuizAnalyticsResponse.QuizSummary.builder().totalQuizzes(5L).publishedQuizzes(4L).build())
                .sessionsTrend(Collections.emptyList())
                .topQuizzes(Collections.emptyList())
                .topWrongQuestions(Collections.emptyList())
                .build();

        when(analyticsDashboardService.getSchoolAdminQuizAnalytics(eq(schoolId), any(), any(), eq("day")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/school-admin/dashboard/quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.totalQuizzes").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/dashboard/tokens - Success returns 200 OK with school token management")
    void getSchoolAdminTokenAnalytics_Success() throws Exception {
        DashboardTokenUsageResponse response = DashboardTokenUsageResponse.builder()
                .summary(DashboardTokenUsageResponse.TokenSummary.builder().totalTokens(30000L).remainingTokens(70000L).build())
                .schoolTokenManagement(DashboardTokenUsageResponse.SchoolTokenManagement.builder()
                        .schoolId(schoolId)
                        .schoolName("THPT Chu Văn An")
                        .totalSchoolTokenQuota(100000)
                        .unallocatedTokenQuota(70000)
                        .allocatedTokens(30000)
                        .build())
                .trend(Collections.emptyList())
                .topUsersByTokenUsage(Collections.emptyList())
                .build();

        when(analyticsDashboardService.getSchoolAdminTokenAnalytics(eq(schoolId), any(), any(), eq("day")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/school-admin/dashboard/tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.totalTokens").value(30000))
                .andExpect(jsonPath("$.data.schoolTokenManagement.totalSchoolTokenQuota").value(100000));
    }

    @Test
    @DisplayName("GET /api/v1/teacher/dashboard/classrooms/{classId} - Success returns 200 OK")
    void getTeacherClassroomDashboard_Success() throws Exception {
        TeacherClassroomDashboardResponse response = TeacherClassroomDashboardResponse.builder()
                .classId(classId)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .studentCount(35L)
                .totalAiMessages(140L)
                .avgAiMessagesPerStudent(4.0)
                .students(Collections.emptyList())
                .build();

        when(analyticsDashboardService.getTeacherClassroomDashboard(eq(classId), any(UserPrincipal.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/teacher/dashboard/classrooms/{classId}", classId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.className").value("10A1"))
                .andExpect(jsonPath("$.data.classCode").value("CVA-10A1-2026"))
                .andExpect(jsonPath("$.data.studentCount").value(35))
                .andExpect(jsonPath("$.data.totalAiMessages").value(140));
    }

    @Test
    @DisplayName("GET /api/v1/student/dashboard - Success returns 200 OK")
    void getStudentDashboard_Success() throws Exception {
        StudentPersonalDashboardResponse response = StudentPersonalDashboardResponse.builder()
                .studentId(UUID.randomUUID())
                .studentName("Trần Quốc Dinh")
                .studentCode("HS1001")
                .schoolName("THPT Chu Văn An")
                .currentTokens(5000)
                .tokensUsedToday(200L)
                .totalQuizzesAttempted(10L)
                .averageScorePercentage(88.5)
                .recentQuizzes(Collections.emptyList())
                .enrolledClassrooms(Collections.emptyList())
                .upcomingAssignments(Collections.emptyList())
                .build();

        when(analyticsDashboardService.getStudentDashboard(any(UserPrincipal.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/student/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentName").value("Trần Quốc Dinh"))
                .andExpect(jsonPath("$.data.studentCode").value("HS1001"))
                .andExpect(jsonPath("$.data.currentTokens").value(5000))
                .andExpect(jsonPath("$.data.averageScorePercentage").value(88.5));
    }

    @Test
    @DisplayName("GET /api/v1/customer/dashboard - Success returns 200 OK")
    void getCustomerDashboard_Success() throws Exception {
        CustomerPersonalDashboardResponse response = CustomerPersonalDashboardResponse.builder()
                .uid(UUID.randomUUID())
                .userName("customer_test")
                .email("cust@test.com")
                .currentTier("PLUS")
                .currentTokens(3000)
                .tierExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(15))
                .totalQuizzesAttempted(15L)
                .averageScorePercentage(75.0)
                .totalAiMessages(90L)
                .recentQuizzes(Collections.emptyList())
                .topCharacters(Collections.emptyList())
                .paymentHistory(Collections.emptyList())
                .build();

        when(analyticsDashboardService.getCustomerDashboard(any(UserPrincipal.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/customer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentTier").value("PLUS"))
                .andExpect(jsonPath("$.data.currentTokens").value(3000))
                .andExpect(jsonPath("$.data.averageScorePercentage").value(75.0));
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard - Success returns 200 OK")
    void getSystemAdminDashboard_Success() throws Exception {
        SystemAdminComprehensiveDashboardResponse response = SystemAdminComprehensiveDashboardResponse.builder()
                .jvmHealth(SystemAdminComprehensiveDashboardResponse.JvmHealthMetrics.builder()
                        .cpuUsagePercentage(15.2)
                        .totalMemoryMb(1024L)
                        .usedMemoryMb(512L)
                        .activeThreadCount(24)
                        .uptimeSeconds(7200L)
                        .build())
                .tokenSummary(SystemAdminComprehensiveDashboardResponse.TokenConsumptionSummary.builder()
                        .totalTokensB2bAllocated(500000L)
                        .totalTokensB2cConsumed(150000L)
                        .totalTokensSystemConsumed(400000L)
                        .build())
                .topWrongQuestions(Collections.emptyList())
                .platformOverview(SystemAdminComprehensiveDashboardResponse.PlatformOverview.builder()
                        .totalSchools(10L)
                        .totalUsers(2000L)
                        .totalQuizzes(50L)
                        .totalRevenue(30000000L)
                        .build())
                .build();

        when(analyticsDashboardService.getSystemAdminDashboard()).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jvmHealth.cpuUsagePercentage").value(15.2))
                .andExpect(jsonPath("$.data.tokenSummary.totalTokensB2bAllocated").value(500000))
                .andExpect(jsonPath("$.data.platformOverview.totalSchools").value(10))
                .andExpect(jsonPath("$.data.platformOverview.totalRevenue").value(30000000));
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/analytics/classrooms - Success returns 200 OK")
    void getClassroomsTokenUsage_Success() throws Exception {
        ClassroomTokenUsageSummaryDto dto = ClassroomTokenUsageSummaryDto.builder()
                .classroomId(classId)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .gradeLevel(10)
                .academicYear(2026)
                .status("ACTIVE")
                .teacherName("Thầy A")
                .studentCount(35L)
                .tokensUsedTotal(12000L)
                .tokensUsedToday(800L)
                .percentageOfSchoolUsage(45.5)
                .build();

        com.historytalk.dto.PaginatedResponse<ClassroomTokenUsageSummaryDto> paginated =
                com.historytalk.dto.PaginatedResponse.<ClassroomTokenUsageSummaryDto>builder()
                        .content(java.util.List.of(dto))
                        .totalElements(1L)
                        .totalPages(1)
                        .currentPage(0)
                        .pageSize(10)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build();

        when(analyticsDashboardService.getClassroomsTokenUsage(any(), any(), any(), any(), any(), any()))
                .thenReturn(paginated);

        mockMvc.perform(get("/api/v1/school-admin/analytics/classrooms")
                        .param("academicYear", "2026")
                        .param("gradeLevel", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].className").value("10A1"))
                .andExpect(jsonPath("$.data[0].tokensUsedTotal").value(12000))
                .andExpect(jsonPath("$.data[0].percentageOfSchoolUsage").value(45.5));
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/analytics/classrooms/{classroomId}/students - Success returns 200 OK")
    void getClassroomStudentsTokenUsage_Success() throws Exception {
        StudentTokenUsageDetailDto dto = StudentTokenUsageDetailDto.builder()
                .studentId(UUID.randomUUID())
                .studentCode("HS001")
                .fullName("Nguyễn Văn An")
                .userName("cva_hs001")
                .tokensUsedTotal(4500L)
                .tokensUsedToday(250L)
                .dailyTokenLimit(1000L)
                .dailyBonusTokens(100L)
                .effectiveRemainingToday(850L)
                .messageCount(15L)
                .build();

        com.historytalk.dto.PaginatedResponse<StudentTokenUsageDetailDto> paginated =
                com.historytalk.dto.PaginatedResponse.<StudentTokenUsageDetailDto>builder()
                        .content(java.util.List.of(dto))
                        .totalElements(1L)
                        .totalPages(1)
                        .currentPage(0)
                        .pageSize(20)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build();

        when(analyticsDashboardService.getClassroomStudentsTokenUsage(eq(classId), any(), any(), any()))
                .thenReturn(paginated);

        mockMvc.perform(get("/api/v1/school-admin/analytics/classrooms/" + classId + "/students")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].studentCode").value("HS001"))
                .andExpect(jsonPath("$.data[0].tokensUsedTotal").value(4500))
                .andExpect(jsonPath("$.data[0].effectiveRemainingToday").value(850));
    }

    @Test
    @DisplayName("GET /api/v1/teacher/dashboard/classes-overview - Success returns 200 OK")
    void getTeacherClassesOverview_Success() throws Exception {
        TeacherClassesOverviewResponse response = TeacherClassesOverviewResponse.builder()
                .teacherUid(UUID.randomUUID())
                .teacherName("Thầy Nguyễn Văn B")
                .totalClassroomsManaged(2)
                .totalStudentsManaged(70)
                .totalTokensUsedByAllClasses(25000)
                .classrooms(java.util.List.of(
                        ClassroomTokenUsageSummaryDto.builder()
                                .classroomId(classId)
                                .className("10A1")
                                .studentCount(35)
                                .tokensUsedTotal(15000)
                                .build(),
                        ClassroomTokenUsageSummaryDto.builder()
                                .classroomId(UUID.randomUUID())
                                .className("10A2")
                                .studentCount(35)
                                .tokensUsedTotal(10000)
                                .build()
                ))
                .build();

        when(analyticsDashboardService.getTeacherClassesOverview(any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/teacher/dashboard/classes-overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.teacherName").value("Thầy Nguyễn Văn B"))
                .andExpect(jsonPath("$.data.totalClassroomsManaged").value(2))
                .andExpect(jsonPath("$.data.totalTokensUsedByAllClasses").value(25000))
                .andExpect(jsonPath("$.data.classrooms[0].className").value("10A1"));
    }
}
