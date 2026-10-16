package com.historytalk.service.school;

import com.historytalk.dto.school.CreateSchoolRequest;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.school.School;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolManagementServiceImplTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SchoolManagementServiceImpl schoolManagementService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(schoolManagementService, "packageSmallTokens", 5000000);
        ReflectionTestUtils.setField(schoolManagementService, "packageMediumTokens", 20000000);
        ReflectionTestUtils.setField(schoolManagementService, "packageLargeTokens", 50000000);
    }

    @Test
    void createSchool_shouldGenerateSchoolCodeWithNameAndLocationAcronym() {
        CreateSchoolRequest request = CreateSchoolRequest.builder()
                .name("Trường THPT Lê Hồng Phong")
                .address("280 An Dương Vương, Phường 4, Quận 5, TP. Hồ Chí Minh")
                .contactEmail("contact@lhp.edu.vn")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .localHistoryPolicyAccepted(true)
                .build();

        when(schoolRepository.existsBySchoolCodeIgnoreCase("LHP-HCM")).thenReturn(false);
        when(schoolRepository.save(any(School.class))).thenAnswer(invocation -> {
            School s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SchoolResponse response = schoolManagementService.createSchool(request);

        assertThat(response.getSchoolCode()).isEqualTo("LHP-HCM");
        ArgumentCaptor<School> captor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(captor.capture());
        assertThat(captor.getValue().getSchoolCode()).isEqualTo("LHP-HCM");
    }

    @Test
    void createSchool_shouldDifferentiateSameSchoolNameInDifferentProvince() {
        CreateSchoolRequest request = CreateSchoolRequest.builder()
                .name("Trường THPT Lê Hồng Phong")
                .address("Vị Xuyên, Thành phố Nam Định, Tỉnh Nam Định")
                .contactEmail("admin@lhpnd.edu.vn")
                .packageType(EnterprisePackage.ENTERPRISE_MEDIUM)
                .localHistoryPolicyAccepted(true)
                .build();

        when(schoolRepository.existsBySchoolCodeIgnoreCase("LHP-ND")).thenReturn(false);
        when(schoolRepository.save(any(School.class))).thenAnswer(invocation -> {
            School s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SchoolResponse response = schoolManagementService.createSchool(request);

        assertThat(response.getSchoolCode()).isEqualTo("LHP-ND");
    }

    @Test
    void createSchool_whenSameSchoolNameAndLocationAlreadyExists_shouldAppendSequenceWithoutBlocking() {
        CreateSchoolRequest secondSchoolRequest = CreateSchoolRequest.builder()
                .name("Trường THPT Lê Hồng Phong")
                .address("Quận 5, TP. Hồ Chí Minh")
                .contactEmail("admin2@lhp.edu.vn")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .localHistoryPolicyAccepted(true)
                .build();

        // LHP-HCM already exists, but LHP-HCM-01 does not
        when(schoolRepository.existsBySchoolCodeIgnoreCase("LHP-HCM")).thenReturn(true);
        when(schoolRepository.existsBySchoolCodeIgnoreCase("LHP-HCM-01")).thenReturn(false);
        when(schoolRepository.save(any(School.class))).thenAnswer(invocation -> {
            School s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SchoolResponse response = schoolManagementService.createSchool(secondSchoolRequest);

        assertThat(response.getSchoolCode()).isEqualTo("LHP-HCM-01");
    }

    @Test
    void createSchool_whenMultipleCollisionsExist_shouldIncrementSequence() {
        CreateSchoolRequest thirdSchoolRequest = CreateSchoolRequest.builder()
                .name("Trường THPT Lê Hồng Phong")
                .address("Quận 5, TP. Hồ Chí Minh")
                .contactEmail("admin3@lhp.edu.vn")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .localHistoryPolicyAccepted(true)
                .build();

        when(schoolRepository.existsBySchoolCodeIgnoreCase("LHP-HCM")).thenReturn(true);
        when(schoolRepository.existsBySchoolCodeIgnoreCase("LHP-HCM-01")).thenReturn(true);
        when(schoolRepository.existsBySchoolCodeIgnoreCase("LHP-HCM-02")).thenReturn(false);
        when(schoolRepository.save(any(School.class))).thenAnswer(invocation -> {
            School s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SchoolResponse response = schoolManagementService.createSchool(thirdSchoolRequest);

        assertThat(response.getSchoolCode()).isEqualTo("LHP-HCM-02");
    }

    @Test
    void getSchools_shouldReturnPaginatedSchools() {
        School school1 = School.builder()
                .id(UUID.randomUUID())
                .name("Trường THPT Lê Hồng Phong")
                .schoolCode("LHP-HCM")
                .address("TP. Hồ Chí Minh")
                .contactEmail("admin@lhp.edu.vn")
                .status(com.historytalk.entity.enums.SchoolStatus.ACTIVE)
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .totalSchoolTokenQuota(5000000)
                .unallocatedTokenQuota(5000000)
                .build();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<School> page = new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(school1), pageable, 1);

        when(schoolRepository.findByNameContainingIgnoreCaseOrSchoolCodeContainingIgnoreCaseOrContactEmailContainingIgnoreCase(
                "Lê Hồng Phong", "Lê Hồng Phong", "Lê Hồng Phong", pageable))
                .thenReturn(page);

        com.historytalk.dto.PaginatedResponse<SchoolResponse> result = schoolManagementService.getSchools(
                "Lê Hồng Phong", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSchoolCode()).isEqualTo("LHP-HCM");
        assertThat(result.getContent().get(0).getName()).isEqualTo("Trường THPT Lê Hồng Phong");
    }

    @Test
    void getSchoolTokenQuota_shouldCalculateAllocatedAndUsagePercentage() {
        UUID schoolId = UUID.randomUUID();
        School school = School.builder()
                .id(schoolId)
                .name("Trường THPT Lê Hồng Phong")
                .schoolCode("LHP-HCM")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .totalSchoolTokenQuota(5000000)
                .unallocatedTokenQuota(3500000)
                .build();

        when(schoolRepository.findById(schoolId)).thenReturn(java.util.Optional.of(school));

        com.historytalk.dto.school.SchoolTokenQuotaResponse response = schoolManagementService.getSchoolTokenQuota(schoolId);

        assertThat(response).isNotNull();
        assertThat(response.getSchoolId()).isEqualTo(schoolId);
        assertThat(response.getSchoolCode()).isEqualTo("LHP-HCM");
        assertThat(response.getTotalSchoolTokenQuota()).isEqualTo(5000000L);
        assertThat(response.getUnallocatedTokenQuota()).isEqualTo(3500000L);
        assertThat(response.getAllocatedTokenQuota()).isEqualTo(1500000L);
        assertThat(response.getUsagePercentage()).isEqualTo(30.0);
    }

    @Test
    void createSchool_shouldAllowDuplicateSchoolNamesInDifferentLocations() {
        CreateSchoolRequest quyNhonSchool = CreateSchoolRequest.builder()
                .name("Trường THPT Lê Hồng Phong")
                .address("Thành phố Quy Nhơn, Tỉnh Bình Định")
                .contactEmail("admin@lhpqn.edu.vn")
                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                .localHistoryPolicyAccepted(true)
                .build();

        when(schoolRepository.existsBySchoolCodeIgnoreCase("LHP-BDH")).thenReturn(false);
        when(schoolRepository.save(any(School.class))).thenAnswer(invocation -> {
            School s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SchoolResponse response = schoolManagementService.createSchool(quyNhonSchool);

        assertThat(response.getName()).isEqualTo("Trường THPT Lê Hồng Phong");
        assertThat(response.getSchoolCode()).isEqualTo("LHP-BDH");
    }

    @Test
    void getSchoolAdmins_shouldReturnPaginatedSchoolAdmins() {
        UUID schoolId = UUID.randomUUID();
        School school = School.builder().id(schoolId).name("THPT Lê Hồng Phong").build();
        com.historytalk.entity.user.User admin = com.historytalk.entity.user.User.builder()
                .uid(UUID.randomUUID())
                .userName("lhp_admin")
                .email("admin@lhp.edu.vn")
                .fullName("Nguyễn Văn Admin")
                .role(com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN)
                .school(school)
                .build();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<com.historytalk.entity.user.User> page = new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(admin), pageable, 1);

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(userRepository.searchSchoolUsersWithFilter(schoolId, com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN, true, "admin", pageable))
                .thenReturn(page);

        com.historytalk.dto.PaginatedResponse<com.historytalk.dto.school.SchoolAdminResponse> result =
                schoolManagementService.getSchoolAdmins(schoolId, "admin", true, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserName()).isEqualTo("lhp_admin");
        assertThat(result.getContent().get(0).getActive()).isTrue();
    }

    @Test
    void getSchoolAdmins_whenSearchIsNull_shouldPassEmptyStringToRepository() {
        UUID schoolId = UUID.randomUUID();
        com.historytalk.entity.school.School school = com.historytalk.entity.school.School.builder()
                .id(schoolId)
                .name("Trường THPT Lê Hồng Phong")
                .schoolCode("LHP-HCM")
                .build();

        com.historytalk.entity.user.User admin = com.historytalk.entity.user.User.builder()
                .uid(UUID.randomUUID())
                .userName("lhp_admin")
                .email("admin@lhp.edu.vn")
                .fullName("Nguyễn Văn Admin")
                .role(com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN)
                .school(school)
                .build();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<com.historytalk.entity.user.User> page = new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(admin), pageable, 1);

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(userRepository.searchSchoolUsersWithFilter(schoolId, com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN, null, "", pageable))
                .thenReturn(page);

        com.historytalk.dto.PaginatedResponse<com.historytalk.dto.school.SchoolAdminResponse> result =
                schoolManagementService.getSchoolAdmins(schoolId, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).searchSchoolUsersWithFilter(schoolId, com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN, null, "", pageable);
    }

    @Test
    void updateSchoolAdmin_shouldUpdateProfileSuccessfully() {
        UUID adminId = UUID.randomUUID();
        com.historytalk.entity.user.User admin = com.historytalk.entity.user.User.builder()
                .uid(adminId)
                .userName("lhp_admin")
                .email("old@lhp.edu.vn")
                .fullName("Old Name")
                .role(com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN)
                .build();

        com.historytalk.dto.school.UpdateSchoolAdminRequest request = com.historytalk.dto.school.UpdateSchoolAdminRequest.builder()
                .fullName("New Name")
                .email("new@lhp.edu.vn")
                .phoneNumber("0987654321")
                .build();

        when(userRepository.findById(adminId)).thenReturn(java.util.Optional.of(admin));
        when(userRepository.existsByEmailIgnoreCaseAndUidNot("new@lhp.edu.vn", adminId)).thenReturn(false);
        when(userRepository.save(any(com.historytalk.entity.user.User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.historytalk.dto.school.SchoolAdminResponse response = schoolManagementService.updateSchoolAdmin(adminId, request);

        assertThat(response.getFullName()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("new@lhp.edu.vn");
        assertThat(response.getPhoneNumber()).isEqualTo("0987654321");
    }

    @Test
    void updateSchoolAdminStatus_shouldToggleDeletedAt() {
        UUID adminId = UUID.randomUUID();
        com.historytalk.entity.user.User admin = com.historytalk.entity.user.User.builder()
                .uid(adminId)
                .userName("lhp_admin")
                .role(com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN)
                .deletedAt(null)
                .build();

        when(userRepository.findById(adminId)).thenReturn(java.util.Optional.of(admin));
        when(userRepository.save(any(com.historytalk.entity.user.User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.historytalk.dto.school.SchoolAdminResponse response = schoolManagementService.updateSchoolAdminStatus(adminId, false);

        assertThat(response.getActive()).isFalse();
        assertThat(admin.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteSchoolAdmin_shouldSoftDeleteUser() {
        UUID adminId = UUID.randomUUID();
        com.historytalk.entity.user.User admin = com.historytalk.entity.user.User.builder()
                .uid(adminId)
                .userName("lhp_admin")
                .role(com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN)
                .build();

        when(userRepository.findById(adminId)).thenReturn(java.util.Optional.of(admin));

        schoolManagementService.deleteSchoolAdmin(adminId);

        assertThat(admin.getDeletedAt()).isNotNull();
        verify(userRepository).save(admin);
    }

    @Test
    void resetSchoolAdminPassword_shouldGenerateNewPasswordAndRequireChange() {
        UUID adminId = UUID.randomUUID();
        com.historytalk.entity.user.User admin = com.historytalk.entity.user.User.builder()
                .uid(adminId)
                .userName("lhp_admin")
                .email("admin@lhp.edu.vn")
                .fullName("Nguyễn Văn Admin")
                .role(com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN)
                .mustChangePassword(false)
                .build();

        when(userRepository.findById(adminId)).thenReturn(java.util.Optional.of(admin));
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-new-pwd");

        com.historytalk.dto.school.ResetPasswordResponse response = schoolManagementService.resetSchoolAdminPassword(adminId);

        assertThat(response).isNotNull();
        assertThat(response.getInitialPassword()).startsWith("Ht@");
        assertThat(admin.getMustChangePassword()).isTrue();
        assertThat(admin.getPassword()).isEqualTo("encoded-new-pwd");
        verify(userRepository).save(admin);
    }
}

