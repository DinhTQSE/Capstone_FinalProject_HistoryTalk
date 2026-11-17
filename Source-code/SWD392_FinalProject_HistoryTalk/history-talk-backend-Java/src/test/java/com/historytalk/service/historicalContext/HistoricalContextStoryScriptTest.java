package com.historytalk.service.historicalContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.historicalContext.HistoricalContextResponse;
import com.historytalk.dto.historicalContext.UpdateHistoricalContextRequest;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.security.ContentAccessPolicy;
import com.historytalk.service.map.MapPinService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** storyScript on historical contexts: validation, absent-vs-null on update, and draft hiding for learners. */
@ExtendWith(MockitoExtension.class)
class HistoricalContextStoryScriptTest {

    @Mock
    private HistoricalContextRepository contextRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private MapPinService mapPinService;
    @Mock
    private ContentAccessPolicy contentAccessPolicy;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private HistoricalContextServiceImpl service;

    private final ObjectMapper json = new ObjectMapper();
    private HistoricalContext context;

    @BeforeEach
    void setUp() {
        context = HistoricalContext.builder()
                .contextId(UUID.randomUUID())
                .name("Trận Bạch Đằng")
                .isPublished(true)
                .createdBy(User.builder().uid(UUID.randomUUID()).userName("staff").build())
                .build();
        lenient().when(contextRepository.findById(context.getContextId())).thenReturn(Optional.of(context));
        lenient().when(contextRepository.save(any(HistoricalContext.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(contentAccessPolicy.canView(any(HistoricalContext.class), any())).thenReturn(true);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static Map<String, Object> script(String status) {
        Map<String, Object> s = new HashMap<>();
        s.put("version", 1);
        s.put("title", "Ngô Quyền kể chuyện Bạch Đằng");
        s.put("narratorId", null);
        s.put("status", status);
        s.put("updatedAt", "2026-11-15T09:30:00.000Z");
        s.put("nodes", List.of(Map.of("id", "start", "type", "start")));
        s.put("edges", List.of());
        return s;
    }

    private HistoricalContextResponse update(String body) throws Exception {
        loginAs("CONTENT_ADMIN");
        UpdateHistoricalContextRequest req = json.readValue(body, UpdateHistoricalContextRequest.class);
        return service.updateContext(context.getContextId().toString(), req, UUID.randomUUID().toString(), "CONTENT_ADMIN");
    }

    @Test
    void jsonAbsentKeyAndExplicitNullAreDistinguished() throws Exception {
        UpdateHistoricalContextRequest absent = json.readValue("{\"name\":\"Bạch Đằng\"}", UpdateHistoricalContextRequest.class);
        UpdateHistoricalContextRequest explicitNull = json.readValue("{\"storyScript\":null}", UpdateHistoricalContextRequest.class);

        assertThat(absent.isStoryScriptPresent()).isFalse();
        assertThat(explicitNull.isStoryScriptPresent()).isTrue();
        assertThat(explicitNull.getStoryScript()).isNull();
    }

    @Test
    void responseAlwaysContainsStoryScriptKey() throws Exception {
        String body = json.writeValueAsString(HistoricalContextResponse.builder().contextId("x").build());
        assertThat(json.readTree(body).has("storyScript")).isTrue();
        assertThat(json.readTree(body).get("storyScript").isNull()).isTrue();
    }

    @Test
    void updateSavesValidScriptThenAbsentKeyKeepsItAndNullDeletesIt() throws Exception {
        HistoricalContextResponse saved = update(json.writeValueAsString(Map.of("storyScript", script("draft"))));
        assertThat(context.getStoryScript()).contains("Ngô Quyền");
        assertThat(saved.getStoryScript()).isInstanceOf(Map.class);

        update("{\"name\":\"Trận Bạch Đằng 938\"}");
        assertThat(context.getStoryScript()).contains("Ngô Quyền");

        HistoricalContextResponse cleared = update("{\"storyScript\":null}");
        assertThat(context.getStoryScript()).isNull();
        assertThat(cleared.getStoryScript()).isNull();
    }

    @Test
    void invalidScriptsAreRejectedWith400() {
        Map<String, Object> badVersion = script("draft");
        badVersion.put("version", 2);
        Map<String, Object> badTitle = script("draft");
        badTitle.put("title", 42);
        Map<String, Object> badStatus = script("archived");
        Map<String, Object> badNodes = script("draft");
        badNodes.put("nodes", "not-an-array");
        Map<String, Object> missingEdges = script("draft");
        missingEdges.remove("edges");
        Map<String, Object> tooManyNodes = script("draft");
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int i = 0; i <= HistoricalContextServiceImpl.STORY_MAX_NODES; i++) nodes.add(Map.of("id", "n" + i));
        tooManyNodes.put("nodes", nodes);
        Map<String, Object> tooBig = script("draft");
        tooBig.put("nodes", List.of(Map.of("id", "big", "data", Map.of("note", "x".repeat(HistoricalContextServiceImpl.STORY_MAX_BYTES)))));

        for (Object bad : List.of(badVersion, badTitle, badStatus, badNodes, missingEdges, tooManyNodes, tooBig, List.of(1, 2), "\"text\"")) {
            assertThatThrownBy(() -> update("{\"storyScript\":" + (bad instanceof String s ? s : json.writeValueAsString(bad)) + "}"))
                    .as("payload %s", bad instanceof Map<?, ?> m ? m.keySet() : bad)
                    .isInstanceOf(InvalidRequestException.class);
        }
        assertThat(context.getStoryScript()).isNull();
    }

    @Test
    void learnersOnlySeePublishedScriptStaffSeeDrafts() throws Exception {
        context.setStoryScript(json.writeValueAsString(script("draft")));

        loginAs("SCHOOL_STUDENT");
        assertThat(service.getContextById(context.getContextId().toString(), "SCHOOL_STUDENT").getStoryScript()).isNull();
        loginAs("TEACHER");
        assertThat(service.getContextById(context.getContextId().toString(), "TEACHER").getStoryScript()).isNull();
        SecurityContextHolder.clearContext();
        assertThat(service.getContextById(context.getContextId().toString(), "USER").getStoryScript()).isNull();

        loginAs("CONTENT_ADMIN");
        assertThat(service.getContextById(context.getContextId().toString(), "CONTENT_ADMIN").getStoryScript()).isNotNull();

        context.setStoryScript(json.writeValueAsString(script("published")));
        loginAs("CUSTOMER");
        Object published = service.getContextById(context.getContextId().toString(), "CUSTOMER").getStoryScript();
        assertThat(published).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) published).get("title")).isEqualTo("Ngô Quyền kể chuyện Bạch Đằng");
    }

    @Test
    void nonStaffCannotWriteStoryScript() {
        loginAs("TEACHER");
        UpdateHistoricalContextRequest req = new UpdateHistoricalContextRequest();
        req.setStoryScript(script("draft"));

        assertThatThrownBy(() -> service.updateContext(context.getContextId().toString(), req, UUID.randomUUID().toString(), "TEACHER"))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(context.getStoryScript()).isNull();
    }
}
