package com.historytalk.service.localcontent;

import com.historytalk.entity.character.Character;
import com.historytalk.entity.document.DocumentMediaMetadata;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.enums.MediaType;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.ClassLocalContentRepository;
import com.historytalk.repository.DocumentMediaMetadataRepository;
import com.historytalk.repository.LocalCharacterRepository;
import com.historytalk.repository.LocalContextRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** School trash: a teacher only sees, restores and purges content they authored. */
@ExtendWith(MockitoExtension.class)
class LocalContentTrashTest {

    @Mock
    private LocalContextRepository localContextRepo;

    @Mock
    private LocalCharacterRepository localCharacterRepo;

    @Mock
    private ClassLocalContentRepository classLocalContentRepo;

    @Mock
    private DocumentMediaMetadataRepository mediaMetadataRepository;

    @InjectMocks
    private LocalContentServiceImpl localContentService;

    private School school;
    private User author;
    private UserPrincipal authorPrincipal;
    private UserPrincipal otherTeacherPrincipal;
    private HistoricalContext trashedContext;

    @BeforeEach
    void setUp() {
        school = School.builder().id(UUID.randomUUID()).name("THPT A").build();
        author = User.builder().uid(UUID.randomUUID()).userName("author").role(UserRole.TEACHER).school(school).build();
        User otherTeacher = User.builder().uid(UUID.randomUUID()).userName("other").role(UserRole.TEACHER).school(school).build();
        authorPrincipal = new UserPrincipal(author);
        otherTeacherPrincipal = new UserPrincipal(otherTeacher);
        trashedContext = HistoricalContext.builder()
                .contextId(UUID.randomUUID())
                .name("Khoi nghia Hai Ba Trung")
                .school(school)
                .createdBy(author)
                .deletedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void trashList_isScopedToCurrentTeacher() {
        when(localContextRepo.findTrashBySchoolAndCreator(eq(school.getId()), eq(author.getUid()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trashedContext)));

        var res = localContentService.getTrashedContexts(1, 10, authorPrincipal);

        assertThat(res.getContent()).hasSize(1);
        verify(localContextRepo, never()).findTrashBySchool(any(), any());
    }

    @Test
    void otherTeacher_cannotRestoreContext() {
        when(localContextRepo.findTrashedByIdAndSchool(trashedContext.getContextId(), school.getId()))
                .thenReturn(Optional.of(trashedContext));

        assertThatThrownBy(() -> localContentService.restoreLocalContext(
                trashedContext.getContextId().toString(), otherTeacherPrincipal))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(trashedContext.getDeletedAt()).isNotNull();
    }

    @Test
    void otherTeacher_cannotPermanentlyDeleteContext() {
        when(localContextRepo.findTrashedByIdAndSchool(trashedContext.getContextId(), school.getId()))
                .thenReturn(Optional.of(trashedContext));

        assertThatThrownBy(() -> localContentService.permanentDeleteLocalContext(
                trashedContext.getContextId().toString(), otherTeacherPrincipal))
                .isInstanceOf(UnauthorizedException.class);
        verify(localContextRepo, never()).delete(any(HistoricalContext.class));
    }

    @Test
    void permanentDelete_removesClassAssignmentsAndMediaRows() {
        DocumentMediaMetadata video = DocumentMediaMetadata.builder()
                .entityType(EntityType.CONTEXT)
                .entityId(trashedContext.getContextId())
                .mediaType(MediaType.VIDEO)
                .storagePath("CONTEXT/x/intro.mp4")
                .build();
        when(localContextRepo.findTrashedByIdAndSchool(trashedContext.getContextId(), school.getId()))
                .thenReturn(Optional.of(trashedContext));
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityId(EntityType.CONTEXT, trashedContext.getContextId()))
                .thenReturn(List.of(video));

        localContentService.permanentDeleteLocalContext(trashedContext.getContextId().toString(), authorPrincipal);

        verify(classLocalContentRepo).deleteByEntityIdAndEntityType(trashedContext.getContextId(), EntityType.CONTEXT);
        verify(mediaMetadataRepository).delete(video);
        verify(localContextRepo).delete(trashedContext);
    }

    @Test
    void otherTeacher_cannotRestoreCharacter() {
        Character trashedCharacter = Character.builder()
                .characterId(UUID.randomUUID())
                .name("Hai Ba Trung")
                .school(school)
                .createdBy(author)
                .deletedAt(LocalDateTime.now())
                .build();
        when(localCharacterRepo.findTrashedByIdAndSchool(trashedCharacter.getCharacterId(), school.getId()))
                .thenReturn(Optional.of(trashedCharacter));

        assertThatThrownBy(() -> localContentService.restoreLocalCharacter(
                trashedCharacter.getCharacterId().toString(), otherTeacherPrincipal))
                .isInstanceOf(UnauthorizedException.class);
    }
}
