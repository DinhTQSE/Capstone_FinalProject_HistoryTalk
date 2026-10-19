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

import java.util.Collections;
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
        character.getHistoricalContexts().add(context);
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

    @Test
    void createSessionThrowsWhenContextDoesNotBelongToCharacter() {
        UUID userId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID contextId = UUID.randomUUID();

        User user = User.builder().uid(userId).userName("user").build();
        Character character = Character.builder()
                .characterId(characterId)
                .name("Tran Hung Dao")
                .background("Historical character background")
                .isPublished(true)
                .createdBy(user)
                .build();
        HistoricalContext unrelatedContext = HistoricalContext.builder()
                .contextId(contextId)
                .name("Dien Bien Phu")
                .description("Unrelated context")
                .isPublished(true)
                .createdBy(user)
                .build();

        CreateChatSessionRequest request = new CreateChatSessionRequest();
        ReflectionTestUtils.setField(request, "characterId", characterId.toString());
        ReflectionTestUtils.setField(request, "contextId", contextId.toString());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(unrelatedContext));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                chatSessionService.createSession(userId.toString(), "USER", request))
                .isInstanceOf(com.historytalk.exception.InvalidRequestException.class)
                .hasMessageContaining("Nhân vật lịch sử này không tham gia vào bối cảnh/sự kiện đã chọn");
    }

    @Test
    void getScenariosReturnsMappedScenariosSuccessfully() {
        UUID characterId = UUID.randomUUID();
        UUID contextId = UUID.randomUUID();
        Character character = Character.builder()
                .characterId(characterId)
                .name("Đại tướng Võ Nguyên Giáp")
                .title("Tổng tư lệnh")
                .imageUrl("https://example.com/vng.jpg")
                .personality("Điềm đạm")
                .isPublished(true)
                .build();
        HistoricalContext context = HistoricalContext.builder()
                .contextId(contextId)
                .name("Chiến dịch Điện Biên Phủ")
                .era(com.historytalk.entity.enums.EventEra.CONTEMPORARY)
                .year(1954)
                .imageUrl("https://example.com/dbp.jpg")
                .description("Chiến dịch chấn động địa cầu")
                .isPublished(true)
                .build();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        List<Object[]> rows = Collections.singletonList(new Object[]{character, context});
        org.springframework.data.domain.Page<Object[]> page = new org.springframework.data.domain.PageImpl<>(
                rows, pageable, 1
        );

        when(characterRepository.findScenarios(any(), any(), any(), org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(page);

        com.historytalk.dto.PaginatedResponse<com.historytalk.dto.chat.ChatScenarioResponse> response =
                chatSessionService.getScenarios("Điện Biên Phủ", com.historytalk.entity.enums.EventEra.CONTEMPORARY, pageable);

        assertThat(response.getContent()).hasSize(1);
        com.historytalk.dto.chat.ChatScenarioResponse scenario = response.getContent().get(0);
        assertThat(scenario.getCharacterId()).isEqualTo(characterId);
        assertThat(scenario.getCharacterName()).isEqualTo("Đại tướng Võ Nguyên Giáp");
        assertThat(scenario.getContextId()).isEqualTo(contextId);
        assertThat(scenario.getContextName()).isEqualTo("Chiến dịch Điện Biên Phủ");
        assertThat(scenario.getContextYear()).isEqualTo(1954);
        assertThat(scenario.getScope()).isEqualTo("GLOBAL");
    }
}
