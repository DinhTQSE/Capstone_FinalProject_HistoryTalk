package com.historytalk.service.localcontent;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.localcontent.LocalContentResponse;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.ClassLocalContentRepository;
import com.historytalk.repository.LocalCharacterRepository;
import com.historytalk.repository.LocalContextRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentLocalContentTest {

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

    @Mock
    private ClassStudentRepository classStudentRepository;

    @InjectMocks
    private LocalContentServiceImpl localContentService;

    private UUID schoolId;
    private UUID studentUid;
    private UUID classId;
    private UserPrincipal studentPrincipal;
    private School school;
    private User studentUser;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        studentUid = UUID.randomUUID();
        classId = UUID.randomUUID();

        school = School.builder().id(schoolId).name("THPT History").build();
        studentUser = User.builder()
                .uid(studentUid)
                .userName("student01")
                .fullName("Nguyen Van A")
                .role(com.historytalk.entity.enums.UserRole.SCHOOL_STUDENT)
                .school(school)
                .build();
        studentPrincipal = new UserPrincipal(studentUser);
    }

    @Test
    void getStudentPublishedLocalContexts_usesStudentClassrooms() {
        Classroom classroom = Classroom.builder().id(classId).className("10A1").build();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classStudentRepository.findActiveClassroomsByStudentUid(studentUid))
                .thenReturn(List.of(classroom));

        HistoricalContext ctx = HistoricalContext.builder()
                .contextId(UUID.randomUUID())
                .name("Khoi Nghia Hai Ba Trung")
                .school(school)
                .contentScope(ContentScope.CLASS_ONLY)
                .contentStatus(ContentStatus.ACTIVE)
                .isPublished(true)
                .createdBy(User.builder().uid(UUID.randomUUID()).userName("teacher").build())
                .build();

        when(localContextRepo.findVisibleToStudent(eq(schoolId), eq(true), eq(List.of(classId)), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ctx)));

        PaginatedResponse<LocalContentResponse> res =
                localContentService.getStudentPublishedLocalContexts("", null, 1, 10, studentPrincipal);

        assertThat(res.getContent()).hasSize(1);
        assertThat(res.getContent().get(0).getName()).isEqualTo("Khoi Nghia Hai Ba Trung");
    }

    @Test
    void getStudentPublishedLocalCharacters_usesStudentClassrooms() {
        Classroom classroom = Classroom.builder().id(classId).className("10A1").build();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(classStudentRepository.findActiveClassroomsByStudentUid(studentUid))
                .thenReturn(List.of(classroom));

        Character character = Character.builder()
                .characterId(UUID.randomUUID())
                .name("Hai Ba Trung")
                .school(school)
                .contentScope(ContentScope.CLASS_ONLY)
                .contentStatus(ContentStatus.ACTIVE)
                .isPublished(true)
                .createdBy(User.builder().uid(UUID.randomUUID()).userName("teacher").build())
                .build();

        when(localCharacterRepo.findVisibleToStudent(eq(schoolId), eq(true), eq(List.of(classId)), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(character)));

        PaginatedResponse<LocalContentResponse> res =
                localContentService.getStudentPublishedLocalCharacters("", null, 1, 10, studentPrincipal);

        assertThat(res.getContent()).hasSize(1);
        assertThat(res.getContent().get(0).getName()).isEqualTo("Hai Ba Trung");
    }

    @Test
    void getPublishedLocalContextById_throwsUnauthorizedWhenClassOnlyAndNotInClass() {
        UUID contextId = UUID.randomUUID();
        HistoricalContext ctx = HistoricalContext.builder()
                .contextId(contextId)
                .name("Private Context")
                .school(school)
                .contentScope(ContentScope.CLASS_ONLY)
                .contentStatus(ContentStatus.ACTIVE)
                .isPublished(true)
                .createdBy(User.builder().uid(UUID.randomUUID()).build())
                .build();

        when(localContextRepo.findByIdAndSchool(contextId, schoolId)).thenReturn(Optional.of(ctx));
        when(classLocalContentRepo.findClassIdsByEntityIdAndEntityType(eq(contextId), any()))
                .thenReturn(List.of(classId));
        // Student is in another class
        when(classStudentRepository.findActiveClassroomsByStudentUid(studentUid))
                .thenReturn(List.of(Classroom.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> localContentService.getPublishedLocalContextById(contextId.toString(), studentPrincipal))
                .isInstanceOf(UnauthorizedException.class);
    }
}
