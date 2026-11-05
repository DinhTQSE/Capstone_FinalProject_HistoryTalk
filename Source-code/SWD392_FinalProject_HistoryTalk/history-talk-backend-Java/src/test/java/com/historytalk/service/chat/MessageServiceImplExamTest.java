package com.historytalk.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.ExamProgressResponse;
import com.historytalk.dto.chat.GetMessagesResponse;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.dto.chat.SendMessageResponse;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;
import com.historytalk.entity.enums.ChatSessionMode;
import com.historytalk.entity.enums.ExamVerdict;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplExamTest {

    private static final String ANSWER_KEY = "Đáp án bí mật";
    private static final String SOURCE = "Tư liệu chứa đáp án";

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GamificationService gamificationService;
    @Mock
    private DailyTokenAllowanceService dailyTokenAllowanceService;
    @Mock
    private ExamSessionService examSessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();

    private MessageServiceImpl service;
    private ChatSession session;
    private SendMessageRequest request;
    private ExamProgressResponse progress;

    @BeforeEach
    void setUp() {
        service = new MessageServiceImpl(messageRepository, chatSessionRepository, aiServiceClient, objectMapper,
                userRepository, gamificationService, dailyTokenAllowanceService, examSessionService,
                new ChatTokenCharger(userRepository, dailyTokenAllowanceService),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));

        session = ChatSession.builder()
                .sessionId(UUID.randomUUID())
                .mode(ChatSessionMode.EXAM)
                .examQuestionCount(3)
                .build();
        request = new SendMessageRequest();
        ReflectionTestUtils.setField(request, "sessionId", session.getSessionId().toString());
        ReflectionTestUtils.setField(request, "content", "Câu trả lời");
        progress = ExamProgressResponse.builder().totalQuestions(3).answeredQuestions(1).correctCount(1).build();

        when(chatSessionRepository.findActiveBySessionIdAndUserUid(session.getSessionId(), userId))
                .thenReturn(Optional.of(session));
    }

    @Test
    void sendMessageReturnsVerdictAndProgressWithoutLeakingTheNextAnswerKey() throws Exception {
        when(examSessionService.submitAnswer(session.getSessionId(), request)).thenReturn(examTurn());

        SendMessageResponse response = service.sendMessage(userId.toString(), request);

        assertThat(response.getExam()).isSameAs(progress);
        assertThat(response.getAssistantMessage().getExamVerdict()).isEqualTo("CORRECT");
        assertThat(response.getSuggestedQuestions()).isEmpty();
        assertThat(objectMapper.findAndRegisterModules().writeValueAsString(response))
                .doesNotContain(ANSWER_KEY, SOURCE);
        verify(aiServiceClient, never()).chat(any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void sendMessageStreamAnswersExamTurnsWithoutTheChatStream() {
        when(examSessionService.submitAnswer(session.getSessionId(), request)).thenReturn(examTurn());

        SseEmitter emitter = service.sendMessageStream(userId.toString(), request);

        assertThat(emitter).isNotNull();
        verify(aiServiceClient, never()).streamChat(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any(), any());
    }

    @Test
    void getMessagesIncludesProgressOnlyForExamSessions() {
        Message opening = assistantMessage(null);
        when(messageRepository.findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId(), false))
                .thenReturn(List.of(opening));
        when(examSessionService.getProgress(session, List.of(opening))).thenReturn(progress);

        GetMessagesResponse exam = service.getMessages(session.getSessionId().toString(), userId.toString());

        session.setMode(ChatSessionMode.CHAT);
        GetMessagesResponse chat = service.getMessages(session.getSessionId().toString(), userId.toString());

        assertThat(exam.getExam()).isSameAs(progress);
        assertThat(chat.getExam()).isNull();
    }

    private ExamTurnResult examTurn() {
        Message user = Message.builder()
                .messageId(UUID.randomUUID())
                .content("Câu trả lời")
                .isFromAi(false)
                .chatSession(session)
                .build();
        return new ExamTurnResult(user, assistantMessage(ExamVerdict.CORRECT), progress, 70, 20, 10);
    }

    private Message assistantMessage(ExamVerdict verdict) {
        return Message.builder()
                .messageId(UUID.randomUUID())
                .content("Đúng vậy.\n---\nCâu tiếp theo?")
                .isFromAi(true)
                .chatSession(session)
                .examQuestion("Câu tiếp theo?")
                .examExpectedAnswer(ANSWER_KEY)
                .examSource(SOURCE)
                .examVerdict(verdict)
                .build();
    }
}
