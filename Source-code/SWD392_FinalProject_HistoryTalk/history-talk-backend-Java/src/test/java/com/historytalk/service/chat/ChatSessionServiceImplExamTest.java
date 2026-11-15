package com.historytalk.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.ChatSessionResponse;
import com.historytalk.dto.chat.CreateChatSessionRequest;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;
import com.historytalk.entity.enums.ChatSessionMode;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.UserTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplExamTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private HistoricalContextRepository contextRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ExamSessionService examSessionService;
    @Mock
    private UserTierRepository userTierRepository;

    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks
    private ChatSessionServiceImpl chatSessionService;

    private final UUID userId = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();
    private CreateChatSessionRequest request;

    @BeforeEach
    void setUp() {
        User user = User.builder().uid(userId).userName("user").build();
        Character character = Character.builder()
                .characterId(characterId)
                .name("Ngô Quyền")
                .background("Tiểu sử")
                .isPublished(true)
                .createdBy(user)
                .build();
        request = new CreateChatSessionRequest();
        ReflectionTestUtils.setField(request, "characterId", characterId.toString());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        lenient().when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setSessionId(UUID.randomUUID());
            return session;
        });
    }

    @Test
    void examSessionOpensWithItsFirstQuestionInsteadOfAGreeting() {
        ReflectionTestUtils.setField(request, "mode", ChatSessionMode.EXAM);
        when(examSessionService.startExam(any(UUID.class)))
                .thenReturn(Message.builder().content("Mở đầu\n---\nCâu hỏi 1?").build());

        ChatSessionResponse response = chatSessionService.createSession(userId.toString(), "USER", request);

        assertThat(response.getMode()).isEqualTo("EXAM");
        assertThat(response.getExamQuestionCount()).isEqualTo(5);
        assertThat(response.getLastMessage()).endsWith("Câu hỏi 1?");
        assertThat(response.getTitle()).isEqualTo("Vấn đáp với Ngô Quyền");
        verify(aiServiceClient, never()).chat(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void examSessionKeepsTheRequestedQuestionCount() {
        ReflectionTestUtils.setField(request, "mode", ChatSessionMode.EXAM);
        ReflectionTestUtils.setField(request, "examQuestionCount", 8);
        when(examSessionService.startExam(any(UUID.class)))
                .thenReturn(Message.builder().content("Câu hỏi 1?").build());

        chatSessionService.createSession(userId.toString(), "USER", request);

        ArgumentCaptor<ChatSession> saved = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository).save(saved.capture());
        assertThat(saved.getValue().getExamQuestionCount()).isEqualTo(8);
    }

    @Test
    void freeB2cCustomerExamSessionIsCappedAtTwoQuestions() {
        User freeCustomer = User.builder()
                .uid(userId)
                .userName("free_customer")
                .role(UserRole.CUSTOMER)
                .token(1500)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(freeCustomer));
        when(userTierRepository.findCurrentActiveByUid(eq(userId), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        ReflectionTestUtils.setField(request, "mode", ChatSessionMode.EXAM);
        ReflectionTestUtils.setField(request, "examQuestionCount", 5);
        when(examSessionService.startExam(any(UUID.class)))
                .thenReturn(Message.builder().content("Câu hỏi 1?").build());

        ChatSessionResponse response = chatSessionService.createSession(userId.toString(), "CUSTOMER", request);

        assertThat(response.getExamQuestionCount()).isEqualTo(2);
    }

    @Test
    void paidPlusOrProB2cCustomerExamSessionAllowsFullQuestionCount() {
        User plusCustomer = User.builder()
                .uid(userId)
                .userName("plus_customer")
                .role(UserRole.CUSTOMER)
                .token(5000)
                .build();
        Tier plusTier = Tier.builder().title("plus").amount(29500).limitedToken(150000).noMonth(1).build();
        UserTier activeSub = UserTier.builder().user(plusCustomer).tier(plusTier).isActive(true).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(plusCustomer));
        when(userTierRepository.findCurrentActiveByUid(eq(userId), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activeSub));

        ReflectionTestUtils.setField(request, "mode", ChatSessionMode.EXAM);
        ReflectionTestUtils.setField(request, "examQuestionCount", 10);
        when(examSessionService.startExam(any(UUID.class)))
                .thenReturn(Message.builder().content("Câu hỏi 1?").build());

        ChatSessionResponse response = chatSessionService.createSession(userId.toString(), "CUSTOMER", request);

        assertThat(response.getExamQuestionCount()).isEqualTo(10);
    }

    @Test
    void sessionWithoutModeStaysANormalChat() {
        when(aiServiceClient.chat(anyString(), anyString(), anyString(), anyList(), any(), any(), anyBoolean()))
                .thenReturn(new AiServiceClient.AiChatResult("Xin chào", List.of(), List.of(), null));

        ChatSessionResponse response = chatSessionService.createSession(userId.toString(), "USER", request);

        assertThat(response.getMode()).isEqualTo("CHAT");
        assertThat(response.getExamQuestionCount()).isNull();
        verify(examSessionService, never()).startExam(any());
    }

    @Test
    void examCanBeStartedFromTheLearnersChatWithTheSameCharacter() {
        UUID chatId = UUID.randomUUID();
        ChatSession chat = ChatSession.builder()
                .sessionId(chatId)
                .mode(ChatSessionMode.CHAT)
                .character(Character.builder().characterId(characterId).build())
                .build();
        when(chatSessionRepository.findActiveBySessionIdAndUserUid(chatId, userId)).thenReturn(Optional.of(chat));
        ReflectionTestUtils.setField(request, "mode", ChatSessionMode.EXAM);
        ReflectionTestUtils.setField(request, "sourceSessionId", chatId.toString());
        when(examSessionService.startExam(any(UUID.class))).thenReturn(Message.builder().content("Câu hỏi 1?").build());

        ChatSessionResponse response = chatSessionService.createSession(userId.toString(), "USER", request);

        assertThat(response.getSourceSessionId()).isEqualTo(chatId.toString());
    }

    @Test
    void examCannotBeStartedFromAnotherCharactersChat() {
        UUID chatId = UUID.randomUUID();
        ChatSession chat = ChatSession.builder()
                .sessionId(chatId)
                .mode(ChatSessionMode.CHAT)
                .character(Character.builder().characterId(UUID.randomUUID()).build())
                .build();
        when(chatSessionRepository.findActiveBySessionIdAndUserUid(chatId, userId)).thenReturn(Optional.of(chat));
        ReflectionTestUtils.setField(request, "mode", ChatSessionMode.EXAM);
        ReflectionTestUtils.setField(request, "sourceSessionId", chatId.toString());

        assertThatThrownBy(() -> chatSessionService.createSession(userId.toString(), "USER", request))
                .isInstanceOf(InvalidRequestException.class);
        verify(examSessionService, never()).startExam(any());
    }

    @Test
    void examSessionIsRemovedWhenTheFirstQuestionCannotBeGenerated() {
        ReflectionTestUtils.setField(request, "mode", ChatSessionMode.EXAM);
        when(examSessionService.startExam(any(UUID.class))).thenThrow(new InvalidRequestException("Chưa có tư liệu"));

        assertThatThrownBy(() -> chatSessionService.createSession(userId.toString(), "USER", request))
                .isInstanceOf(InvalidRequestException.class);

        verify(chatSessionRepository).deleteById(any(UUID.class));
    }
}
