package com.historytalk.controller.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.assignment.StudentAssignmentResponse;
import com.historytalk.dto.assignment.StudentSubmissionResponse;
import com.historytalk.dto.assignment.SubmitAssignmentRequest;
import com.historytalk.entity.enums.SubmissionStatus;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.assignment.StudentAssignmentService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StudentAssignmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentAssignmentService studentAssignmentService;

    @InjectMocks
    private StudentAssignmentController studentAssignmentController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UserPrincipal studentPrincipal;
    private UUID assignmentId;
    private UUID submissionId;

    @BeforeEach
    void setUp() {
        assignmentId = UUID.randomUUID();
        submissionId = UUID.randomUUID();

        com.historytalk.entity.school.School school = com.historytalk.entity.school.School.builder()
                .id(UUID.randomUUID()).build();
        com.historytalk.entity.user.User studentUser = com.historytalk.entity.user.User.builder()
                .uid(UUID.randomUUID())
                .school(school)
                .role(com.historytalk.entity.enums.UserRole.SCHOOL_STUDENT)
                .email("student@school.edu.vn")
                .build();
        studentPrincipal = new UserPrincipal(studentUser);

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return studentPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(studentAssignmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/student/assignments - Lấy danh sách bài tập của học sinh")
    void testGetMyAssignments_Success() throws Exception {
        StudentAssignmentResponse item = StudentAssignmentResponse.builder()
                .assignmentId(assignmentId)
                .title("Bài tập 1")
                .submissionStatus(SubmissionStatus.NOT_STARTED)
                .build();

        PaginatedResponse<StudentAssignmentResponse> pageResp = PaginatedResponse.<StudentAssignmentResponse>builder()
                .content(List.of(item))
                .currentPage(0)
                .pageSize(20)
                .totalElements(1L)
                .totalPages(1)
                .build();

        when(studentAssignmentService.getMyAssignments(any(), any(), any(), any())).thenReturn(pageResp);

        mockMvc.perform(get("/api/v1/student/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data[0].title").value("Bài tập 1"));
    }

    @Test
    @DisplayName("GET /api/v1/student/assignments/{assignmentId} - Xem chi tiết bài tập")
    void testGetAssignmentDetail_Success() throws Exception {
        StudentAssignmentResponse resp = StudentAssignmentResponse.builder()
                .assignmentId(assignmentId)
                .title("Chi tiết bài tập")
                .submissionStatus(SubmissionStatus.IN_PROGRESS)
                .build();

        when(studentAssignmentService.getAssignmentDetail(eq(assignmentId), any())).thenReturn(resp);

        mockMvc.perform(get("/api/v1/student/assignments/{assignmentId}", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Chi tiết bài tập"));
    }

    @Test
    @DisplayName("POST /api/v1/student/assignments/{assignmentId}/start - Bắt đầu làm bài")
    void testStartAssignment_Success() throws Exception {
        StudentSubmissionResponse resp = StudentSubmissionResponse.builder()
                .submissionId(submissionId)
                .assignmentId(assignmentId)
                .status(SubmissionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();

        when(studentAssignmentService.startAssignment(eq(assignmentId), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/student/assignments/{assignmentId}/start", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("POST /api/v1/student/assignments/{assignmentId}/submit - Nộp bài và nhận điểm")
    void testSubmitAssignment_Success() throws Exception {
        SubmitAssignmentRequest req = SubmitAssignmentRequest.builder()
                .chatSessionId(UUID.randomUUID())
                .build();

        StudentSubmissionResponse resp = StudentSubmissionResponse.builder()
                .submissionId(submissionId)
                .assignmentId(assignmentId)
                .status(SubmissionStatus.SUBMITTED)
                .score(new BigDecimal("9.50"))
                .submittedAt(LocalDateTime.now())
                .build();

        when(studentAssignmentService.submitAssignment(eq(assignmentId), any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/student/assignments/{assignmentId}/submit", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.score").value(9.50));
    }
}
