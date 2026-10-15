package com.historytalk.controller.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.assignment.AssignmentGradebookResponse;
import com.historytalk.dto.assignment.CreateAssignmentRequest;
import com.historytalk.dto.assignment.TeacherAssignmentResponse;
import com.historytalk.dto.assignment.UpdateAssignmentRequest;
import com.historytalk.entity.enums.AssignmentStatus;
import com.historytalk.entity.enums.AssignmentType;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.assignment.TeacherAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

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
class TeacherAssignmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TeacherAssignmentService teacherAssignmentService;

    @InjectMocks
    private TeacherAssignmentController teacherAssignmentController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UserPrincipal teacherPrincipal;
    private UUID classroomId;
    private UUID assignmentId;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();

        com.historytalk.entity.school.School school = com.historytalk.entity.school.School.builder()
                .id(UUID.randomUUID()).build();
        com.historytalk.entity.user.User teacherUser = com.historytalk.entity.user.User.builder()
                .uid(UUID.randomUUID())
                .school(school)
                .role(com.historytalk.entity.enums.UserRole.TEACHER)
                .email("teacher@school.edu.vn")
                .build();
        teacherPrincipal = new UserPrincipal(teacherUser);

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return teacherPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(teacherAssignmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/teacher/assignments - Tạo bài tập thành công trả về 201 Created")
    void testCreateAssignment_Success() throws Exception {
        CreateAssignmentRequest req = CreateAssignmentRequest.builder()
                .classId(classroomId)
                .title("Tìm hiểu Trận Bạch Đằng")
                .assignmentType(AssignmentType.CONTEXT_READING)
                .targetId(UUID.randomUUID())
                .deadline(LocalDateTime.now().plusDays(2))
                .minRequirement(60)
                .build();

        TeacherAssignmentResponse resp = TeacherAssignmentResponse.builder()
                .assignmentId(assignmentId)
                .classroomId(classroomId)
                .title("Tìm hiểu Trận Bạch Đằng")
                .status(AssignmentStatus.ACTIVE)
                .build();

        when(teacherAssignmentService.createAssignment(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/teacher/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.title").value("Tìm hiểu Trận Bạch Đằng"));
    }

    @Test
    @DisplayName("GET /api/v1/teacher/assignments/classroom/{classroomId} - Lấy danh sách bài tập lớp")
    void testGetAssignmentsByClassroom_Success() throws Exception {
        TeacherAssignmentResponse item = TeacherAssignmentResponse.builder()
                .assignmentId(assignmentId)
                .title("Bài tập 1")
                .build();

        PaginatedResponse<TeacherAssignmentResponse> pageResp = PaginatedResponse.<TeacherAssignmentResponse>builder()
                .content(List.of(item))
                .currentPage(0)
                .pageSize(20)
                .totalElements(1L)
                .totalPages(1)
                .build();

        when(teacherAssignmentService.getAssignmentsByClassroom(eq(classroomId), any(), any(), any()))
                .thenReturn(pageResp);

        mockMvc.perform(get("/api/v1/teacher/assignments/classroom/{classroomId}", classroomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].assignmentId").value(assignmentId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/teacher/assignments/{assignmentId} - Xem Gradebook bài tập")
    void testGetAssignmentGradebook_Success() throws Exception {
        AssignmentGradebookResponse gradebook = AssignmentGradebookResponse.builder()
                .assignmentId(assignmentId)
                .title("Bảng điểm bài tập 1")
                .submissions(Collections.emptyList())
                .build();

        when(teacherAssignmentService.getAssignmentGradebook(eq(assignmentId), any())).thenReturn(gradebook);

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Bảng điểm bài tập 1"));
    }

    @Test
    @DisplayName("PUT /api/v1/teacher/assignments/{assignmentId} - Cập nhật gia hạn deadline")
    void testUpdateAssignment_Success() throws Exception {
        UpdateAssignmentRequest req = UpdateAssignmentRequest.builder()
                .title("Tiêu đề mới")
                .deadline(LocalDateTime.now().plusDays(3))
                .build();

        TeacherAssignmentResponse resp = TeacherAssignmentResponse.builder()
                .assignmentId(assignmentId)
                .title("Tiêu đề mới")
                .build();

        when(teacherAssignmentService.updateAssignment(eq(assignmentId), any(), any())).thenReturn(resp);

        mockMvc.perform(put("/api/v1/teacher/assignments/{assignmentId}", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Tiêu đề mới"));
    }

    @Test
    @DisplayName("DELETE /api/v1/teacher/assignments/{assignmentId} - Hủy bài tập trả về 200 OK")
    void testCancelAssignment_Success() throws Exception {
        doNothing().when(teacherAssignmentService).cancelAssignment(eq(assignmentId), any());

        mockMvc.perform(delete("/api/v1/teacher/assignments/{assignmentId}", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
