package com.historytalk.service.assignment;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.assignment.CreateAssignmentRequest;
import com.historytalk.dto.assignment.TeacherAssignmentResponse;
import com.historytalk.entity.assignment.Assignment;
import com.historytalk.entity.assignment.AssignmentSubmission;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.AssignmentStatus;
import com.historytalk.entity.enums.AssignmentType;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.assignment.AssignmentRepository;
import com.historytalk.repository.assignment.AssignmentSubmissionRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherAssignmentServiceImplTest {

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private AssignmentSubmissionRepository assignmentSubmissionRepository;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @Mock
    private HistoricalContextRepository historicalContextRepository;
    @Mock
    private com.historytalk.repository.UserRepository userRepository;

    @InjectMocks
    private TeacherAssignmentServiceImpl teacherAssignmentService;

    private UUID schoolId;
    private UUID teacherUid;
    private UUID classroomId;
    private UserPrincipal teacherPrincipal;
    private Classroom classroom;
    private User teacher;
    private School school;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        teacherUid = UUID.randomUUID();
        classroomId = UUID.randomUUID();

        school = School.builder().id(schoolId).name("THPT Chu Văn An").build();
        teacher = User.builder().uid(teacherUid).role(com.historytalk.entity.enums.UserRole.TEACHER).school(school).fullName("Thầy Hoàng").build();
        classroom = Classroom.builder()
                .id(classroomId)
                .school(school)
                .teacher(teacher)
                .className("10A1")
                .build();

        teacherPrincipal = new UserPrincipal(teacher);
    }

    @Test
    @DisplayName("Tạo bài tập thành công và tự động khởi tạo submission cho học sinh ACTIVE")
    void testCreateAssignment_Success() {
        UUID contextId = UUID.randomUUID();
        HistoricalContext context = HistoricalContext.builder()
                .contextId(contextId)
                .name("Chiến thắng Điện Biên Phủ")
                .build();

        CreateAssignmentRequest req = CreateAssignmentRequest.builder()
                .classId(classroomId)
                .title("Tìm hiểu Điện Biên Phủ")
                .assignmentType(AssignmentType.CONTEXT_READING)
                .targetId(contextId)
                .minRequirement(120)
                .deadline(LocalDateTime.now().plusDays(3))
                .build();

        Assignment savedAssignment = Assignment.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .teacher(teacher)
                .title(req.getTitle())
                .assignmentType(req.getAssignmentType())
                .targetId(contextId)
                .minRequirement(120)
                .deadline(req.getDeadline())
                .status(AssignmentStatus.ACTIVE)
                .build();

        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId))
                .thenReturn(Optional.of(classroom));
        when(userRepository.findById(teacherUid)).thenReturn(Optional.of(teacher));
        when(historicalContextRepository.findById(contextId)).thenReturn(Optional.of(context));
        when(assignmentRepository.save(any(Assignment.class))).thenReturn(savedAssignment);

        UUID student1Uid = UUID.randomUUID();
        UUID student2Uid = UUID.randomUUID();
        when(classStudentRepository.findEnrolledStudentUids(classroomId))
                .thenReturn(List.of(student1Uid, student2Uid));
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(User.builder().uid(student1Uid).build(), User.builder().uid(student2Uid).build()));

        TeacherAssignmentResponse response = teacherAssignmentService.createAssignment(req, teacherPrincipal);

        assertNotNull(response);
        assertEquals("Tìm hiểu Điện Biên Phủ", response.getTitle());
        assertEquals("Chiến thắng Điện Biên Phủ", response.getTargetTitle());
        assertEquals(2, response.getTotalStudents());
        verify(assignmentSubmissionRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Lấy danh sách bài tập của lớp phân trang")
    void testGetAssignmentsByClassroom_Success() {
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId))
                .thenReturn(Optional.of(classroom));

        Assignment a = Assignment.builder()
                .id(UUID.randomUUID())
                .classroom(classroom)
                .title("Bài tập 1")
                .assignmentType(AssignmentType.CONTEXT_READING)
                .status(AssignmentStatus.ACTIVE)
                .build();

        when(assignmentRepository.findAssignmentsByClassroomAndOptionalStatus(eq(classroomId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(a)));

        PaginatedResponse<TeacherAssignmentResponse> result = teacherAssignmentService.getAssignmentsByClassroom(
                classroomId, null, PageRequest.of(0, 10), teacherPrincipal);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Bài tập 1", result.getContent().get(0).getTitle());
    }
}
