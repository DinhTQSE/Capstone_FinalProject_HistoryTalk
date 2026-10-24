package com.historytalk.service.localcontent;

import com.historytalk.dto.localcontent.CreateLocalContextRequest;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.localcontent.ClassLocalContent;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.ClassLocalContentRepository;
import com.historytalk.repository.LocalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalContentClassAssignmentTest {

    @Mock
    private LocalContextRepository localContextRepo;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private ClassLocalContentRepository classLocalContentRepo;

    @Mock
    private ClassroomRepository classroomRepository;

    @InjectMocks
    private LocalContentServiceImpl localContentService;

    private School school;
    private User teacher;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        school = School.builder().id(UUID.randomUUID()).name("THPT A").build();
        teacher = User.builder().uid(UUID.randomUUID()).userName("teacher").role(UserRole.TEACHER).school(school).build();
        principal = new UserPrincipal(teacher);

        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        when(localContextRepo.save(any(HistoricalContext.class))).thenAnswer(inv -> {
            HistoricalContext ctx = inv.getArgument(0);
            if (ctx.getContextId() == null) ctx.setContextId(UUID.randomUUID());
            return ctx;
        });
    }

    private CreateLocalContextRequest request(List<String> classIds) {
        return CreateLocalContextRequest.builder()
                .name("Khoi nghia Hai Ba Trung")
                .description("Bối cảnh cho lớp 10A1")
                .contentScope(ContentScope.CLASS_ONLY)
                .classIds(classIds)
                .build();
    }

    @Test
    void createLocalContext_rejectsClassOfAnotherSchool() {
        UUID foreignClass = UUID.randomUUID();
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(foreignClass, school.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> localContentService.createLocalContext(request(List.of(foreignClass.toString())), principal))
                .isInstanceOf(InvalidRequestException.class);
        verify(classLocalContentRepo, never()).save(any());
        verify(classLocalContentRepo, never()).deleteByEntityIdAndEntityType(any(), any());
    }

    @Test
    void createLocalContext_rejectsMalformedClassId() {
        assertThatThrownBy(() -> localContentService.createLocalContext(request(List.of("abc")), principal))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createLocalContext_savesDistinctClassesOfOwnSchool() {
        UUID classId = UUID.randomUUID();
        when(classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classId, school.getId()))
                .thenReturn(Optional.of(Classroom.builder().id(classId).school(school).build()));

        localContentService.createLocalContext(request(List.of(classId.toString(), classId.toString())), principal);

        ArgumentCaptor<ClassLocalContent> saved = ArgumentCaptor.forClass(ClassLocalContent.class);
        verify(classLocalContentRepo, times(1)).save(saved.capture());
        assertThat(saved.getValue().getClassId()).isEqualTo(classId);
        assertThat(saved.getValue().getAssignedBy()).isEqualTo(teacher.getUid());
    }
}
