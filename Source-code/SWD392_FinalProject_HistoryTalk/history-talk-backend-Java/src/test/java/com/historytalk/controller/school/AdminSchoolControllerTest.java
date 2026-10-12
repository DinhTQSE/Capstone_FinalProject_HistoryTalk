package com.historytalk.controller.school;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.*;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.service.school.SchoolManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminSchoolControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SchoolManagementService schoolManagementService;

    @InjectMocks
    private AdminSchoolController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID schoolId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        schoolId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Test
    @DisplayName("GET /api/v1/admin/schools/{schoolId}/school-admins - Returns paginated school admins")
    void getSchoolAdmins_Success() throws Exception {
        SchoolAdminResponse adminResponse = SchoolAdminResponse.builder()
                .uid(adminId)
                .userName("lhp_hcm_admin")
                .fullName("Nguyễn Văn Hiệu Trưởng")
                .email("admin@lhp.edu.vn")
                .role(UserRole.SCHOOL_ADMIN)
                .schoolId(schoolId)
                .schoolName("THPT Lê Hồng Phong")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        PaginatedResponse<SchoolAdminResponse> paginatedResponse = PaginatedResponse.<SchoolAdminResponse>builder()
                .content(List.of(adminResponse))
                .currentPage(0)
                .pageSize(10)
                .totalElements(1L)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(schoolManagementService.getSchoolAdmins(eq(schoolId), eq("Hiệu Trưởng"), eq(true), any(Pageable.class)))
                .thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/admin/schools/{schoolId}/school-admins", schoolId)
                        .param("search", "Hiệu Trưởng")
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userName").value("lhp_hcm_admin"))
                .andExpect(jsonPath("$.data[0].fullName").value("Nguyễn Văn Hiệu Trưởng"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/school-admins/{id} - Successfully updates school admin profile")
    void updateSchoolAdmin_Success() throws Exception {
        UpdateSchoolAdminRequest request = UpdateSchoolAdminRequest.builder()
                .fullName("Nguyễn Văn Hiệu Trưởng Cập Nhật")
                .email("hieutruong.moi@lhp.edu.vn")
                .phoneNumber("0988776655")
                .build();

        SchoolAdminResponse response = SchoolAdminResponse.builder()
                .uid(adminId)
                .userName("lhp_hcm_admin")
                .fullName("Nguyễn Văn Hiệu Trưởng Cập Nhật")
                .email("hieutruong.moi@lhp.edu.vn")
                .phoneNumber("0988776655")
                .role(UserRole.SCHOOL_ADMIN)
                .schoolId(schoolId)
                .active(true)
                .build();

        when(schoolManagementService.updateSchoolAdmin(eq(adminId), any(UpdateSchoolAdminRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/school-admins/{id}", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn Hiệu Trưởng Cập Nhật"))
                .andExpect(jsonPath("$.data.email").value("hieutruong.moi@lhp.edu.vn"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/school-admins/{id} - Validation error when invalid email")
    void updateSchoolAdmin_ValidationError() throws Exception {
        UpdateSchoolAdminRequest invalidRequest = UpdateSchoolAdminRequest.builder()
                .fullName("Nguyễn Văn A")
                .email("invalid-email")
                .build();

        mockMvc.perform(put("/api/v1/admin/school-admins/{id}", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/school-admins/{id}/status - Successfully updates status")
    void updateSchoolAdminStatus_Success() throws Exception {
        UpdateSchoolAdminStatusRequest request = UpdateSchoolAdminStatusRequest.builder()
                .active(false)
                .build();

        SchoolAdminResponse response = SchoolAdminResponse.builder()
                .uid(adminId)
                .userName("lhp_hcm_admin")
                .role(UserRole.SCHOOL_ADMIN)
                .active(false)
                .build();

        when(schoolManagementService.updateSchoolAdminStatus(eq(adminId), eq(false)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/school-admins/{id}/status", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/school-admins/{id} - Successfully soft deletes admin")
    void deleteSchoolAdmin_Success() throws Exception {
        doNothing().when(schoolManagementService).deleteSchoolAdmin(adminId);

        mockMvc.perform(delete("/api/v1/admin/school-admins/{id}", adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("School Admin account deleted successfully"));

        verify(schoolManagementService).deleteSchoolAdmin(adminId);
    }

    @Test
    @DisplayName("POST /api/v1/admin/school-admins/{id}/reset-password - Successfully resets password")
    void resetSchoolAdminPassword_Success() throws Exception {
        ResetPasswordResponse response = ResetPasswordResponse.builder()
                .uid(adminId)
                .userName("lhp_hcm_admin")
                .fullName("Nguyễn Văn Hiệu Trưởng")
                .email("admin@lhp.edu.vn")
                .initialPassword("Ht@a8B9cD1")
                .message("School Admin password reset successfully.")
                .build();

        when(schoolManagementService.resetSchoolAdminPassword(adminId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/school-admins/{id}/reset-password", adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.initialPassword").value("Ht@a8B9cD1"))
                .andExpect(jsonPath("$.data.userName").value("lhp_hcm_admin"));
    }
}
