package com.historytalk.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.ChatSessionResponse;
import com.historytalk.dto.chat.CreateChatSessionRequest;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;
import com.historytalk.entity.enums.ChatSessionMode;
import com.historytalk.entity.user.User;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setSessionId(UUID.randomUUID());
            return session;
        });
    }

    @Test
    void examSessionOpensWithItsFirstQuestionInsteadOfAGreeting() {
        ReflectionTestUtils.setField(request, "mode", ChatSessionMode.EXAM);
        when(examSessionService.startExam(any(ChatSession.class)))
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
        when(examSessionService.startExam(any(ChatSession.class)))
                .thenReturn(Message.builder().content("Câu hỏi 1?").build());

        chatSessionService.createSession(userId.toString(), "USER", request);

        ArgumentCaptor<ChatSession> saved = ArgumentCaptor.forClass(ChatSession.class);
        verify(examSessionService).startExam(saved.capture());
        assertThat(saved.getValue().getExamQuestionCount()).isEqualTo(8);
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
}
