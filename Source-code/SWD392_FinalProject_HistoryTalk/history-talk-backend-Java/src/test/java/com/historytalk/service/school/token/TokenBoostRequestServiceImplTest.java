package com.historytalk.service.school.token;

import com.historytalk.dto.school.CreateTokenBoostRequestDto;
import com.historytalk.dto.school.ReviewTokenBoostRequestDto;
import com.historytalk.dto.school.TeacherGrantStudentTokenRequest;
import com.historytalk.dto.school.TokenBoostRequestResponse;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.TokenBoostRequestStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.school.TokenBoostRequest;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
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
import java.time.LocalDateTime;
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
    private User schoolAdmin;
    private User teacher;
    private User otherTeacher;
    private User student;
    private Classroom classroom;
    private UserPrincipal studentPrincipal;
    private UserPrincipal teacherPrincipal;
    private UserPrincipal otherTeacherPrincipal;
    private UserPrincipal schoolAdminPrincipal;

    @BeforeEach
    void setUp() {
        school = School.builder()
                .id(UUID.randomUUID())
                .name("THPT Chu Văn An")
                .schoolCode("CVA")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .totalSchoolTokenQuota(5_000_000)
                .unallocatedTokenQuota(50_000)
                .defaultDailyStudentQuota(1_000)
                .defaultDailyTeacherQuota(10_000)
                .build();

        schoolAdmin = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_admin")
                .fullName("Ban Giám Hiệu CVA")
                .email("admin@cva.edu.vn")
                .role(UserRole.SCHOOL_ADMIN)
                .school(school)
                .build();

        teacher = User.builder()
                .uid(UUID.randomUUID())
                .userName("cva_gv_01")
                .fullName("Nguyễn Thị Mai")
                .email("mai@cva.edu.vn")
                .role(UserRole.TEACHER)
                .school(school)
                .token(10_000)
                .dailyTokensUsed(0)
                .dailyBonusTokens(0)
                .lastTokenResetAt(LocalDateTime.now())
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
                .dailyTokensUsed(1_000)
                .dailyBonusTokens(0)
                .lastTokenResetAt(LocalDateTime.now())
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
        schoolAdminPrincipal = new UserPrincipal(schoolAdmin);
    }

    @Test
    @DisplayName("US-SP7-17: Student creates daily token boost request successfully")
    void createBoostRequest_Success() {
        when(userRepository.findByIdForUpdate(student.getUid())).thenReturn(Optional.of(student));
        when(classStudentRepository.findActiveClassroomsByStudentUid(student.getUid()))
                .thenReturn(List.of(classroom));
        when(tokenBoostRequestRepository.existsByStudent_UidAndRequestDateAndStatusIn(
                org.mockito.ArgumentMatchers.eq(student.getUid()), org.mockito.ArgumentMatchers.eq(LocalDate.now()),
                org.mockito.ArgumentMatchers.anyCollection()))
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
        when(userRepository.findByIdForUpdate(student.getUid())).thenReturn(Optional.of(student));
        when(classStudentRepository.findActiveClassroomsByStudentUid(student.getUid()))
                .thenReturn(List.of(classroom));
        when(tokenBoostRequestRepository.existsByStudent_UidAndRequestDateAndStatusIn(
                org.mockito.ArgumentMatchers.eq(student.getUid()), org.mockito.ArgumentMatchers.eq(LocalDate.now()),
                org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(true);

        CreateTokenBoostRequestDto dto = CreateTokenBoostRequestDto.builder()
                .requestedTokens(1000)
                .reason("Xin thêm lần 2")
                .build();

        assertThrows(DataConflictException.class,
                () -> tokenBoostRequestService.createBoostRequest(studentPrincipal, dto));
    }

    @Test
    @DisplayName("US-SP7-17: Assigned classroom teacher approves request by transferring from Teacher's daily allowance to Student")
    void reviewBoostRequest_TeacherApprove_TransfersFromTeacherDailyAllowance() {
        UUID reqId = UUID.randomUUID();
        TokenBoostRequest pendingReq = TokenBoostRequest.builder()
                .id(reqId)
                .school(school)
                .classroom(classroom)
                .student(student)
                .requester(student)
                .requesterRole(UserRole.SCHOOL_STUDENT)
                .requestedTokens(1000)
                .reason("Ôn thi học kỳ")
                .status(TokenBoostRequestStatus.PENDING)
                .requestDate(LocalDate.now())
                .build();

        when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        when(tokenBoostRequestRepository.findByIdForUpdate(reqId)).thenReturn(Optional.of(pendingReq));
        when(tokenBoostRequestRepository.save(any(TokenBoostRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewTokenBoostRequestDto reviewDto = ReviewTokenBoostRequestDto.builder()
                .action("APPROVE")
                .approvedTokens(1000)
                .reviewNote("Cô chuyển 1000 token từ quỹ của cô cho em ôn bài")
                .build();

        TokenBoostRequestResponse response = tokenBoostRequestService.reviewBoostRequest(
                teacherPrincipal, reqId, reviewDto);

        assertEquals(TokenBoostRequestStatus.APPROVED, response.getStatus());
        assertEquals(1000, response.getApprovedTokens());
        // Teacher's daily usage increases by 1000 (from 0 to 1000), remaining teacher token = 9000
        assertEquals(1000, teacher.getDailyTokensUsed());
        assertEquals(9000, teacher.getToken());
        // Student receives +1000 daily bonus tokens
        assertEquals(1000, student.getDailyBonusTokens());
        assertEquals(1000, student.getToken());
        // School shared pool remains untouched (50,000) until student actually chats with AI
        assertEquals(50_000, school.getUnallocatedTokenQuota());
    }

    @Test
    @DisplayName("US-SP7-17: Teacher directly grants tokens from Teacher's daily allowance to Student without waiting for ticket")
    void grantTokensFromTeacherToStudent_Success() {
        when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        when(userRepository.findById(student.getUid())).thenReturn(Optional.of(student));
        when(classStudentRepository.findActiveClassroomsByStudentUid(student.getUid()))
                .thenReturn(List.of(classroom));
        when(tokenBoostRequestRepository.save(any(TokenBoostRequest.class))).thenAnswer(inv -> {
            TokenBoostRequest r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        TeacherGrantStudentTokenRequest grantReq = TeacherGrantStudentTokenRequest.builder()
                .tokens(1500)
                .reason("Thưởng thêm token thảo luận nhóm")
                .build();

        TokenBoostRequestResponse response = tokenBoostRequestService.grantTokensFromTeacherToStudent(
                teacherPrincipal, student.getUid(), grantReq);

        assertEquals(TokenBoostRequestStatus.APPROVED, response.getStatus());
        assertEquals(1500, response.getApprovedTokens());
        assertEquals(1500, teacher.getDailyTokensUsed());
        assertEquals(8500, teacher.getToken());
        assertEquals(1500, student.getDailyBonusTokens());
        assertEquals(1500, student.getToken());
    }

    @Test
    @DisplayName("US-SP7-17: When Teacher runs out of daily tokens, Teacher escalates student request to School Admin (PENDING_ADMIN) and School Admin approves")
    void reviewBoostRequest_TeacherOutOfTokens_EscalatesToSchoolAdminAndAdminApproves() {
        // Teacher has already used 9,500 out of 10,000 daily tokens
        teacher.setDailyTokensUsed(9_500);
        teacher.setToken(500);

        UUID reqId = UUID.randomUUID();
        TokenBoostRequest pendingReq = TokenBoostRequest.builder()
                .id(reqId)
                .school(school)
                .classroom(classroom)
                .student(student)
                .requester(student)
                .requesterRole(UserRole.SCHOOL_STUDENT)
                .requestedTokens(1000)
                .reason("Em cần thêm 1000 token")
                .status(TokenBoostRequestStatus.PENDING)
                .requestDate(LocalDate.now())
                .build();

        when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        when(tokenBoostRequestRepository.findByIdForUpdate(reqId)).thenReturn(Optional.of(pendingReq));
        when(tokenBoostRequestRepository.save(any(TokenBoostRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Attempting direct APPROVE fails because teacher only has 500 left
        ReviewTokenBoostRequestDto approveDto = ReviewTokenBoostRequestDto.builder()
                .action("APPROVE")
                .approvedTokens(1000)
                .build();
        assertThrows(InvalidRequestException.class,
                () -> tokenBoostRequestService.reviewBoostRequest(teacherPrincipal, reqId, approveDto));

        // 2. Teacher escalates ticket to School Admin
        ReviewTokenBoostRequestDto escalateDto = ReviewTokenBoostRequestDto.builder()
                .action("ESCALATE")
                .reviewNote("Cô đã hết hạn mức ngày, nhờ Admin duyệt cấp thêm 1000 token cho học sinh")
                .build();
        TokenBoostRequestResponse escalatedResp = tokenBoostRequestService.reviewBoostRequest(
                teacherPrincipal, reqId, escalateDto);
        assertEquals(TokenBoostRequestStatus.PENDING_ADMIN, escalatedResp.getStatus());

        // 3. School Admin approves the PENDING_ADMIN ticket
        when(userRepository.findById(schoolAdmin.getUid())).thenReturn(Optional.of(schoolAdmin));
        ReviewTokenBoostRequestDto adminApproveDto = ReviewTokenBoostRequestDto.builder()
                .action("APPROVE")
                .approvedTokens(1000)
                .reviewNote("Admin duyệt cấp 1000 token cho học sinh")
                .build();
        TokenBoostRequestResponse adminResp = tokenBoostRequestService.reviewBoostRequest(
                schoolAdminPrincipal, reqId, adminApproveDto);

        assertEquals(TokenBoostRequestStatus.APPROVED, adminResp.getStatus());
        assertEquals(1000, student.getDailyBonusTokens());
        assertEquals(1000, student.getToken());
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
        when(tokenBoostRequestRepository.findByIdForUpdate(reqId)).thenReturn(Optional.of(pendingReq));

        ReviewTokenBoostRequestDto reviewDto = ReviewTokenBoostRequestDto.builder()
                .action("APPROVE")
                .build();

        assertThrows(AccessDeniedException.class,
                () -> tokenBoostRequestService.reviewBoostRequest(otherTeacherPrincipal, reqId, reviewDto));
    }

    @Test
    @DisplayName("MSG38: a second request on the same day is refused even after the first one was approved")
    void createBoostRequest_SecondRequestSameDayAfterApproval_ThrowsConflictAndChecksApprovedStatus() {
        when(userRepository.findByIdForUpdate(student.getUid())).thenReturn(Optional.of(student));
        when(classStudentRepository.findActiveClassroomsByStudentUid(student.getUid()))
                .thenReturn(List.of(classroom));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Collection<TokenBoostRequestStatus>> statuses =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        when(tokenBoostRequestRepository.existsByStudent_UidAndRequestDateAndStatusIn(
                org.mockito.ArgumentMatchers.eq(student.getUid()), org.mockito.ArgumentMatchers.eq(LocalDate.now()),
                statuses.capture()))
                .thenReturn(true);

        CreateTokenBoostRequestDto dto = CreateTokenBoostRequestDto.builder()
                .requestedTokens(500)
                .reason("Xin thêm lần 2 sau khi đã được duyệt")
                .build();

        DataConflictException ex = assertThrows(DataConflictException.class,
                () -> tokenBoostRequestService.createBoostRequest(studentPrincipal, dto));
        assertTrue(ex.getMessage().contains("Mỗi ngày chỉ được gửi một yêu cầu"));
        assertTrue(statuses.getValue().containsAll(List.of(
                TokenBoostRequestStatus.PENDING, TokenBoostRequestStatus.PENDING_ADMIN, TokenBoostRequestStatus.APPROVED)));
        assertFalse(statuses.getValue().contains(TokenBoostRequestStatus.REJECTED));
        verify(tokenBoostRequestRepository, never()).save(any(TokenBoostRequest.class));
    }

    @Test
    @DisplayName("Teacher cannot approve more tokens than the student asked for")
    void reviewBoostRequest_ApproveMoreThanRequested_ThrowsAndGrantsNothing() {
        UUID reqId = UUID.randomUUID();
        TokenBoostRequest pendingReq = TokenBoostRequest.builder()
                .id(reqId).school(school).classroom(classroom).student(student).requester(student)
                .requesterRole(UserRole.SCHOOL_STUDENT).requestedTokens(500)
                .status(TokenBoostRequestStatus.PENDING).requestDate(LocalDate.now())
                .build();
        when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        when(tokenBoostRequestRepository.findByIdForUpdate(reqId)).thenReturn(Optional.of(pendingReq));

        ReviewTokenBoostRequestDto reviewDto = ReviewTokenBoostRequestDto.builder()
                .action("APPROVE").approvedTokens(2000).build();

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> tokenBoostRequestService.reviewBoostRequest(teacherPrincipal, reqId, reviewDto));
        assertTrue(ex.getMessage().contains("không được vượt quá số token đã xin"));
        assertEquals(TokenBoostRequestStatus.PENDING, pendingReq.getStatus());
        assertEquals(0, student.getDailyBonusTokens());
    }

    @Test
    @DisplayName("ESCALATE keeps the amount the student asked for and stores a readable default note")
    void reviewBoostRequest_EscalateIgnoresApprovedTokens() {
        UUID reqId = UUID.randomUUID();
        TokenBoostRequest pendingReq = TokenBoostRequest.builder()
                .id(reqId).school(school).classroom(classroom).student(student).requester(student)
                .requesterRole(UserRole.SCHOOL_STUDENT).requestedTokens(300)
                .status(TokenBoostRequestStatus.PENDING).requestDate(LocalDate.now())
                .build();
        when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        when(tokenBoostRequestRepository.findByIdForUpdate(reqId)).thenReturn(Optional.of(pendingReq));
        when(tokenBoostRequestRepository.save(any(TokenBoostRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewTokenBoostRequestDto escalateDto = ReviewTokenBoostRequestDto.builder()
                .action("ESCALATE").approvedTokens(40000).build();

        TokenBoostRequestResponse resp = tokenBoostRequestService.reviewBoostRequest(teacherPrincipal, reqId, escalateDto);

        assertEquals(TokenBoostRequestStatus.PENDING_ADMIN, resp.getStatus());
        assertEquals(300, resp.getRequestedTokens());
        assertTrue(resp.getTeacherReviewNote().startsWith("Giáo viên đã hết hạn mức ngày"));
    }
}
