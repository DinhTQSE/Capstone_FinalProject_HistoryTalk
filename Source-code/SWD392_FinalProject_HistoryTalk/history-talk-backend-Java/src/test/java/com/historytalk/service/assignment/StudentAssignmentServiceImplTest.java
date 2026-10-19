package com.historytalk.service.assignment;

import com.historytalk.dto.assignment.StudentSubmissionResponse;
import com.historytalk.dto.assignment.SubmitAssignmentRequest;
import com.historytalk.entity.assignment.Assignment;
import com.historytalk.entity.assignment.AssignmentSubmission;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.AssignmentStatus;
import com.historytalk.entity.enums.AssignmentType;
import com.historytalk.entity.enums.SubmissionStatus;
import com.historytalk.entity.quiz.Question;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.entity.user.User;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.assignment.AssignmentSubmissionRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAssignmentServiceImplTest {

    @Mock
    private AssignmentSubmissionRepository assignmentSubmissionRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private QuizSessionRepository quizSessionRepository;
    @Mock
    private com.historytalk.repository.QuestionRepository questionRepository;
    @Mock
    private com.historytalk.repository.UserRepository userRepository;

    @InjectMocks
    private StudentAssignmentServiceImpl studentAssignmentService;

    private UUID studentUid;
    private UserPrincipal studentPrincipal;
    private UUID assignmentId;
    private User student;

    @BeforeEach
    void setUp() {
        studentUid = UUID.randomUUID();
        assignmentId = UUID.randomUUID();

        student = User.builder()
                .uid(studentUid)
                .role(com.historytalk.entity.enums.UserRole.SCHOOL_STUDENT)
                .fullName("Nguyễn Văn A")
                .build();
        studentPrincipal = new UserPrincipal(student);
    }

    @Test
    @DisplayName("Bắt đầu làm bài tập: chuyển NOT_STARTED sang IN_PROGRESS và ghi nhận startedAt")
    void testStartAssignment_Success() {
        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .status(AssignmentStatus.ACTIVE)
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(SubmissionStatus.NOT_STARTED)
                .build();

        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid))
                .thenReturn(Optional.of(submission));

        StudentSubmissionResponse resp = studentAssignmentService.startAssignment(assignmentId, studentPrincipal);

        assertNotNull(resp);
        assertEquals(SubmissionStatus.IN_PROGRESS, resp.getStatus());
        assertNotNull(resp.getStartedAt());
        verify(assignmentSubmissionRepository, times(1)).save(submission);
    }

    @Test
    @DisplayName("Nộp bài AI Chat Roleplay: Đếm tin nhắn và tính điểm trên thang điểm 10")
    void testSubmitAssignment_AiChatRoleplay_AutoGrading() {
        UUID characterId = UUID.randomUUID();
        UUID chatSessionId = UUID.randomUUID();

        Character character = Character.builder().characterId(characterId).name("Trần Hưng Đạo").build();
        ChatSession chatSession = ChatSession.builder()
                .sessionId(chatSessionId)
                .character(character)
                .user(student)
                .build();

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .assignmentType(AssignmentType.AI_CHAT_ROLEPLAY)
                .targetId(characterId)
                .minRequirement(5) // Cần 5 tin nhắn để đạt 10 điểm
                .deadline(LocalDateTime.now().plusDays(1))
                .status(AssignmentStatus.ACTIVE)
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(SubmissionStatus.IN_PROGRESS)
                .build();

        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid))
                .thenReturn(Optional.of(submission));
        when(chatSessionRepository.findActiveBySessionIdAndUserUid(chatSessionId, studentUid))
                .thenReturn(Optional.of(chatSession));
        when(messageRepository.countUserMessagesInSession(chatSessionId)).thenReturn(4L); // 4/5 tin nhắn -> 8.00 điểm

        SubmitAssignmentRequest req = SubmitAssignmentRequest.builder()
                .chatSessionId(chatSessionId)
                .build();

        StudentSubmissionResponse resp = studentAssignmentService.submitAssignment(assignmentId, req, studentPrincipal);

        assertNotNull(resp);
        assertEquals(SubmissionStatus.SUBMITTED, resp.getStatus());
        assertEquals(4, resp.getMessageCount());
        assertEquals(new BigDecimal("8.00"), resp.getScore());
        verify(assignmentSubmissionRepository, times(1)).save(submission);
    }

    @Test
    @DisplayName("Nộp bài Quiz: Lấy kết quả từ QuizSession và quy đổi về thang điểm 10")
    void testSubmitAssignment_QuizTest_AutoGrading() {
        UUID quizId = UUID.randomUUID();
        UUID quizSessionId = UUID.randomUUID();

        Quiz quiz = Quiz.builder()
                .quizId(quizId)
                .questions(List.of(
                        Question.builder().build(),
                        Question.builder().build(),
                        Question.builder().build(),
                        Question.builder().build(),
                        Question.builder().build()
                )) // 5 câu hỏi
                .build();

        QuizSession quizSession = QuizSession.builder()
                .sessionId(quizSessionId)
                .quiz(quiz)
                .user(student)
                .score(4.0f) // Đúng 4/5 câu -> 8.00 điểm
                .endTime(LocalDateTime.now())
                .build();

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .assignmentType(AssignmentType.QUIZ_TEST)
                .targetId(quizId)
                .deadline(LocalDateTime.now().plusDays(1))
                .status(AssignmentStatus.ACTIVE)
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(SubmissionStatus.IN_PROGRESS)
                .build();

        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid))
                .thenReturn(Optional.of(submission));
        when(quizSessionRepository.findBySessionId(quizSessionId))
                .thenReturn(Optional.of(quizSession));

        SubmitAssignmentRequest req = SubmitAssignmentRequest.builder()
                .quizSessionId(quizSessionId)
                .build();

        StudentSubmissionResponse resp = studentAssignmentService.submitAssignment(assignmentId, req, studentPrincipal);

        assertNotNull(resp);
        assertEquals(SubmissionStatus.SUBMITTED, resp.getStatus());
        assertEquals(new BigDecimal("8.00"), resp.getScore());
    }

    @Test
    @DisplayName("Nộp bài Context Reading: Tính thời gian đọc và quy đổi điểm thang 10")
    void testSubmitAssignment_ContextReading_AutoGrading() {
        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .assignmentType(AssignmentType.CONTEXT_READING)
                .minRequirement(60) // Tiêu chuẩn 60 giây
                .deadline(LocalDateTime.now().plusDays(1))
                .status(AssignmentStatus.ACTIVE)
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .startedAt(LocalDateTime.now().minusSeconds(45)) // Đã đọc 45s
                .status(SubmissionStatus.IN_PROGRESS)
                .build();

        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid))
                .thenReturn(Optional.of(submission));

        StudentSubmissionResponse resp = studentAssignmentService.submitAssignment(assignmentId, null, studentPrincipal);

        assertNotNull(resp);
        assertEquals(SubmissionStatus.SUBMITTED, resp.getStatus());
        assertTrue(resp.getReadingDurationSeconds() >= 44);
        assertNotNull(resp.getScore());
    }

    @Test
    @DisplayName("Nộp bài sau deadline: tự động đánh dấu LATE_SUBMITTED")
    void testSubmitAssignment_LateSubmitted_WhenAfterDeadline() {
        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .assignmentType(AssignmentType.CONTEXT_READING)
                .minRequirement(60)
                .deadline(LocalDateTime.now().minusHours(2)) // Đã quá hạn 2 tiếng
                .status(AssignmentStatus.ACTIVE)
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .startedAt(LocalDateTime.now().minusSeconds(60))
                .status(SubmissionStatus.IN_PROGRESS)
                .build();

        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid))
                .thenReturn(Optional.of(submission));

        StudentSubmissionResponse resp = studentAssignmentService.submitAssignment(assignmentId, null, studentPrincipal);

        assertNotNull(resp);
        assertEquals(SubmissionStatus.LATE_SUBMITTED, resp.getStatus());
    }

    @Test
    @DisplayName("Chặn nộp lại nếu bài đã có trạng thái SUBMITTED")
    void testSubmitAssignment_Throws_AlreadySubmitted() {
        Assignment assignment = Assignment.builder().id(assignmentId).status(AssignmentStatus.ACTIVE).build();
        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(SubmissionStatus.SUBMITTED) // Đã nộp rồi
                .build();

        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid))
                .thenReturn(Optional.of(submission));

        assertThrows(com.historytalk.exception.InvalidRequestException.class, () ->
                studentAssignmentService.submitAssignment(assignmentId, null, studentPrincipal));
    }

    @Test
    @DisplayName("Chặn nộp Quiz nếu phiên làm bài không thuộc về học sinh (Anti-cheat)")
    void testSubmitAssignment_QuizTest_Throws_NotOwner() {
        UUID otherStudentUid = UUID.randomUUID();
        User otherStudent = User.builder().uid(otherStudentUid).build();
        UUID quizId = UUID.randomUUID();
        UUID quizSessionId = UUID.randomUUID();

        Quiz quiz = Quiz.builder().quizId(quizId).build();
        QuizSession quizSession = QuizSession.builder()
                .sessionId(quizSessionId)
                .quiz(quiz)
                .user(otherStudent) // Thuộc học sinh khác!
                .endTime(LocalDateTime.now())
                .build();

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .assignmentType(AssignmentType.QUIZ_TEST)
                .targetId(quizId)
                .status(AssignmentStatus.ACTIVE)
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(SubmissionStatus.IN_PROGRESS)
                .build();

        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid))
                .thenReturn(Optional.of(submission));
        when(quizSessionRepository.findBySessionId(quizSessionId))
                .thenReturn(Optional.of(quizSession));

        SubmitAssignmentRequest req = SubmitAssignmentRequest.builder().quizSessionId(quizSessionId).build();

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                studentAssignmentService.submitAssignment(assignmentId, req, studentPrincipal));
    }

    @Test
    @DisplayName("Nộp bài AI Chat Roleplay không cần truyền Request Body: Tự động lấy phiên chat mới nhất")
    void testSubmitAssignment_AiChatRoleplay_WithoutRequestBody() {
        UUID characterId = UUID.randomUUID();
        UUID chatSessionId = UUID.randomUUID();

        Character character = Character.builder().characterId(characterId).name("Trần Hưng Đạo").build();
        ChatSession chatSession = ChatSession.builder()
                .sessionId(chatSessionId)
                .character(character)
                .user(student)
                .build();

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .assignmentType(AssignmentType.AI_CHAT_ROLEPLAY)
                .targetId(characterId)
                .minRequirement(5)
                .deadline(LocalDateTime.now().plusDays(1))
                .status(AssignmentStatus.ACTIVE)
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(SubmissionStatus.IN_PROGRESS)
                .build();

        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid))
                .thenReturn(Optional.of(submission));
        when(chatSessionRepository.findByUserAndCharacter(studentUid, characterId))
                .thenReturn(List.of(chatSession));
        when(messageRepository.countUserMessagesInSession(chatSessionId)).thenReturn(5L);

        StudentSubmissionResponse resp = studentAssignmentService.submitAssignment(assignmentId, null, studentPrincipal);

        assertNotNull(resp);
        assertEquals(SubmissionStatus.SUBMITTED, resp.getStatus());
        assertEquals(5, resp.getMessageCount());
        assertEquals(new BigDecimal("10.00"), resp.getScore());
    }

    @Test
    @DisplayName("Nộp bài Quiz không cần truyền Request Body: Tự động lấy phiên làm bài hoàn thành điểm cao nhất")
    void testSubmitAssignment_QuizTest_WithoutRequestBody() {
        UUID quizId = UUID.randomUUID();
        UUID quizSessionId = UUID.randomUUID();

        Quiz quiz = Quiz.builder()
                .quizId(quizId)
                .questions(List.of(
                        Question.builder().build(),
                        Question.builder().build(),
                        Question.builder().build(),
                        Question.builder().build(),
                        Question.builder().build()
                ))
                .build();

        QuizSession quizSession = QuizSession.builder()
                .sessionId(quizSessionId)
                .quiz(quiz)
                .user(student)
                .score(4.0f)
                .endTime(LocalDateTime.now())
                .build();

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .assignmentType(AssignmentType.QUIZ_TEST)
                .targetId(quizId)
                .deadline(LocalDateTime.now().plusDays(1))
                .status(AssignmentStatus.ACTIVE)
                .build();

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .id(UUID.randomUUID())
                .assignment(assignment)
                .student(student)
                .status(SubmissionStatus.IN_PROGRESS)
                .build();

        when(assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid))
                .thenReturn(Optional.of(submission));
        when(quizSessionRepository.findCompletedByQuizAndUser(quizId, studentUid))
                .thenReturn(List.of(quizSession));

        StudentSubmissionResponse resp = studentAssignmentService.submitAssignment(assignmentId, null, studentPrincipal);

        assertNotNull(resp);
        assertEquals(SubmissionStatus.SUBMITTED, resp.getStatus());
        assertEquals(new BigDecimal("8.00"), resp.getScore());
    }
}
