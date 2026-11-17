package com.historytalk.security;

import com.historytalk.entity.character.Character;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ContentAccessPolicyTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private HistoricalContextRepository contextRepository;
    @Mock
    private CharacterRepository characterRepository;

    private ContentAccessPolicy policy;

    private final School schoolA = School.builder().id(UUID.randomUUID()).name("A").schoolCode("A").build();
    private final School schoolB = School.builder().id(UUID.randomUUID()).name("B").schoolCode("B").build();
    private final User teacherA = User.builder().uid(UUID.randomUUID()).school(schoolA).build();
    private final User teacherA2 = User.builder().uid(UUID.randomUUID()).school(schoolA).build();
    private final User teacherB = User.builder().uid(UUID.randomUUID()).school(schoolB).build();
    private final User customer = User.builder().uid(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        policy = new ContentAccessPolicy(userRepository, contextRepository, characterRepository);
        for (User u : List.of(teacherA, teacherA2, teacherB, customer)) {
            lenient().when(userRepository.findById(u.getUid())).thenReturn(Optional.of(u));
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUid().toString(), null, List.of()));
    }

    private HistoricalContext localDraftOf(User author) {
        return HistoricalContext.builder().contextId(UUID.randomUUID()).school(author.getSchool())
                .isPublished(false).createdBy(author).build();
    }

    @Test
    void teacherAndSchoolAdminAreNotStaff() {
        assertThat(ContentAccessPolicy.isStaff("CONTENT_ADMIN")).isTrue();
        assertThat(ContentAccessPolicy.isStaff("ROLE_SYSTEM_ADMIN")).isTrue();
        assertThat(ContentAccessPolicy.isStaff("TEACHER")).isFalse();
        assertThat(ContentAccessPolicy.isStaff("SCHOOL_ADMIN")).isFalse();
        assertThat(ContentAccessPolicy.isStaff(null)).isFalse();
    }

    @Test
    void teacherOfAnotherSchoolCannotReadLocalDraftOrPublishedContent() {
        HistoricalContext draft = localDraftOf(teacherA);
        HistoricalContext published = localDraftOf(teacherA);
        published.setIsPublished(true);

        loginAs(teacherB);
        assertThat(policy.canView(draft, "TEACHER")).isFalse();
        assertThat(policy.canView(published, "TEACHER")).isFalse();
        assertThat(policy.canView(draft, "SCHOOL_ADMIN")).isFalse();
    }

    @Test
    void authorSeesOwnDraftButOtherTeacherOfSameSchoolOnlySeesPublished() {
        HistoricalContext draft = localDraftOf(teacherA);

        loginAs(teacherA);
        assertThat(policy.canView(draft, "TEACHER")).isTrue();

        loginAs(teacherA2);
        assertThat(policy.canView(draft, "TEACHER")).isFalse();
        draft.setIsPublished(true);
        assertThat(policy.canView(draft, "TEACHER")).isTrue();
    }

    @Test
    void globalContentNeedsPublishedAndNotDeleted() {
        HistoricalContext global = HistoricalContext.builder().contextId(UUID.randomUUID()).isPublished(true).build();
        loginAs(customer);
        assertThat(policy.canView(global, "CUSTOMER")).isTrue();

        global.setIsPublished(false);
        assertThat(policy.canView(global, "TEACHER")).isFalse();
        assertThat(policy.canView(global, "CONTENT_ADMIN")).isTrue();

        global.setIsPublished(true);
        global.setDeletedAt(LocalDateTime.now());
        assertThat(policy.canView(global, "CUSTOMER")).isFalse();
    }

    @Test
    void anonymousUserCannotReadLocalContent() {
        HistoricalContext published = localDraftOf(teacherA);
        published.setIsPublished(true);
        assertThat(policy.canView(published, "USER")).isFalse();
    }

    @Test
    void localCharacterFollowsSameRules() {
        Character draft = Character.builder().characterId(UUID.randomUUID()).school(schoolA)
                .isPublished(false).createdBy(teacherA).build();
        loginAs(teacherB);
        assertThat(policy.canView(draft, "TEACHER")).isFalse();
        loginAs(teacherA);
        assertThat(policy.canView(draft, "TEACHER")).isTrue();
    }

    @Test
    void documentFilterHidesDocumentsOfOtherSchoolsAndDeletedDocuments() {
        HistoricalContext localDraft = localDraftOf(teacherA);
        HistoricalContext global = HistoricalContext.builder().contextId(UUID.randomUUID()).isPublished(true).build();
        lenient().when(contextRepository.findById(localDraft.getContextId())).thenReturn(Optional.of(localDraft));
        lenient().when(contextRepository.findById(global.getContextId())).thenReturn(Optional.of(global));

        Document secret = Document.builder().docId(UUID.randomUUID()).entityType(EntityType.CONTEXT)
                .entityId(localDraft.getContextId()).build();
        Document open = Document.builder().docId(UUID.randomUUID()).entityType(EntityType.CONTEXT)
                .entityId(global.getContextId()).build();
        Document deleted = Document.builder().docId(UUID.randomUUID()).entityType(EntityType.CONTEXT)
                .entityId(global.getContextId()).deletedAt(LocalDateTime.now()).build();

        loginAs(teacherB);
        assertThat(List.of(secret, open, deleted).stream().filter(policy.documentFilter("TEACHER")).toList())
                .containsExactly(open);

        loginAs(customer);
        assertThat(policy.canView(secret, "CUSTOMER")).isFalse();
        assertThat(policy.canView(secret, "CONTENT_ADMIN")).isTrue();
    }
}
