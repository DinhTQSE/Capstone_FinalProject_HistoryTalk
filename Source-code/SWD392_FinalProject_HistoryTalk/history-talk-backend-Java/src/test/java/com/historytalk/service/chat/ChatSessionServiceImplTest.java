package com.historytalk.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.ChatSessionResponse;
import com.historytalk.dto.chat.CreateChatSessionRequest;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.user.User;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

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

    @InjectMocks
    private ChatSessionServiceImpl chatSessionService;

    @Test
    void createSessionAllowsPublicUserToUsePublishedActiveCharacterAndContext() {
        UUID userId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID contextId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User user = User.builder().uid(userId).userName("user").build();
        Character character = Character.builder()
                .characterId(characterId)
                .name("Tran Hung Dao")
                .background("Historical character background")
                .isPublished(true)
                .createdBy(user)
                .build();
        HistoricalContext context = HistoricalContext.builder()
                .contextId(contextId)
                .name("Bach Dang")
                .description("Historical context description")
                .isPublished(true)
                .createdBy(user)
                .build();
        CreateChatSessionRequest request = new CreateChatSessionRequest();
        ReflectionTestUtils.setField(request, "characterId", characterId.toString());
        ReflectionTestUtils.setField(request, "contextId", contextId.toString());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(context));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            if (session.getSessionId() == null) {
                session.setSessionId(sessionId);
            }
            return session;
        });
        when(aiServiceClient.chat(anyString(), anyString(), anyString(), anyList(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new AiServiceClient.AiChatResult("Xin chao", List.of(), List.of(), null));

        ChatSessionResponse response = chatSessionService.createSession(userId.toString(), "USER", request);

        assertThat(response.getId()).isEqualTo(sessionId.toString());
        assertThat(response.getCharacterId()).isEqualTo(characterId.toString());
        assertThat(response.getContextId()).isEqualTo(contextId.toString());
    }
}
