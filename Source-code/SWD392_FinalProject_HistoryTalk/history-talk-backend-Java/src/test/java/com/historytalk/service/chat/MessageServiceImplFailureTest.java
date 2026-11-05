package com.historytalk.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.dto.chat.SendMessageResponse;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;
import com.historytalk.entity.enums.QuestType;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.gamification.GamificationService;
import com.historytalk.service.school.token.DailyTokenAllowanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplFailureTest {

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

    private final UUID userId = UUID.randomUUID();

    private MessageServiceImpl service;
    private ChatSession session;
    private SendMessageRequest request;

    @BeforeEach
    void setUp() {
        service = new MessageServiceImpl(messageRepository, chatSessionRepository, aiServiceClient, new ObjectMapper(),
                userRepository, gamificationService, dailyTokenAllowanceService, examSessionService,
                new ChatTokenCharger(userRepository, dailyTokenAllowanceService),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));

        User user = new User();
        user.setUid(userId);
        user.setRole(UserRole.CUSTOMER);
        user.setToken(100);

        session = ChatSession.builder()
                .sessionId(UUID.randomUUID())
                .user(user)
                .character(Character.builder().characterId(UUID.randomUUID()).name("Ngô Quyền").background("Tiểu sử").build())
                .build();
        request = new SendMessageRequest();
        ReflectionTestUtils.setField(request, "sessionId", session.getSessionId().toString());
        ReflectionTestUtils.setField(request, "content", "Câu hỏi");

        lenient().when(chatSessionRepository.findActiveBySessionIdAndUserUid(session.getSessionId(), userId))
                .thenReturn(Optional.of(session));
        lenient().when(messageRepository.findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId(), false))
                .thenReturn(List.of());
        lenient().when(chatSessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        lenient().when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message message = inv.getArgument(0);
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID());
            }
            return message;
        });
    }

    @Test
    void obsceneMessageIsRejectedBeforeAnythingIsSavedOrSentToAi() {
        ReflectionTestUtils.setField(request, "content", "địt mẹ");

        assertThatThrownBy(() -> service.sendMessage(userId.toString(), request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("INAPPROPRIATE_CONTENT: ");
        assertThatThrownBy(() -> service.sendMessageStream(userId.toString(), request))
                .isInstanceOf(InvalidRequestException.class);

        verify(messageRepository, never()).save(any(Message.class));
        verify(aiServiceClient, never()).chat(any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(aiServiceClient, never()).streamChat(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(examSessionService, never()).submitAnswer(any(), any());
        verify(userRepository, never()).deductTokens(any(), anyInt());
    }

    @Test
    void checkOnlyValidatesTheContent() {
        assertThatThrownBy(() -> service.checkMessageContent("địt mẹ"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("INAPPROPRIATE_CONTENT: ");

        service.checkMessageContent("Trận Bạch Đằng diễn ra năm nào?");

        verify(messageRepository, never()).save(any(Message.class));
        verify(aiServiceClient, never()).chat(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void failedChatRemovesTheUserMessage() {
        when(aiServiceClient.chat(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new SystemException("AI lỗi"));

        assertThatThrownBy(() -> service.sendMessage(userId.toString(), request)).isInstanceOf(SystemException.class);

        verify(messageRepository).delete(any(Message.class));
        verify(userRepository, never()).deductTokens(any(), anyInt());
    }

    @Test
    void replyIsKeptWhenTheQuestUpdateFails() {
        when(aiServiceClient.chat(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new AiServiceClient.AiChatResult("Ta là Ngô Quyền.", List.of(), List.of(),
                        new AiMetricsService.TokenUsage("ollama", "test", 10, 5, 15)));
        doThrow(new RuntimeException("quest down")).when(gamificationService).recordProgress(userId.toString(), QuestType.CHAT);

        SendMessageResponse response = service.sendMessage(userId.toString(), request);

        assertThat(response.getAssistantMessage().getContent()).isEqualTo("Ta là Ngô Quyền.");
        assertThat(response.getRemainingTokens()).isEqualTo(85);
        verify(userRepository).deductTokens(userId, 15);
    }

    @Test
    void streamErrorSavesNoAssistantMessageAndRemovesTheUserMessage() {
        stubStream(List.of(
                "data: {\"type\":\"text\",\"data\":\"Ta là\"}",
                "data: {\"type\":\"error\",\"message\":\"Dịch vụ AI đang gặp sự cố\"}"));

        service.sendMessageStream(userId.toString(), request);

        verify(messageRepository, timeout(3000)).delete(any(Message.class));
        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).noneMatch(message -> Boolean.TRUE.equals(message.getIsFromAi()));
        verify(userRepository, never()).deductTokens(any(), anyInt());
    }

    @Test
    void emptyStreamIsTreatedAsAFailure() {
        stubStream(List.of());

        service.sendMessageStream(userId.toString(), request);

        verify(messageRepository, timeout(3000)).delete(any(Message.class));
        verify(userRepository, never()).deductTokens(any(), anyInt());
    }

    @Test
    void completedStreamSavesTheReplyAndCharges() {
        stubStream(List.of(
                "data: {\"type\":\"text\",\"data\":\"Ta là Ngô Quyền.\"}",
                "data: {\"type\":\"metadata\",\"data\":{\"promptTokens\":10,\"completionTokens\":5,\"suggestedQuestions\":[],\"quotes_used\":[\"Tư liệu\"]}}"));

        service.sendMessageStream(userId.toString(), request);

        verify(userRepository, timeout(3000)).deductTokens(userId, 15);
        verify(messageRepository, never()).delete(any(Message.class));
    }

    @SuppressWarnings("unchecked")
    private void stubStream(List<String> lines) {
        doAnswer(inv -> {
            Consumer<String> onData = inv.getArgument(7);
            Runnable onComplete = inv.getArgument(8);
            lines.forEach(onData);
            onComplete.run();
            return null;
        }).when(aiServiceClient).streamChat(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any());
    }
}
