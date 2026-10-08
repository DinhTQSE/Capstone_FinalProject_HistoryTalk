package com.historytalk.service.school;

import com.historytalk.dto.school.CreateSchoolAdminRequest;
import com.historytalk.dto.school.CreateSchoolRequest;
import com.historytalk.dto.school.SchoolAdminResponse;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolManagementServiceImpl implements SchoolManagementService {

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public SchoolResponse createSchool(CreateSchoolRequest request) {
        String normalizedCode = request.getSchoolCode().trim().toUpperCase();

        if (schoolRepository.existsBySchoolCodeIgnoreCase(normalizedCode)) {
            throw new DataConflictException("School code " + normalizedCode + " already exists in the system");
        }

        if (Boolean.FALSE.equals(request.getLocalHistoryPolicyAccepted())) {
            throw new InvalidRequestException("School must agree to the local history content terms and conditions");
        }

        int totalQuota = request.getPackageType().getTokens();

        School school = School.builder()
                .name(request.getName().trim())
                .schoolCode(normalizedCode)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .contactEmail(request.getContactEmail().trim().toLowerCase())
                .contactPhone(request.getContactPhone() != null ? request.getContactPhone().trim() : null)
                .packageType(request.getPackageType())
                .totalSchoolTokenQuota(totalQuota)
                .unallocatedTokenQuota(totalQuota)
                .localHistoryPolicyAccepted(true)
                .build();

        School savedSchool = schoolRepository.save(school);
        log.info("Created new school: {} ({}) with quota: {} tokens", savedSchool.getName(), savedSchool.getSchoolCode(), totalQuota);

        return mapToSchoolResponse(savedSchool);
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolResponse getSchoolById(UUID schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + schoolId));
        return mapToSchoolResponse(school);
    }

    @Override
    @Transactional
    public SchoolAdminResponse createSchoolAdmin(CreateSchoolAdminRequest request) {
        School school = schoolRepository.findById(request.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + request.getSchoolId()));

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DataConflictException("Email " + normalizedEmail + " is already in use by another account");
        }

        String baseUserName = school.getSchoolCode().toLowerCase() + "_admin";
        String userName = baseUserName;
        int counter = 1;
        while (userRepository.existsByUserNameIgnoreCase(userName)) {
            userName = baseUserName + "_" + counter++;
        }

        String rawPassword = generateRandomPassword(10);

        User schoolAdmin = User.builder()
                .userName(userName)
                .email(normalizedEmail)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(request.getFullName().trim())
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .role(UserRole.SCHOOL_ADMIN)
                .school(school)
                .mustChangePassword(true)
                .token(0)
                .build();

        User savedUser = userRepository.save(schoolAdmin);
        log.info("Created School Admin account: {} for school: {}", savedUser.getUserName(), school.getName());

        return SchoolAdminResponse.builder()
                .uid(savedUser.getUid())
                .userName(savedUser.getUserName())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .phoneNumber(savedUser.getPhoneNumber())
                .role(savedUser.getRole())
                .schoolId(school.getId())
                .schoolName(school.getName())
                .initialPassword(rawPassword)
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    private SchoolResponse mapToSchoolResponse(School school) {
        return SchoolResponse.builder()
                .id(school.getId())
                .name(school.getName())
                .schoolCode(school.getSchoolCode())
                .address(school.getAddress())
                .contactEmail(school.getContactEmail())
                .contactPhone(school.getContactPhone())
                .packageType(school.getPackageType())
                .totalSchoolTokenQuota(school.getTotalSchoolTokenQuota())
                .unallocatedTokenQuota(school.getUnallocatedTokenQuota())
                .localHistoryPolicyAccepted(school.getLocalHistoryPolicyAccepted())
                .status(school.getStatus())
                .createdAt(school.getCreatedAt())
                .build();
    }

    private String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder("Ht@");
        for (int i = 3; i < length; i++) {
            sb.append(CHAR_POOL.charAt(secureRandom.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }
}
