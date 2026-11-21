package com.historytalk.service.localcontent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.localcontent.LocalContentResponse;
import com.historytalk.dto.localcontent.UpdateLocalContextRequest;
import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.ForbiddenException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.ClassLocalContentRepository;
import com.historytalk.repository.LocalCharacterRepository;
import com.historytalk.repository.LocalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Teachers write a story script on their own local contexts; students only read a published one. */
@ExtendWith(MockitoExtension.class)
class LocalContextStoryScriptTest {

    @Mock
    private LocalContextRepository localContextRepo;
    @Mock
    private LocalCharacterRepository localCharacterRepo;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private ClassLocalContentRepository classLocalContentRepo;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private LocalContentServiceImpl localContentService;

    private final ObjectMapper json = new ObjectMapper();
    private School school;
    private User teacher;
    private UserPrincipal teacherPrincipal;
    private HistoricalContext ctx;

    @BeforeEach
    void setUp() {
        school = School.builder().id(UUID.randomUUID()).name("THPT History").build();
        teacher = User.builder().uid(UUID.randomUUID()).userName("gv").role(UserRole.TEACHER).school(school).build();
        teacherPrincipal = new UserPrincipal(teacher);
        ctx = HistoricalContext.builder()
                .contextId(UUID.randomUUID())
                .name("Khởi nghĩa Hai Bà Trưng")
                .school(school)
                .contentScope(ContentScope.CLASS_ONLY)
                .contentStatus(ContentStatus.ACTIVE)
                .isPublished(true)
                .createdBy(teacher)
                .build();
        lenient().when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        lenient().when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        lenient().when(localContextRepo.findByIdAndSchool(ctx.getContextId(), school.getId())).thenReturn(Optional.of(ctx));
        lenient().when(localContextRepo.save(any(HistoricalContext.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(classLocalContentRepo.findClassIdsByEntityIdAndEntityType(any(), any())).thenReturn(List.of());
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
        s.put("title", "Hai Bà Trưng kể chuyện");
        s.put("narratorId", null);
        s.put("status", status);
        s.put("updatedAt", "2026-11-21T09:00:00.000Z");
        s.put("nodes", List.of(Map.of("id", "start", "type", "start")));
        s.put("edges", List.of());
        return s;
    }

    private LocalContentResponse update(String body) throws Exception {
        loginAs("TEACHER");
        UpdateLocalContextRequest req = json.readValue(body, UpdateLocalContextRequest.class);
        return localContentService.updateLocalContext(ctx.getContextId().toString(), req, teacherPrincipal);
    }

    @Test
    void teacherSavesScriptThenAbsentKeyKeepsItAndNullDeletesIt() throws Exception {
        LocalContentResponse saved = update(json.writeValueAsString(Map.of("storyScript", script("draft"))));
        assertThat(ctx.getStoryScript()).contains("Hai Bà Trưng kể chuyện");
        assertThat(saved.getStoryScript()).isInstanceOf(Map.class);

        update("{\"name\":\"Khởi nghĩa Hai Bà Trưng năm 40\"}");
        assertThat(ctx.getStoryScript()).contains("Hai Bà Trưng kể chuyện");

        LocalContentResponse cleared = update("{\"storyScript\":null}");
        assertThat(ctx.getStoryScript()).isNull();
        assertThat(cleared.getStoryScript()).isNull();
    }

    @Test
    void invalidScriptIsRejectedWith400() {
        Map<String, Object> bad = script("archived");
        assertThatThrownBy(() -> update(json.writeValueAsString(Map.of("storyScript", bad))))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(ctx.getStoryScript()).isNull();
    }

    @Test
    void anotherTeacherCannotWriteTheScript() {
        User colleague = User.builder().uid(UUID.randomUUID()).userName("gv2").role(UserRole.TEACHER).school(school).build();
        when(userRepository.findById(colleague.getUid())).thenReturn(Optional.of(colleague));
        loginAs("TEACHER");
        UpdateLocalContextRequest req = new UpdateLocalContextRequest();
        req.setStoryScript(script("draft"));

        assertThatThrownBy(() -> localContentService.updateLocalContext(
                ctx.getContextId().toString(), req, new UserPrincipal(colleague)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ctx.getStoryScript()).isNull();
    }

    @Test
    void studentsOnlySeePublishedScriptTeachersSeeDrafts() throws Exception {
        ctx.setStoryScript(json.writeValueAsString(script("draft")));
        loginAs("SCHOOL_STUDENT");
        // getLocalContextById is the teacher read; the mapper is shared, so the role decides what is shown.
        assertThat(localContentService.getLocalContextById(ctx.getContextId().toString(), teacherPrincipal).getStoryScript()).isNull();

        loginAs("TEACHER");
        assertThat(localContentService.getLocalContextById(ctx.getContextId().toString(), teacherPrincipal).getStoryScript()).isNotNull();

        ctx.setStoryScript(json.writeValueAsString(script("published")));
        loginAs("SCHOOL_STUDENT");
        Object published = localContentService.getLocalContextById(ctx.getContextId().toString(), teacherPrincipal).getStoryScript();
        assertThat(published).isInstanceOf(Map.class);
    }

    private Character character(String name, boolean published, ContentScope scope) {
        Character c = Character.builder()
                .characterId(UUID.randomUUID())
                .name(name)
                .school(school)
                .contentScope(scope)
                .contentStatus(published ? ContentStatus.ACTIVE : ContentStatus.DRAFT)
                .isPublished(published)
                .createdBy(teacher)
                .historicalContexts(new java.util.HashSet<>(Set.of(ctx)))
                .build();
        lenient().when(localCharacterRepo.findById(c.getCharacterId())).thenReturn(Optional.of(c));
        return c;
    }

    private static Map<String, Object> publishedWithSpeaker(Character speaker) {
        Map<String, Object> s = script("published");
        s.put("nodes", List.of(
                Map.of("id", "start", "type", "start", "data", Map.of("kind", "start")),
                Map.of("id", "l1", "type", "line", "data", Map.of("kind", "line", "speakerId", speaker.getCharacterId().toString()))));
        return s;
    }

    @Test
    void publishingNeedsAPublishedContext() {
        ctx.setIsPublished(false);
        ctx.setContentStatus(ContentStatus.DRAFT);
        assertThatThrownBy(() -> update(json.writeValueAsString(Map.of("storyScript", script("published")))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("xuất bản bối cảnh");
        // A draft story can still be saved on a draft context.
        org.assertj.core.api.Assertions.assertThatCode(() -> update(json.writeValueAsString(Map.of("storyScript", script("draft")))))
                .doesNotThrowAnyException();
    }

    @Test
    void publishingRejectsAnUnpublishedSpeaker() {
        Character draft = character("Trưng Nhị", false, ContentScope.SCHOOL_WIDE);
        assertThatThrownBy(() -> update(json.writeValueAsString(Map.of("storyScript", publishedWithSpeaker(draft)))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Trưng Nhị (chưa xuất bản)");
    }

    @Test
    void publishingRejectsAClassOnlySpeakerOutsideTheContextClasses() {
        UUID classA = UUID.randomUUID();
        UUID classB = UUID.randomUUID();
        Character narrow = character("Thi Sách", true, ContentScope.CLASS_ONLY);
        when(classLocalContentRepo.findClassIdsByEntityIdAndEntityType(ctx.getContextId(), EntityType.CONTEXT)).thenReturn(List.of(classA, classB));
        when(classLocalContentRepo.findClassIdsByEntityIdAndEntityType(narrow.getCharacterId(), EntityType.CHARACTER)).thenReturn(List.of(classA));

        assertThatThrownBy(() -> update(json.writeValueAsString(Map.of("storyScript", publishedWithSpeaker(narrow)))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Thi Sách (phạm vi lớp hẹp hơn bối cảnh)");
    }

    @Test
    void publishingAcceptsPublishedLinkedSpeakers() throws Exception {
        Character ok = character("Trưng Trắc", true, ContentScope.SCHOOL_WIDE);
        Map<String, Object> s = publishedWithSpeaker(ok);
        s.put("narratorId", ok.getCharacterId().toString());

        update(json.writeValueAsString(Map.of("storyScript", s)));

        assertThat(ctx.getStoryScript()).contains("\"published\"");
    }

    // ── Cascade: hiding a story character moves its published story back to draft ──

    private Character publishedStoryWith(String name) throws Exception {
        Character c = character(name, true, ContentScope.SCHOOL_WIDE);
        lenient().when(localCharacterRepo.findByIdAndSchool(c.getCharacterId(), school.getId())).thenReturn(Optional.of(c));
        lenient().when(localCharacterRepo.save(any(Character.class))).thenAnswer(inv -> inv.getArgument(0));
        ctx.setStoryScript(json.writeValueAsString(publishedWithSpeaker(c)));
        return c;
    }

    private String storyStatus() throws Exception {
        return (String) json.readValue(ctx.getStoryScript(), Map.class).get("status");
    }

    @Test
    void deletingAStoryCharacterMovesTheStoryToDraftAndKeepsItsContent() throws Exception {
        Character c = publishedStoryWith("Trưng Trắc");
        loginAs("TEACHER");

        localContentService.deleteLocalCharacter(c.getCharacterId().toString(), teacherPrincipal);

        assertThat(storyStatus()).isEqualTo("draft");
        assertThat(ctx.getStoryScript()).contains(c.getCharacterId().toString());
    }

    @Test
    void unpublishingAStoryCharacterMovesTheStoryToDraft() throws Exception {
        Character c = publishedStoryWith("Trưng Trắc");
        loginAs("TEACHER");
        com.historytalk.dto.localcontent.UpdateLocalCharacterRequest req = new com.historytalk.dto.localcontent.UpdateLocalCharacterRequest();
        req.setIsPublished(false);

        localContentService.updateLocalCharacter(c.getCharacterId().toString(), req, teacherPrincipal);

        assertThat(storyStatus()).isEqualTo("draft");
    }

    @Test
    void unlinkingAStoryCharacterMovesTheStoryToDraft() throws Exception {
        Character c = publishedStoryWith("Trưng Trắc");
        loginAs("TEACHER");

        localContentService.unlinkCharacterFromContext(c.getCharacterId().toString(), ctx.getContextId().toString(), teacherPrincipal);

        assertThat(storyStatus()).isEqualTo("draft");
    }

    @Test
    void editingAStoryCharacterThatStaysVisibleKeepsTheStoryPublished() throws Exception {
        Character c = publishedStoryWith("Trưng Trắc");
        loginAs("TEACHER");
        com.historytalk.dto.localcontent.UpdateLocalCharacterRequest req = new com.historytalk.dto.localcontent.UpdateLocalCharacterRequest();
        req.setName("Trưng Trắc (Hai Bà Trưng)");

        localContentService.updateLocalCharacter(c.getCharacterId().toString(), req, teacherPrincipal);

        assertThat(storyStatus()).isEqualTo("published");
    }

    @Test
    void publishedStoriesOfACharacterAreListedForTheWarning() throws Exception {
        Character c = publishedStoryWith("Trưng Trắc");
        loginAs("TEACHER");

        var usages = localContentService.getCharacterPublishedStories(c.getCharacterId().toString(), teacherPrincipal);

        assertThat(usages).singleElement().satisfies(u -> {
            assertThat(u.contextId()).isEqualTo(ctx.getContextId().toString());
            assertThat(u.storyTitle()).isEqualTo("Hai Bà Trưng kể chuyện");
        });
    }
}
