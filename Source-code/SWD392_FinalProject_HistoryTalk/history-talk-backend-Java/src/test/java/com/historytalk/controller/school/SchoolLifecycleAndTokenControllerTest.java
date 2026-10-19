package com.historytalk.controller.school;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.historytalk.dto.school.AcademicYearRolloverRequest;
import com.historytalk.dto.school.AcademicYearRolloverResponse;
import com.historytalk.dto.school.CreateTokenBoostRequestDto;
import com.historytalk.dto.school.DeactivateStudentRequest;
import com.historytalk.dto.school.RenewSchoolSubscriptionRequest;
import com.historytalk.dto.school.ReviewTokenBoostRequestDto;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.dto.school.TokenBoostRequestResponse;
import com.historytalk.dto.school.TopUpSchoolTokensRequest;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.enums.TokenBoostRequestStatus;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.AcademicLifecycleService;
import com.historytalk.service.school.token.TokenBoostRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SchoolLifecycleAndTokenControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AcademicLifecycleService academicLifecycleService;

    @Mock
    private TokenBoostRequestService tokenBoostRequestService;

    @InjectMocks
    private SchoolLifecycleAndTokenController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(UserPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return null;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(principalResolver)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/admin/schools/{id}/renew returns 200 OK")
    void renewSchoolSubscription_Returns200() throws Exception {
        UUID schoolId = UUID.randomUUID();
        RenewSchoolSubscriptionRequest request = RenewSchoolSubscriptionRequest.builder()
                .packageType(EnterprisePackage.ENTERPRISE_MEDIUM)
                .referenceDate(LocalDate.of(2026, 5, 10))
                .build();

        SchoolResponse mockResponse = SchoolResponse.builder()
                .id(schoolId)
                .name("THPT Chu Văn An")
                .renewedForNextYear(true)
                .nextPackageType(EnterprisePackage.ENTERPRISE_MEDIUM)
                .status(SchoolStatus.ACTIVE)
                .build();

        when(academicLifecycleService.renewSchoolSubscription(eq(schoolId), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/admin/schools/{id}/renew", schoolId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.renewedForNextYear").value(true))
                .andExpect(jsonPath("$.data.nextPackageType").value("ENTERPRISE_MEDIUM"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/schools/{id}/top-up returns 200 OK")
    void topUpSchoolTokens_Returns200() throws Exception {
        UUID schoolId = UUID.randomUUID();
        TopUpSchoolTokensRequest request = TopUpSchoolTokensRequest.builder()
                .additionalTokens(500_000)
                .note("Top-up HK2")
                .build();

        SchoolResponse mockResponse = SchoolResponse.builder()
                .id(schoolId)
                .totalSchoolTokenQuota(5_500_000)
                .unallocatedTokenQuota(1_500_000)
                .build();

        when(academicLifecycleService.topUpSchoolTokens(eq(schoolId), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/admin/schools/{id}/top-up", schoolId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSchoolTokenQuota").value(5500000));
    }

    @Test
    @DisplayName("POST /api/v1/student/token-requests returns 201 Created")
    void createTokenBoostRequest_Returns201() throws Exception {
        CreateTokenBoostRequestDto request = CreateTokenBoostRequestDto.builder()
                .requestedTokens(1000)
                .reason("Ôn tập lịch sử")
                .build();

        TokenBoostRequestResponse mockResponse = TokenBoostRequestResponse.builder()
                .id(UUID.randomUUID())
                .requestedTokens(1000)
                .status(TokenBoostRequestStatus.PENDING)
                .build();

        when(tokenBoostRequestService.createBoostRequest(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/student/token-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestedTokens").value(1000))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("PUT /api/v1/teacher/token-requests/{requestId}/review returns 200 OK")
    void reviewTokenBoostRequest_Returns200() throws Exception {
        UUID requestId = UUID.randomUUID();
        ReviewTokenBoostRequestDto reviewDto = ReviewTokenBoostRequestDto.builder()
                .action("APPROVE")
                .approvedTokens(1000)
                .reviewNote("Đồng ý")
                .build();

        TokenBoostRequestResponse mockResponse = TokenBoostRequestResponse.builder()
                .id(requestId)
                .approvedTokens(1000)
                .status(TokenBoostRequestStatus.APPROVED)
                .build();

        when(tokenBoostRequestService.reviewBoostRequest(any(), eq(requestId), any())).thenReturn(mockResponse);

        mockMvc.perform(put("/api/v1/teacher/token-requests/{requestId}/review", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedTokens").value(1000));
    }

    @Test
    @DisplayName("POST /api/v1/school-admin/academic-year/rollover returns 200 OK")
    void rolloverAcademicYear_Returns200() throws Exception {
        AcademicYearRolloverRequest request = AcademicYearRolloverRequest.builder()
                .completedAcademicYear(2025)
                .deactivateGraduatedGrade12(true)
                .build();

        AcademicYearRolloverResponse mockResponse = AcademicYearRolloverResponse.builder()
                .completedAcademicYear(2025)
                .archivedClassroomsCount(12)
                .deactivatedGraduatedStudentsCount(150)
                .reclaimedTokensToPool(45000L)
                .build();

        when(academicLifecycleService.rolloverAcademicYear(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/school-admin/academic-year/rollover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archivedClassroomsCount").value(12))
                .andExpect(jsonPath("$.data.deactivatedGraduatedStudentsCount").value(150));
    }

    @Test
    @DisplayName("PUT /api/v1/school-admin/students/{uid}/deactivate returns 200 OK")
    void deactivateStudentAccount_Returns200() throws Exception {
        UUID studentUid = UUID.randomUUID();
        DeactivateStudentRequest request = DeactivateStudentRequest.builder()
                .reason("GRADUATED")
                .build();

        StudentResponse mockResponse = StudentResponse.builder()
                .uid(studentUid)
                .active(false)
                .token(0)
                .build();

        when(academicLifecycleService.deactivateStudentAccount(any(), eq(studentUid), any())).thenReturn(mockResponse);

        mockMvc.perform(put("/api/v1/school-admin/students/{uid}/deactivate", studentUid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.token").value(0));
    }
}
