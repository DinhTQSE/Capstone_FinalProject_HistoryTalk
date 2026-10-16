package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.ResetPasswordResponse;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.dto.school.UpdateStudentRequest;
import com.historytalk.entity.enums.Gender;
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

import java.time.LocalDate;
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
class StudentManagementServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StudentManagementServiceImpl studentManagementService;

    private UUID schoolId;
    private School school;
    private UUID studentId;
    private User student;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        school = School.builder()
                .id(schoolId)
                .name("THPT Lê Hồng Phong")
                .schoolCode("LHP-HCM")
                .build();

        studentId = UUID.randomUUID();
        student = User.builder()
                .uid(studentId)
                .studentCode("HS001")
                .userName("lhp_hcm_hs001")
                .fullName("Nguyễn Văn Học Sinh")
                .email("hs001@lhp.edu.vn")
                .dob(LocalDate.of(2008, 5, 20))
                .gender(Gender.MALE)
                .role(UserRole.SCHOOL_STUDENT)
                .school(school)
                .token(10000)
                .mustChangePassword(true)
                .build();
    }

    @Test
    void getStudents_shouldReturnPaginatedStudents() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(student), pageable, 1);

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(userRepository.searchSchoolUsersWithFilter(schoolId, UserRole.SCHOOL_STUDENT, true, "HS001", pageable))
                .thenReturn(page);

        PaginatedResponse<StudentResponse> result = studentManagementService.getStudents(
                schoolId, "HS001", true, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStudentCode()).isEqualTo("HS001");
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("Nguyễn Văn Học Sinh");
    }

    @Test
    void getStudents_whenSearchIsNull_shouldPassEmptyStringToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(student), pageable, 1);

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(userRepository.searchSchoolUsersWithFilter(schoolId, UserRole.SCHOOL_STUDENT, null, "", pageable))
                .thenReturn(page);

        PaginatedResponse<StudentResponse> result = studentManagementService.getStudents(
                schoolId, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).searchSchoolUsersWithFilter(schoolId, UserRole.SCHOOL_STUDENT, null, "", pageable);
    }

    @Test
    void getStudentById_shouldReturnStudentResponse() {
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

        StudentResponse response = studentManagementService.getStudentById(studentId, schoolId);

        assertThat(response).isNotNull();
        assertThat(response.getUid()).isEqualTo(studentId);
        assertThat(response.getStudentCode()).isEqualTo("HS001");
        assertThat(response.getToken()).isEqualTo(10000);
    }

    @Test
    void getStudentById_whenBelongsToOtherSchool_shouldThrowException() {
        UUID otherSchoolId = UUID.randomUUID();
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentManagementService.getStudentById(studentId, otherSchoolId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Student does not belong to your school");
    }

    @Test
    void updateStudent_shouldUpdateProfileSuccessfully() {
        UpdateStudentRequest request = UpdateStudentRequest.builder()
                .fullName("Nguyễn Văn Cập Nhật")
                .email("hs001_new@lhp.edu.vn")
                .dob(LocalDate.of(2008, 6, 15))
                .gender(Gender.FEMALE)
                .phoneNumber("0911222333")
                .build();

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(userRepository.existsByEmailIgnoreCaseAndUidNot("hs001_new@lhp.edu.vn", studentId)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentResponse response = studentManagementService.updateStudent(studentId, schoolId, request);

        assertThat(response.getFullName()).isEqualTo("Nguyễn Văn Cập Nhật");
        assertThat(response.getEmail()).isEqualTo("hs001_new@lhp.edu.vn");
        assertThat(response.getDob()).isEqualTo(LocalDate.of(2008, 6, 15));
        assertThat(response.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(response.getPhoneNumber()).isEqualTo("0911222333");
    }

    @Test
    void updateStudent_whenEmailTaken_shouldThrowDataConflictException() {
        UpdateStudentRequest request = UpdateStudentRequest.builder()
                .fullName("Nguyễn Văn Cập Nhật")
                .email("taken@lhp.edu.vn")
                .build();

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(userRepository.existsByEmailIgnoreCaseAndUidNot("taken@lhp.edu.vn", studentId)).thenReturn(true);

        assertThatThrownBy(() -> studentManagementService.updateStudent(studentId, schoolId, request))
                .isInstanceOf(DataConflictException.class);
    }

    @Test
    void updateStudentStatus_shouldToggleActive() {
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentResponse response = studentManagementService.updateStudentStatus(studentId, schoolId, false);

        assertThat(response.getActive()).isFalse();
        assertThat(student.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteStudent_shouldSoftDelete() {
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

        studentManagementService.deleteStudent(studentId, schoolId);

        assertThat(student.getDeletedAt()).isNotNull();
        verify(userRepository).save(student);
    }

    @Test
    void resetStudentPassword_shouldResetToDefaultCodePattern() {
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(passwordEncoder.encode("HS001@2026")).thenReturn("encoded-hs-pwd");

        ResetPasswordResponse response = studentManagementService.resetStudentPassword(studentId, schoolId);

        assertThat(response).isNotNull();
        assertThat(response.getInitialPassword()).isEqualTo("HS001@2026");
        assertThat(student.getMustChangePassword()).isTrue();
        assertThat(student.getPassword()).isEqualTo("encoded-hs-pwd");
        verify(userRepository).save(student);
    }
}
