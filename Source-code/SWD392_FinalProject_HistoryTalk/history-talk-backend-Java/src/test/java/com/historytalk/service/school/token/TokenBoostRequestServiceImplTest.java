package com.historytalk.service.school.token;

import com.historytalk.dto.school.CreateTokenBoostRequestDto;
import com.historytalk.dto.school.ReviewTokenBoostRequestDto;
import com.historytalk.dto.school.TokenBoostRequestResponse;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.TokenBoostRequestStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.school.TokenBoostRequest;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.repository.school.TokenBoostRequestRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBoostRequestServiceImplTest {

    @Mock
    private TokenBoostRequestRepository tokenBoostRequestRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @InjectMocks
    private TokenBoostRequestServiceImpl tokenBoostRequestService;

    private School school;
    private User teacher;
    private User otherTeacher;
    private User student;
    private Classroom classroom;
    private UserPrincipal studentPrincipal;
    private UserPrincipal teacherPrincipal;
    private UserPrincipal otherTeacherPrincipal;

    @BeforeEach
    void setUp() {
        school = School.builder()
                .id(UUID.randomUUID())
                .name("THPT Chu Văn An")
                .schoolCode("CVA")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .totalSchoolTokenQuota(5_000_000)
                .unallocatedTokenQuota(50_000)
                .build();

        teacher = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_gv_01")
                .fullName("Nguyễn Thị Mai")
                .email("mai@cva.edu.vn")
                .role(UserRole.TEACHER)
                .school(school)
                .build();

        otherTeacher = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_gv_02")
                .fullName("Trần Văn Hùng")
                .email("hung@cva.edu.vn")
                .role(UserRole.TEACHER)
                .school(school)
                .build();

        student = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_hs_01")
                .studentCode("HS001")
                .fullName("Trần Quốc Dinh")
                .email("dinh@cva.edu.vn")
                .role(UserRole.SCHOOL_STUDENT)
                .school(school)
                .token(0)
                .build();

        classroom = Classroom.builder()
                .id(UUID.randomUUID())
                .className("10A1")
                .classCode("CVA-10A1-2026")
                .school(school)
                .teacher(teacher)
                .status("ACTIVE")
                .build();

        studentPrincipal = new UserPrincipal(student);
        teacherPrincipal = new UserPrincipal(teacher);
        otherTeacherPrincipal = new UserPrincipal(otherTeacher);
    }

    @Test
    @DisplayName("US-SP7-17: Student creates daily token boost request successfully")
    void createBoostRequest_Success() {
        when(userRepository.findById(student.getUid())).thenReturn(Optional.of(student));
        when(classStudentRepository.findActiveClassroomsByStudentUid(student.getUid()))
                .thenReturn(List.of(classroom));
        when(tokenBoostRequestRepository.existsByStudent_UidAndRequestDateAndStatus(
                student.getUid(), LocalDate.now(), TokenBoostRequestStatus.PENDING))
                .thenReturn(false);
        when(tokenBoostRequestRepository.save(any(TokenBoostRequest.class))).thenAnswer(inv -> {
            TokenBoostRequest r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        CreateTokenBoostRequestDto dto = CreateTokenBoostRequestDto.builder()
                .requestedTokens(1000)
                .reason("Em cần hỏi thêm nhân vật Trần Hưng Đạo cho bài tập nhóm")
                .build();

        TokenBoostRequestResponse response = tokenBoostRequestService.createBoostRequest(studentPrincipal, dto);

        assertNotNull(response.getId());
        assertEquals(1000, response.getRequestedTokens());
        assertEquals(TokenBoostRequestStatus.PENDING, response.getStatus());
        assertEquals("10A1", response.getClassName());
    }

    @Test
    @DisplayName("US-SP7-17: Duplicate PENDING request on same day throws DataConflictException")
    void createBoostRequest_DuplicatePendingToday_ThrowsConflict() {
        when(userRepository.findById(student.getUid())).thenReturn(Optional.of(student));
        when(classStudentRepository.findActiveClassroomsByStudentUid(student.getUid()))
                .thenReturn(List.of(classroom));
        when(tokenBoostRequestRepository.existsByStudent_UidAndRequestDateAndStatus(
                student.getUid(), LocalDate.now(), TokenBoostRequestStatus.PENDING))
                .thenReturn(true);

        CreateTokenBoostRequestDto dto = CreateTokenBoostRequestDto.builder()
                .requestedTokens(1000)
                .reason("Xin thêm lần 2")
                .build();

        assertThrows(DataConflictException.class,
                () -> tokenBoostRequestService.createBoostRequest(studentPrincipal, dto));
    }

    @Test
    @DisplayName("US-SP7-17: Assigned classroom teacher approves request and grants tokens from school pool")
    void reviewBoostRequest_TeacherApprove_Success() {
        UUID reqId = UUID.randomUUID();
        TokenBoostRequest pendingReq = TokenBoostRequest.builder()
                .id(reqId)
                .school(school)
                .classroom(classroom)
                .student(student)
                .requestedTokens(1000)
                .reason("Ôn thi học kỳ")
                .status(TokenBoostRequestStatus.PENDING)
                .requestDate(LocalDate.now())
                .build();

        when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        when(tokenBoostRequestRepository.findById(reqId)).thenReturn(Optional.of(pendingReq));
        when(tokenBoostRequestRepository.save(any(TokenBoostRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewTokenBoostRequestDto reviewDto = ReviewTokenBoostRequestDto.builder()
                .action("APPROVE")
                .approvedTokens(1000)
                .reviewNote("Duyệt cho em ôn bài")
                .build();

        TokenBoostRequestResponse response = tokenBoostRequestService.reviewBoostRequest(
                teacherPrincipal, reqId, reviewDto);

        assertEquals(TokenBoostRequestStatus.APPROVED, response.getStatus());
        assertEquals(1000, response.getApprovedTokens());
        assertEquals(1000, student.getToken());
        assertEquals(49_000, school.getUnallocatedTokenQuota());
    }

    @Test
    @DisplayName("US-SP7-17: Teacher not assigned to student's classroom cannot review request")
    void reviewBoostRequest_UnassignedTeacher_ThrowsAccessDenied() {
        UUID reqId = UUID.randomUUID();
        TokenBoostRequest pendingReq = TokenBoostRequest.builder()
                .id(reqId)
                .school(school)
                .classroom(classroom)
                .student(student)
                .requestedTokens(1000)
                .status(TokenBoostRequestStatus.PENDING)
                .build();

        when(userRepository.findById(otherTeacher.getUid())).thenReturn(Optional.of(otherTeacher));
        when(tokenBoostRequestRepository.findById(reqId)).thenReturn(Optional.of(pendingReq));

        ReviewTokenBoostRequestDto reviewDto = ReviewTokenBoostRequestDto.builder()
                .action("APPROVE")
                .build();

        assertThrows(AccessDeniedException.class,
                () -> tokenBoostRequestService.reviewBoostRequest(otherTeacherPrincipal, reqId, reviewDto));
    }
}
