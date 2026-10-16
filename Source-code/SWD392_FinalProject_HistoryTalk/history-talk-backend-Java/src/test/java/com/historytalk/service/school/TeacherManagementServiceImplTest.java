package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateTeacherRequest;
import com.historytalk.dto.school.ResetPasswordResponse;
import com.historytalk.dto.school.TeacherResponse;
import com.historytalk.dto.school.UpdateTeacherRequest;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherManagementServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TeacherManagementServiceImpl teacherManagementService;

    private UUID schoolId;
    private School school;
    private UUID teacherId;
    private User teacher;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        school = School.builder()
                .id(schoolId)
                .name("THPT Lê Hồng Phong")
                .schoolCode("LHP-HCM")
                .build();

        teacherId = UUID.randomUUID();
        teacher = User.builder()
                .uid(teacherId)
                .userName("lhp_hcm_gv_1234")
                .fullName("Trần Thị Giáo Viên")
                .email("teacher@lhp.edu.vn")
                .role(UserRole.TEACHER)
                .school(school)
                .mustChangePassword(true)
                .build();
    }

    @Test
    void createTeacher_shouldCreateTeacherSuccessfully() {
        CreateTeacherRequest request = CreateTeacherRequest.builder()
                .fullName("Trần Thị Giáo Viên")
                .email("teacher@lhp.edu.vn")
                .phoneNumber("0912345678")
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(userRepository.existsByEmailIgnoreCase("teacher@lhp.edu.vn")).thenReturn(false);
        when(userRepository.existsByUserNameIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeacherResponse response = teacherManagementService.createTeacher(request, schoolId);

        assertThat(response).isNotNull();
        assertThat(response.getFullName()).isEqualTo("Trần Thị Giáo Viên");
        assertThat(response.getInitialPassword()).startsWith("Ht@");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void getTeachers_shouldReturnPaginatedTeachers() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(teacher), pageable, 1);

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(userRepository.searchSchoolUsersWithFilter(schoolId, UserRole.TEACHER, true, "Giáo Viên", pageable))
                .thenReturn(page);

        PaginatedResponse<TeacherResponse> result = teacherManagementService.getTeachers(
                schoolId, "Giáo Viên", true, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("Trần Thị Giáo Viên");
    }

    @Test
    void getTeachers_whenSearchIsNull_shouldPassEmptyStringToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(teacher), pageable, 1);

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(userRepository.searchSchoolUsersWithFilter(schoolId, UserRole.TEACHER, null, "", pageable))
                .thenReturn(page);

        PaginatedResponse<TeacherResponse> result = teacherManagementService.getTeachers(
                schoolId, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).searchSchoolUsersWithFilter(schoolId, UserRole.TEACHER, null, "", pageable);
    }

    @Test
    void updateTeacher_shouldUpdateProfileSuccessfully() {
        UpdateTeacherRequest request = UpdateTeacherRequest.builder()
                .fullName("Trần Thị Giáo Viên Mới")
                .email("newteacher@lhp.edu.vn")
                .phoneNumber("0999888777")
                .build();

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(userRepository.existsByEmailIgnoreCaseAndUidNot("newteacher@lhp.edu.vn", teacherId)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeacherResponse response = teacherManagementService.updateTeacher(teacherId, schoolId, request);

        assertThat(response.getFullName()).isEqualTo("Trần Thị Giáo Viên Mới");
        assertThat(response.getEmail()).isEqualTo("newteacher@lhp.edu.vn");
        assertThat(response.getPhoneNumber()).isEqualTo("0999888777");
    }

    @Test
    void updateTeacher_whenEmailTaken_shouldThrowDataConflictException() {
        UpdateTeacherRequest request = UpdateTeacherRequest.builder()
                .fullName("Trần Thị Giáo Viên Mới")
                .email("taken@lhp.edu.vn")
                .build();

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(userRepository.existsByEmailIgnoreCaseAndUidNot("taken@lhp.edu.vn", teacherId)).thenReturn(true);

        assertThatThrownBy(() -> teacherManagementService.updateTeacher(teacherId, schoolId, request))
                .isInstanceOf(DataConflictException.class);
    }

    @Test
    void updateTeacher_whenBelongsToDifferentSchool_shouldThrowException() {
        UUID otherSchoolId = UUID.randomUUID();
        UpdateTeacherRequest request = UpdateTeacherRequest.builder()
                .fullName("Trần Thị Giáo Viên Mới")
                .email("newteacher@lhp.edu.vn")
                .build();

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> teacherManagementService.updateTeacher(teacherId, otherSchoolId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Teacher does not belong to your school");
    }

    @Test
    void updateTeacherStatus_shouldToggleActive() {
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeacherResponse response = teacherManagementService.updateTeacherStatus(teacherId, schoolId, false);

        assertThat(response.getActive()).isFalse();
        assertThat(teacher.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteTeacher_shouldSoftDelete() {
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

        teacherManagementService.deleteTeacher(teacherId, schoolId);

        assertThat(teacher.getDeletedAt()).isNotNull();
        verify(userRepository).save(teacher);
    }

    @Test
    void resetTeacherPassword_shouldGeneratePasswordAndRequireChange() {
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-new-pwd");

        ResetPasswordResponse response = teacherManagementService.resetTeacherPassword(teacherId, schoolId);

        assertThat(response).isNotNull();
        assertThat(response.getInitialPassword()).startsWith("Ht@");
        assertThat(teacher.getMustChangePassword()).isTrue();
        assertThat(teacher.getPassword()).isEqualTo("encoded-new-pwd");
        verify(userRepository).save(teacher);
    }
}
