package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.ResetPasswordResponse;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.dto.school.UpdateStudentRequest;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentManagementServiceImpl implements StudentManagementService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<StudentResponse> getStudents(UUID schoolId, String search, Boolean active, Pageable pageable) {
        if (!schoolRepository.existsById(schoolId)) {
            throw new ResourceNotFoundException("School not found with ID: " + schoolId);
        }

        String keyword = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        Page<User> studentPage = userRepository.searchSchoolUsersWithFilter(
                schoolId, UserRole.SCHOOL_STUDENT, active, keyword, pageable);

        List<StudentResponse> content = studentPage.getContent().stream()
                .map(this::mapToStudentResponse)
                .toList();

        return PaginatedResponse.<StudentResponse>builder()
                .content(content)
                .totalElements(studentPage.getTotalElements())
                .totalPages(studentPage.getTotalPages())
                .currentPage(studentPage.getNumber())
                .pageSize(studentPage.getSize())
                .hasNext(studentPage.hasNext())
                .hasPrevious(studentPage.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getStudentById(UUID studentId, UUID schoolId) {
        User student = findStudentInSchool(studentId, schoolId);
        return mapToStudentResponse(student);
    }

    @Override
    @Transactional
    public StudentResponse updateStudent(UUID studentId, UUID schoolId, UpdateStudentRequest request) {
        User student = findStudentInSchool(studentId, schoolId);

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(student.getEmail()) && userRepository.existsByEmailIgnoreCaseAndUidNot(newEmail, studentId)) {
                throw new DataConflictException("Email " + newEmail + " is already in use by another account");
            }
            student.setEmail(newEmail);
        }

        student.setFullName(request.getFullName().trim());
        if (request.getDob() != null) {
            student.setDob(request.getDob());
        }
        if (request.getGender() != null) {
            student.setGender(request.getGender());
        }
        student.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null);

        User saved = userRepository.save(student);
        log.info("Updated student profile for {}: {}", saved.getUserName(), saved.getFullName());
        return mapToStudentResponse(saved);
    }

    @Override
    @Transactional
    public StudentResponse updateStudentStatus(UUID studentId, UUID schoolId, boolean active) {
        User student = findStudentInSchool(studentId, schoolId);

        if (active) {
            student.setDeletedAt(null);
        } else {
            student.setDeletedAt(LocalDateTime.now());
        }

        User saved = userRepository.save(student);
        log.info("Updated student status for {}: active={}", saved.getUserName(), active);
        return mapToStudentResponse(saved);
    }

    @Override
    @Transactional
    public void deleteStudent(UUID studentId, UUID schoolId) {
        User student = findStudentInSchool(studentId, schoolId);
        student.setDeletedAt(LocalDateTime.now());
        userRepository.save(student);
        log.info("Soft-deleted student: {}", student.getUserName());
    }

    @Override
    @Transactional
    public ResetPasswordResponse resetStudentPassword(UUID studentId, UUID schoolId) {
        User student = findStudentInSchool(studentId, schoolId);

        String rawPassword;
        if (student.getStudentCode() != null && !student.getStudentCode().trim().isEmpty()) {
            rawPassword = student.getStudentCode().trim() + "@2026";
        } else {
            rawPassword = generateRandomPassword(10);
        }

        student.setPassword(passwordEncoder.encode(rawPassword));
        student.setMustChangePassword(true);
        userRepository.save(student);

        log.info("Reset password for student: {}", student.getUserName());
        return ResetPasswordResponse.builder()
                .uid(student.getUid())
                .userName(student.getUserName())
                .fullName(student.getFullName())
                .email(student.getEmail())
                .initialPassword(rawPassword)
                .message("Student password reset successfully. Default pattern is {studentCode}@2026.")
                .build();
    }

    private User findStudentInSchool(UUID studentId, UUID schoolId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        if (student.getSchool() == null || !student.getSchool().getId().equals(schoolId)) {
            throw new ResourceNotFoundException("Student does not belong to your school");
        }

        if (student.getRole() != UserRole.SCHOOL_STUDENT) {
            throw new ResourceNotFoundException("Account is not a student");
        }

        return student;
    }

    private StudentResponse mapToStudentResponse(User user) {
        return StudentResponse.builder()
                .uid(user.getUid())
                .studentCode(user.getStudentCode())
                .userName(user.getUserName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .dob(user.getDob())
                .gender(user.getGender())
                .phoneNumber(user.getPhoneNumber())
                .token(user.getToken())
                .active(user.getDeletedAt() == null)
                .schoolId(user.getSchool() != null ? user.getSchool().getId() : null)
                .schoolName(user.getSchool() != null ? user.getSchool().getName() : null)
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now())
                .updatedAt(user.getUpdatedAt())
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
