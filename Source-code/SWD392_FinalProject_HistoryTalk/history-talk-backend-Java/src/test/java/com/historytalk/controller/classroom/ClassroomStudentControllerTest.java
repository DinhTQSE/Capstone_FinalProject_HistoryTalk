package com.historytalk.controller.classroom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.BatchEnrollResultResponse;
import com.historytalk.dto.classroom.BatchEnrollStudentRequest;
import com.historytalk.dto.classroom.ClassroomStudentResponse;
import com.historytalk.dto.classroom.TransferStudentRequest;
import com.historytalk.dto.school.ImportedStudentAccountDto;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.classroom.ClassroomStudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ClassroomStudentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClassroomStudentService classroomStudentService;

    @InjectMocks
    private ClassroomStudentController classroomStudentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserPrincipal adminPrincipal;
    private UUID classId;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        classId = UUID.randomUUID();
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

        mockMvc = MockMvcBuilders.standaloneSetup(classroomStudentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{classId}/students/batch - Gán thành công 2 học sinh vào lớp")
    void testBatchEnrollStudents_Success() throws Exception {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        BatchEnrollStudentRequest req = BatchEnrollStudentRequest.builder()
                .studentIds(List.of(s1, s2))
                .build();

        BatchEnrollResultResponse res = BatchEnrollResultResponse.builder()
                .classroomId(classId)
                .className("10A1")
                .totalRequested(2)
                .successCount(2)
                .alreadyEnrolledCount(0)
                .currentStudentCount(38)
                .notes(Collections.emptyList())
                .build();

        when(classroomStudentService.enrollStudentsBatch(eq(classId), any(BatchEnrollStudentRequest.class), any(UserPrincipal.class)))
                .thenReturn(res);

        mockMvc.perform(post("/api/v1/classrooms/{classId}/students/batch", classId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Thêm học sinh vào lớp học thành công"))
                .andExpect(jsonPath("$.data.totalRequested").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.currentStudentCount").value(38));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{classId}/students/batch - Validation Error khi mảng studentIds rỗng")
    void testBatchEnrollStudents_ValidationError() throws Exception {
        BatchEnrollStudentRequest emptyReq = BatchEnrollStudentRequest.builder()
                .studentIds(Collections.emptyList())
                .build();

        mockMvc.perform(post("/api/v1/classrooms/{classId}/students/batch", classId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{classId}/students/import-csv - Upload CSV gán học sinh vào lớp thành công")
    void testImportClassroomStudents_Success() throws Exception {
        String csvContent = "student_code,full_name,email,dob,gender,phone_number,password,additional_token\n"
                + "HS260101,Trần Bảo Nam,,2009-05-15,MALE,0912345601,,0\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        StudentImportResultDto result = StudentImportResultDto.builder()
                .totalRows(1)
                .successCount(1)
                .failureCount(0)
                .remainingSchoolTokens(45000)
                .errors(Collections.emptyList())
                .successfulAccounts(List.of(
                        ImportedStudentAccountDto.builder()
                                .studentCode("HS260101")
                                .fullName("Trần Bảo Nam")
                                .allocatedToken(0)
                                .initialPassword("(Tài khoản hiện có)")
                                .build()
                ))
                .build();

        when(classroomStudentService.importClassroomStudents(eq(classId), any(), any(), any(UserPrincipal.class)))
                .thenReturn(result);

        mockMvc.perform(multipart("/api/v1/classrooms/{classId}/students/import-csv", classId)
                        .file(file)
                        .param("defaultInitialToken", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(0))
                .andExpect(jsonPath("$.data.successfulAccounts[0].studentCode").value("HS260101"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{classId}/students - Lấy danh sách học sinh theo lớp thành công")
    void testGetClassroomStudents_Success() throws Exception {
        ClassroomStudentResponse studentDto = ClassroomStudentResponse.builder()
                .studentId(UUID.randomUUID())
                .studentCode("HS260101")
                .userName("cva_hs_hs260101")
                .fullName("Trần Bảo Nam")
                .email("nam@cva.edu.vn")
                .token(10000)
                .status("ACTIVE")
                .build();

        PaginatedResponse<ClassroomStudentResponse> paginatedResponse = PaginatedResponse.<ClassroomStudentResponse>builder()
                .content(List.of(studentDto))
                .currentPage(0)
                .pageSize(10)
                .totalElements(1L)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(classroomStudentService.getClassroomStudents(eq(classId), any(), any(Pageable.class), any(UserPrincipal.class)))
                .thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/classrooms/{classId}/students", classId)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].studentCode").value("HS260101"))
                .andExpect(jsonPath("$.data[0].fullName").value("Trần Bảo Nam"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{classId}/available-students - Lấy danh sách học sinh khả dụng thành công")
    void testGetAvailableStudents_Success() throws Exception {
        StudentResponse studentDto = StudentResponse.builder()
                .uid(UUID.randomUUID())
                .studentCode("HS260102")
                .userName("cva_hs_hs260102")
                .fullName("Lê Thảo Vy")
                .email("vy@cva.edu.vn")
                .token(10000)
                .active(true)
                .build();

        PaginatedResponse<StudentResponse> paginatedResponse = PaginatedResponse.<StudentResponse>builder()
                .content(List.of(studentDto))
                .currentPage(0)
                .pageSize(10)
                .totalElements(1L)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(classroomStudentService.getAvailableStudents(eq(classId), any(), any(Pageable.class), any(UserPrincipal.class)))
                .thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/classrooms/{classId}/available-students", classId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].studentCode").value("HS260102"))
                .andExpect(jsonPath("$.data[0].fullName").value("Lê Thảo Vy"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{classId}/students/{studentId} - Gỡ học sinh khỏi lớp thành công")
    void testRemoveStudentFromClass_Success() throws Exception {
        UUID studentId = UUID.randomUUID();
        doNothing().when(classroomStudentService).removeStudentFromClass(eq(classId), eq(studentId), any(UserPrincipal.class));

        mockMvc.perform(delete("/api/v1/classrooms/{classId}/students/{studentId}", classId, studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Gỡ học sinh khỏi lớp học thành công"));

        verify(classroomStudentService).removeStudentFromClass(eq(classId), eq(studentId), any(UserPrincipal.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{classId}/students/{studentId} - Trả về 404 khi học sinh không có trong lớp")
    void testRemoveStudentFromClass_NotFound() throws Exception {
        UUID studentId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Học sinh không có trong lớp học này"))
                .when(classroomStudentService).removeStudentFromClass(eq(classId), eq(studentId), any(UserPrincipal.class));

        mockMvc.perform(delete("/api/v1/classrooms/{classId}/students/{studentId}", classId, studentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Học sinh không có trong lớp học này"));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{fromClassId}/students/transfer - Chuyển lớp thành công")
    void testTransferStudents_Success() throws Exception {
        UUID toClassId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        TransferStudentRequest req = TransferStudentRequest.builder()
                .targetClassroomId(toClassId)
                .studentIds(List.of(studentId))
                .build();

        BatchEnrollResultResponse res = BatchEnrollResultResponse.builder()
                .classroomId(toClassId)
                .className("10A2")
                .totalRequested(1)
                .successCount(1)
                .currentStudentCount(35)
                .notes(Collections.emptyList())
                .build();

        when(classroomStudentService.transferStudents(eq(classId), any(TransferStudentRequest.class), any(UserPrincipal.class)))
                .thenReturn(res);

        mockMvc.perform(post("/api/v1/classrooms/{fromClassId}/students/transfer", classId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Chuyển lớp học sinh thành công"))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.className").value("10A2"));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{fromClassId}/students/transfer - Trả về 400 khi lớp đích trùng lớp hiện tại")
    void testTransferStudents_SameTargetClass_Returns400() throws Exception {
        TransferStudentRequest req = TransferStudentRequest.builder()
                .targetClassroomId(classId)
                .studentIds(List.of(UUID.randomUUID()))
                .build();

        when(classroomStudentService.transferStudents(eq(classId), any(TransferStudentRequest.class), any(UserPrincipal.class)))
                .thenThrow(new InvalidRequestException("Lớp học đích không được trùng với lớp học hiện tại"));

        mockMvc.perform(post("/api/v1/classrooms/{fromClassId}/students/transfer", classId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Lớp học đích không được trùng với lớp học hiện tại"));
    }

    @Test
    @DisplayName("Access Denied: Trả về 403 khi giáo viên không được phụ trách lớp")
    void testAccessDenied_Returns403() throws Exception {
        when(classroomStudentService.enrollStudentsBatch(eq(classId), any(BatchEnrollStudentRequest.class), any(UserPrincipal.class)))
                .thenThrow(new AccessDeniedException("Bạn không được phân công phụ trách lớp học này!"));

        BatchEnrollStudentRequest req = BatchEnrollStudentRequest.builder()
                .studentIds(List.of(UUID.randomUUID()))
                .build();

        mockMvc.perform(post("/api/v1/classrooms/{classId}/students/batch", classId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Bạn không được phân công phụ trách lớp học này!"));
    }
}
