package com.historytalk.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.chat.AiServiceClient;
import com.historytalk.service.chat.AiServiceClient.AuthoringResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAuthoringServiceImplTest {

    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private HistoricalContextRepository contextRepository;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID contextId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private AiAuthoringServiceImpl service;
    private HistoricalContext context;

    @BeforeEach
    void setUp() {
        service = new AiAuthoringServiceImpl(aiServiceClient, contextRepository, characterRepository, userRepository,
                objectMapper, new TransactionTemplate(mock(PlatformTransactionManager.class)));
        context = HistoricalContext.builder()
                .contextId(contextId)
                .name("Trận Bạch Đằng")
                .year(938)
                .build();
    }

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private Character character(String name) {
        return Character.builder().characterId(UUID.randomUUID()).name(name).background("Tiểu sử").build();
    }

    private ObjectNode body(String json) throws Exception {
        return (ObjectNode) objectMapper.readTree(json.replace("CTX", contextId.toString()));
    }

    @Test
    void storyGetsTheContextAndItsCharactersPlusTheNarrator() throws Exception {
        signIn("CONTENT_ADMIN");
        Character ngoQuyen = character("Ngô Quyền");
        Character narrator = character("Dương Đình Nghệ");
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(context));
        when(characterRepository.findByContextIdOrderByNameAsc(contextId, true, false)).thenReturn(List.of(ngoQuyen));
        when(characterRepository.findAllById(any())).thenReturn(List.of(narrator));
        when(aiServiceClient.forwardAuthoring(eq("POST"), eq("/v1/ai/story/generate"), anyString()))
                .thenReturn(new AuthoringResponse(200, "{}"));

        service.generateStory(body("{\"mode\":\"full\",\"contextId\":\"CTX\",\"narratorId\":\""
                + narrator.getCharacterId() + "\"}"));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(aiServiceClient).forwardAuthoring(eq("POST"), eq("/v1/ai/story/generate"), sent.capture());
        JsonNode forwarded = objectMapper.readTree(sent.getValue());
        assertThat(forwarded.path("contextData").path("name").asText()).isEqualTo("Trận Bạch Đằng");
        assertThat(forwarded.path("contextData").path("description").asText()).isEmpty();
        assertThat(forwarded.path("characterIds")).extracting(JsonNode::asText)
                .containsExactly(ngoQuyen.getCharacterId().toString(), narrator.getCharacterId().toString());
        assertThat(forwarded.path("characterData").get(0).path("name").asText()).isEqualTo("Ngô Quyền");
    }

    @Test
    void teacherCannotDraftForAnotherSchoolsContext() throws Exception {
        signIn("TEACHER");
        context.setSchool(School.builder().id(UUID.randomUUID()).build());
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(context));
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().uid(userId).school(School.builder().id(UUID.randomUUID()).build()).build()));

        assertThatThrownBy(() -> service.planBattleMap(body("{\"contextId\":\"CTX\"}")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(aiServiceClient, never()).forwardAuthoring(any(), any(), any());
    }

    @Test
    void deletedContextIsNotFound() throws Exception {
        context.setDeletedAt(java.time.LocalDateTime.now());
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(context));

        assertThatThrownBy(() -> service.generateStory(body("{\"contextId\":\"CTX\"}")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void jobIdMustLookLikeAJobId() {
        assertThatThrownBy(() -> service.getJob("../health")).isInstanceOf(InvalidRequestException.class);
        verify(aiServiceClient, never()).forwardAuthoring(any(), any(), any());
    }
}
