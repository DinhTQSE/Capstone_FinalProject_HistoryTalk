package com.historytalk.controller.school;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.*;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.TeacherManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SchoolAdminTeacherControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TeacherManagementService teacherManagementService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SchoolAdminTeacherController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserPrincipal adminPrincipal;
    private UUID schoolId;
    private UUID teacherId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        teacherId = UUID.randomUUID();

        School school = School.builder()
                .id(schoolId)
                .schoolCode("LHP-HCM")
                .name("THPT Lê Hồng Phong")
                .build();

        User adminUser = User.builder()
                .uid(UUID.randomUUID())
                .userName("lhp_admin")
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
    @DisplayName("GET /api/v1/school-admin/teachers - Returns paginated teachers with active filter")
    void getTeachers_Success() throws Exception {
        TeacherResponse teacherResponse = TeacherResponse.builder()
                .uid(teacherId)
                .userName("lhp_hcm_gv_1042")
                .fullName("Trần Thị Giáo Viên")
                .email("teacher@lhp.edu.vn")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        PaginatedResponse<TeacherResponse> paginatedResponse = PaginatedResponse.<TeacherResponse>builder()
                .content(List.of(teacherResponse))
                .currentPage(0)
                .pageSize(10)
                .totalElements(1L)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(teacherManagementService.getTeachers(eq(schoolId), eq("Giáo Viên"), eq(true), any(Pageable.class)))
                .thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/school-admin/teachers")
                        .param("search", "Giáo Viên")
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userName").value("lhp_hcm_gv_1042"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/school-admin/teachers/{id} - Successfully updates teacher profile")
    void updateTeacher_Success() throws Exception {
        UpdateTeacherRequest request = UpdateTeacherRequest.builder()
                .fullName("Trần Thị Giáo Viên Mới")
                .email("teacher.moi@lhp.edu.vn")
                .phoneNumber("0988112233")
                .build();

        TeacherResponse response = TeacherResponse.builder()
                .uid(teacherId)
                .userName("lhp_hcm_gv_1042")
                .fullName("Trần Thị Giáo Viên Mới")
                .email("teacher.moi@lhp.edu.vn")
                .phoneNumber("0988112233")
                .active(true)
                .build();

        when(teacherManagementService.updateTeacher(eq(teacherId), eq(schoolId), any(UpdateTeacherRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/school-admin/teachers/{id}", teacherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Trần Thị Giáo Viên Mới"))
                .andExpect(jsonPath("$.data.email").value("teacher.moi@lhp.edu.vn"));
    }

    @Test
    @DisplayName("PUT /api/v1/school-admin/teachers/{id}/status - Successfully updates teacher status")
    void updateTeacherStatus_Success() throws Exception {
        UpdateTeacherStatusRequest request = UpdateTeacherStatusRequest.builder()
                .active(false)
                .build();

        TeacherResponse response = TeacherResponse.builder()
                .uid(teacherId)
                .userName("lhp_hcm_gv_1042")
                .active(false)
                .build();

        when(teacherManagementService.updateTeacherStatus(eq(teacherId), eq(schoolId), eq(false)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/school-admin/teachers/{id}/status", teacherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/school-admin/teachers/{id} - Successfully soft deletes teacher")
    void deleteTeacher_Success() throws Exception {
        doNothing().when(teacherManagementService).deleteTeacher(teacherId, schoolId);

        mockMvc.perform(delete("/api/v1/school-admin/teachers/{id}", teacherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Teacher account deleted successfully"));

        verify(teacherManagementService).deleteTeacher(teacherId, schoolId);
    }

    @Test
    @DisplayName("POST /api/v1/school-admin/teachers/{id}/reset-password - Successfully resets password")
    void resetTeacherPassword_Success() throws Exception {
        ResetPasswordResponse response = ResetPasswordResponse.builder()
                .uid(teacherId)
                .userName("lhp_hcm_gv_1042")
                .fullName("Trần Thị Giáo Viên")
                .email("teacher@lhp.edu.vn")
                .initialPassword("Ht@m4P7nK2")
                .message("Teacher password reset successfully.")
                .build();

        when(teacherManagementService.resetTeacherPassword(teacherId, schoolId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/school-admin/teachers/{id}/reset-password", teacherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.initialPassword").value("Ht@m4P7nK2"))
                .andExpect(jsonPath("$.data.userName").value("lhp_hcm_gv_1042"));
    }
}
