package com.historytalk.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.ExamProgressResponse;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;
import com.historytalk.entity.enums.ChatSessionMode;
import com.historytalk.entity.enums.ExamVerdict;
import com.historytalk.entity.enums.QuestType;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.chat.AiServiceClient.ExamGradeResult;
import com.historytalk.service.chat.AiServiceClient.ExamQuestionResult;
import com.historytalk.service.chat.ExamSessionService.ExamTurnResult;
import com.historytalk.service.gamification.GamificationService;
import com.historytalk.service.school.token.DailyTokenAllowanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamSessionServiceImplTest {

    private static final AiMetricsService.TokenUsage USAGE =
            new AiMetricsService.TokenUsage("ollama", "test", 10, 5, 15);

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private GamificationService gamificationService;
    @Mock
    private DailyTokenAllowanceService dailyTokenAllowanceService;

    private ExamSessionServiceImpl service;
    private ChatSession session;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ExamSessionServiceImpl(messageRepository, chatSessionRepository,
                new ChatTokenCharger(userRepository, dailyTokenAllowanceService),
                aiServiceClient, new ObjectMapper(), gamificationService,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));

        user = new User();
        user.setUid(UUID.randomUUID());
        user.setRole(UserRole.CUSTOMER);
        user.setToken(100);

        session = ChatSession.builder()
                .sessionId(UUID.randomUUID())
                .user(user)
                .character(Character.builder()
                        .characterId(UUID.randomUUID())
                        .name("Ngô Quyền")
                        .background("Tiểu sử")
                        .build())
                .mode(ChatSessionMode.EXAM)
                .examQuestionCount(2)
                .build();

        lenient().when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(chatSessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
    }

    @Test
    void startExamStoresTheAnswerKeyAndAsksTheFirstQuestion() {
        when(aiServiceClient.generateExamQuestion(anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new ExamQuestionResult("Ta dùng kế gì?", "Cọc gỗ", "Tư liệu 1", USAGE));

        Message message = service.startExam(session.getSessionId());

        assertThat(message.getContent()).startsWith("Ta là Ngô Quyền. Hôm nay ta sẽ hỏi ngươi 2 câu.")
                .endsWith("\n---\nTa dùng kế gì?");
        assertThat(message.getContent()).doesNotContain("Cọc gỗ", "Tư liệu 1");
        assertThat(message.getExamExpectedAnswer()).isEqualTo("Cọc gỗ");
        assertThat(message.getExamSource()).isEqualTo("Tư liệu 1");
        assertThat(message.getQuotes()).isNull();
    }

    @Test
    void answerIsGradedAgainstCurrentQuestionThenNextQuestionIsAsked() {
        Message current = questionMessage("Ta dùng kế gì?", "Cọc gỗ", "Tư liệu 1");
        when(messageRepository.findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId(), false))
                .thenReturn(new ArrayList<>(List.of(current)));
        when(aiServiceClient.gradeExamAnswer(anyString(), anyString(), any(), any(),
                eq("Ta dùng kế gì?"), eq("Cọc gỗ"), eq("Tư liệu 1"), eq("Cắm cọc")))
                .thenReturn(new ExamGradeResult("CORRECT", "Đúng vậy.", USAGE));
        when(aiServiceClient.generateExamQuestion(anyString(), anyString(), any(), any(),
                eq(List.of("Ta dùng kế gì?")), eq(List.of("Tư liệu 1"))))
                .thenReturn(new ExamQuestionResult("Ta đóng đô ở đâu?", "Cổ Loa", "Tư liệu 2", USAGE));

        ExamTurnResult turn = service.submitAnswer(session.getSessionId(), request("Cắm cọc"));

        Message assistant = turn.assistantMessage();
        assertThat(assistant.getContent()).isEqualTo("Đúng vậy.\n---\nTa đóng đô ở đâu?");
        assertThat(assistant.getExamVerdict()).isEqualTo(ExamVerdict.CORRECT);
        assertThat(assistant.getExamQuestion()).isEqualTo("Ta đóng đô ở đâu?");
        assertThat(assistant.getQuotes()).contains("Tư liệu 1").doesNotContain("Tư liệu 2");
        assertThat(turn.progress().isFinished()).isFalse();
        assertThat(turn.progress().getAnsweredQuestions()).isEqualTo(1);
        assertThat(turn.promptTokens()).isEqualTo(20);
        assertThat(turn.completionTokens()).isEqualTo(10);
        assertThat(turn.remainingTokens()).isEqualTo(70);
        verify(userRepository).deductTokens(user.getUid(), 30);
        verify(gamificationService).recordProgress(user.getUid().toString(), QuestType.CHAT);
    }

    @Test
    void lastAnswerClosesTheExamWithoutAskingAgain() {
        Message graded = questionMessage("Câu 2?", "Đáp 2", "Tư liệu 2");
        graded.setExamVerdict(ExamVerdict.CORRECT);
        when(messageRepository.findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId(), false))
                .thenReturn(new ArrayList<>(List.of(questionMessage("Câu 1?", "Đáp 1", "Tư liệu 1"), graded)));
        when(aiServiceClient.gradeExamAnswer(anyString(), anyString(), any(), any(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ExamGradeResult("PARTIAL", "Còn thiếu ý.", USAGE));

        ExamTurnResult turn = service.submitAnswer(session.getSessionId(), request("Trả lời"));

        assertThat(turn.progress().isFinished()).isTrue();
        assertThat(turn.progress().getScore()).isEqualTo(7.5);
        assertThat(turn.assistantMessage().getExamQuestion()).isNull();
        assertThat(turn.assistantMessage().getContent()).contains("Còn thiếu ý.", "Ngươi trả lời đúng", "Điểm của ngươi: 7.5/10");
        verify(aiServiceClient, never()).generateExamQuestion(any(), any(), any(), any(), any(), any());
    }

    @Test
    void answerAfterTheExamEndedIsRejectedWithoutCallingAi() {
        Message closing = Message.builder().content("Kết thúc").isFromAi(true).examVerdict(ExamVerdict.CORRECT).build();
        when(messageRepository.findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId(), false))
                .thenReturn(List.of(closing));

        assertThatThrownBy(() -> service.submitAnswer(session.getSessionId(), request("Trả lời")))
                .isInstanceOf(InvalidRequestException.class);

        verify(messageRepository, never()).save(any());
        verify(aiServiceClient, never()).gradeExamAnswer(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownVerdictFailsInsteadOfGuessingAGrade() {
        when(messageRepository.findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId(), false))
                .thenReturn(List.of(questionMessage("Câu 1?", "Đáp 1", "Tư liệu 1")));
        when(aiServiceClient.gradeExamAnswer(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ExamGradeResult("MAYBE", "Không rõ.", USAGE));

        assertThatThrownBy(() -> service.submitAnswer(session.getSessionId(), request("Trả lời")))
                .isInstanceOf(SystemException.class);

        verify(userRepository, never()).deductTokens(any(), anyInt());
        verify(messageRepository).delete(any(Message.class));
    }

    @Test
    void scoreCountsPartialAsHalfAndIgnoresUngradedMessages() {
        session.setExamQuestionCount(4);
        Message correct = Message.builder().examVerdict(ExamVerdict.CORRECT).build();
        Message partial = Message.builder().examVerdict(ExamVerdict.PARTIAL).build();
        Message incorrect = Message.builder().examVerdict(ExamVerdict.INCORRECT).build();
        Message opening = Message.builder().build();

        ExamProgressResponse progress = service.getProgress(session, List.of(opening, correct, partial, incorrect));

        assertThat(progress.getAnsweredQuestions()).isEqualTo(3);
        assertThat(progress.getScore()).isEqualTo(3.8);
        assertThat(progress.isFinished()).isFalse();
    }

    private Message questionMessage(String question, String expected, String source) {
        return Message.builder()
                .content(question)
                .isFromAi(true)
                .chatSession(session)
                .examQuestion(question)
                .examExpectedAnswer(expected)
                .examSource(source)
                .build();
    }

    private SendMessageRequest request(String content) {
        SendMessageRequest request = new SendMessageRequest();
        ReflectionTestUtils.setField(request, "sessionId", session.getSessionId().toString());
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }
}
