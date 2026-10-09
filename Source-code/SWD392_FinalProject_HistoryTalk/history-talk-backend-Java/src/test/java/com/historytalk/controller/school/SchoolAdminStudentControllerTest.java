package com.historytalk.controller.school;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.school.AllocateStudentTokenRequest;
import com.historytalk.dto.school.AllocateStudentTokenResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.student.StudentImportProcessor;
import com.historytalk.service.school.token.StudentTokenAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SchoolAdminStudentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentImportProcessor studentImportProcessor;

    @Mock
    private StudentTokenAllocationService studentTokenAllocationService;

    @InjectMocks
    private SchoolAdminStudentController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserPrincipal adminPrincipal;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();

        School school = School.builder()
                .id(schoolId)
                .schoolCode("CVA")
                .name("THPT Chu Văn An")
                .build();

        User adminUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_admin")
                .role(UserRole.SCHOOL_ADMIN)
                .school(school)
                .build();

        adminPrincipal = new UserPrincipal(adminUser);

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
                .setCustomArgumentResolvers(principalResolver)
                .build();
    }

    @Test
    @DisplayName("PUT /api/v1/school-admin/students/token-allocation - Success allocates tokens")
    void allocateTokens_Success() throws Exception {
        UUID studentId = UUID.randomUUID();
        AllocateStudentTokenRequest request = AllocateStudentTokenRequest.builder()
                .studentId(studentId)
                .tokenAmount(5000)
                .build();

        AllocateStudentTokenResponse response = AllocateStudentTokenResponse.builder()
                .schoolId(schoolId)
                .totalTokensAllocated(5000)
                .affectedStudentCount(1)
                .tokenAmountPerStudent(5000)
                .remainingSchoolTokens(45000)
                .message("Allocated 5,000 tokens to 1 students successfully")
                .build();

        when(studentTokenAllocationService.allocateTokens(eq(schoolId), any(AllocateStudentTokenRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/school-admin/students/token-allocation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTokensAllocated").value(5000))
                .andExpect(jsonPath("$.data.affectedStudentCount").value(1))
                .andExpect(jsonPath("$.data.remainingSchoolTokens").value(45000));
    }

    @Test
    @DisplayName("PUT /api/v1/school-admin/students/token-allocation - Fails when tokenAmount is null or <= 0")
    void allocateTokens_ValidationError_WhenInvalidTokenAmount() throws Exception {
        AllocateStudentTokenRequest invalidRequest = AllocateStudentTokenRequest.builder()
                .tokenAmount(0) // violates @Min(1)
                .build();

        mockMvc.perform(put("/api/v1/school-admin/students/token-allocation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/students/template - Downloads CSV template with UTF-8 BOM")
    void downloadTemplate_Success() throws Exception {
        mockMvc.perform(get("/api/v1/school-admin/students/template"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student_import_template.csv\""))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("student_code,full_name,email")));
    }
}
