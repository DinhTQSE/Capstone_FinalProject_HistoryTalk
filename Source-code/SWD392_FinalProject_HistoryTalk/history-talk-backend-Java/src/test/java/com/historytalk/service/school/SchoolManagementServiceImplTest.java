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
}

