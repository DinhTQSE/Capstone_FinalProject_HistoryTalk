package com.historytalk.controller.classroom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.ClassroomResponse;
import com.historytalk.dto.classroom.CreateClassroomRequest;
import com.historytalk.dto.classroom.TeacherSummaryDto;
import com.historytalk.dto.classroom.UpdateClassroomRequest;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.classroom.ClassroomService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ClassroomControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClassroomService classroomService;

    @InjectMocks
    private ClassroomController classroomController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserPrincipal adminPrincipal;
    private UUID schoolId;
    private UUID classroomId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        classroomId = UUID.randomUUID();

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

        // Custom resolver for @AuthenticationPrincipal UserPrincipal
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

        mockMvc = MockMvcBuilders.standaloneSetup(classroomController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/classrooms - Success creates classroom and returns 201 Created")
    void createClassroom_Success() throws Exception {
        CreateClassroomRequest request = CreateClassroomRequest.builder()
                .className("10A1")
                .gradeLevel(10)
                .academicYear(2026)
                .description("Lớp 10A1")
                .build();

        ClassroomResponse response = ClassroomResponse.builder()
                .id(classroomId)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .gradeLevel(10)
                .academicYear(2026)
                .status("ACTIVE")
                .studentCount(0L)
                .build();

        when(classroomService.createClassroom(any(CreateClassroomRequest.class), any(UserPrincipal.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(classroomId.toString()))
                .andExpect(jsonPath("$.data.className").value("10A1"))
                .andExpect(jsonPath("$.data.classCode").value("CVA-10A1-2026"));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms - Validation error returns 400 Bad Request")
    void createClassroom_ValidationError_Returns400() throws Exception {
        // Missing required className, gradeLevel, academicYear
        CreateClassroomRequest invalidRequest = CreateClassroomRequest.builder().build();

        mockMvc.perform(post("/api/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id} - Success returns classroom details")
    void getClassroomById_Success() throws Exception {
        ClassroomResponse response = ClassroomResponse.builder()
                .id(classroomId)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .gradeLevel(10)
                .academicYear(2026)
                .status("ACTIVE")
                .studentCount(35L)
                .teacher(TeacherSummaryDto.builder()
                        .fullName("Nguyễn Thị Mai")
                        .build())
                .build();

        when(classroomService.getClassroomById(eq(classroomId), any(UserPrincipal.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/classrooms/{id}", classroomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(classroomId.toString()))
                .andExpect(jsonPath("$.data.studentCount").value(35))
                .andExpect(jsonPath("$.data.teacher.fullName").value("Nguyễn Thị Mai"));
    }

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id} - Success updates classroom and returns 200")
    void updateClassroom_Success() throws Exception {
        UpdateClassroomRequest request = UpdateClassroomRequest.builder()
                .className("10A1 Chuyên")
                .build();

        ClassroomResponse response = ClassroomResponse.builder()
                .id(classroomId)
                .className("10A1 Chuyên")
                .classCode("CVA-10A1-2026")
                .build();

        when(classroomService.updateClassroom(eq(classroomId), any(UpdateClassroomRequest.class), any(UserPrincipal.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/classrooms/{id}", classroomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.className").value("10A1 Chuyên"));
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{id} - Success returns 200 OK")
    void deleteClassroom_Success() throws Exception {
        doNothing().when(classroomService).deleteClassroom(eq(classroomId), any(UserPrincipal.class));

        mockMvc.perform(delete("/api/v1/classrooms/{id}", classroomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(classroomService, times(1)).deleteClassroom(eq(classroomId), any(UserPrincipal.class));
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/classrooms - Success returns paginated classrooms")
    void getSchoolClassrooms_Success() throws Exception {
        ClassroomResponse item = ClassroomResponse.builder()
                .id(classroomId)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .build();

        PaginatedResponse<ClassroomResponse> paginated = PaginatedResponse.<ClassroomResponse>builder()
                .content(List.of(item))
                .currentPage(0)
                .pageSize(10)
                .totalElements(1L)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(classroomService.getSchoolClassrooms(any(), any(), any(), any(Pageable.class), any(UserPrincipal.class)))
                .thenReturn(paginated);

        mockMvc.perform(get("/api/v1/school-admin/classrooms")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].className").value("10A1"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/teacher/classrooms - Success returns teacher classrooms")
    void getTeacherClassrooms_Success() throws Exception {
        PaginatedResponse<ClassroomResponse> paginated = PaginatedResponse.<ClassroomResponse>builder()
                .content(Collections.emptyList())
                .currentPage(0)
                .pageSize(10)
                .totalElements(0L)
                .totalPages(0)
                .build();

        when(classroomService.getTeacherClassrooms(any(), any(Pageable.class), any(UserPrincipal.class)))
                .thenReturn(paginated);

        mockMvc.perform(get("/api/v1/teacher/classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/student/classrooms - Success returns student classrooms")
    void getStudentClassrooms_Success() throws Exception {
        ClassroomResponse item = ClassroomResponse.builder()
                .id(classroomId)
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .build();

        when(classroomService.getStudentClassrooms(any(UserPrincipal.class)))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/student/classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].className").value("10A1"));
    }
}
