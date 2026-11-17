package com.historytalk.service.localcontent;

import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.ForbiddenException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.LocalCharacterRepository;
import com.historytalk.repository.LocalContextRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** DS-02b: a teacher of School B opening School A's local content gets MSG11 (403), not a 401 or a bare 404. */
@ExtendWith(MockitoExtension.class)
class LocalContentCrossSchoolAccessTest {

    private static final String MSG11 = "Bạn không có quyền thực hiện thao tác này.";

    @Mock
    private LocalContextRepository localContextRepo;
    @Mock
    private LocalCharacterRepository localCharacterRepo;
    @Mock
    private SchoolRepository schoolRepository;

    @InjectMocks
    private LocalContentServiceImpl localContentService;

    private School schoolA;
    private School schoolB;
    private User authorA;
    private UserPrincipal teacherB;
    private UserPrincipal otherTeacherA;

    @BeforeEach
    void setUp() {
        schoolA = School.builder().id(UUID.randomUUID()).name("A").build();
        schoolB = School.builder().id(UUID.randomUUID()).name("B").build();
        authorA = User.builder().uid(UUID.randomUUID()).userName("authorA").role(UserRole.TEACHER).school(schoolA).build();
        teacherB = new UserPrincipal(User.builder().uid(UUID.randomUUID()).userName("teacherB")
                .role(UserRole.TEACHER).school(schoolB).build());
        otherTeacherA = new UserPrincipal(User.builder().uid(UUID.randomUUID()).userName("otherA")
                .role(UserRole.TEACHER).school(schoolA).build());
    }

    private HistoricalContext localContextOfA() {
        return HistoricalContext.builder().contextId(UUID.randomUUID()).name("Local A")
                .school(schoolA).createdBy(authorA).contentScope(ContentScope.CLASS_ONLY).build();
    }

    @Test
    void teacherOfOtherSchool_getsMsg11() {
        HistoricalContext ctx = localContextOfA();
        when(schoolRepository.findById(schoolB.getId())).thenReturn(Optional.of(schoolB));
        when(localContextRepo.findByIdAndSchool(ctx.getContextId(), schoolB.getId())).thenReturn(Optional.empty());
        when(localContextRepo.findById(ctx.getContextId())).thenReturn(Optional.of(ctx));

        assertThatThrownBy(() -> localContentService.getLocalContextById(ctx.getContextId().toString(), teacherB))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(MSG11);
    }

    @Test
    void missingContext_isNotFound() {
        UUID missing = UUID.randomUUID();
        when(schoolRepository.findById(schoolB.getId())).thenReturn(Optional.of(schoolB));
        when(localContextRepo.findByIdAndSchool(missing, schoolB.getId())).thenReturn(Optional.empty());
        when(localContextRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> localContentService.getLocalContextById(missing.toString(), teacherB))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy bối cảnh địa phương");
    }

    @Test
    void deletedContextOfOwnSchool_isNotFoundRatherThanForbidden() {
        HistoricalContext ctx = localContextOfA();
        ctx.setDeletedAt(LocalDateTime.now());
        when(schoolRepository.findById(schoolA.getId())).thenReturn(Optional.of(schoolA));
        when(localContextRepo.findByIdAndSchool(ctx.getContextId(), schoolA.getId())).thenReturn(Optional.empty());
        when(localContextRepo.findById(ctx.getContextId())).thenReturn(Optional.of(ctx));

        assertThatThrownBy(() -> localContentService.getLocalContextById(ctx.getContextId().toString(), otherTeacherA))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sameSchoolTeacher_whoIsNotTheAuthor_getsMsg11OnClassOnlyContent() {
        HistoricalContext ctx = localContextOfA();
        when(schoolRepository.findById(schoolA.getId())).thenReturn(Optional.of(schoolA));
        when(localContextRepo.findByIdAndSchool(ctx.getContextId(), schoolA.getId())).thenReturn(Optional.of(ctx));

        assertThatThrownBy(() -> localContentService.getLocalContextById(ctx.getContextId().toString(), otherTeacherA))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(MSG11);
    }
}
