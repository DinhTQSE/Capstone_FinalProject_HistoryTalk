package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateTeacherRequest;
import com.historytalk.dto.school.TeacherResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
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
public class TeacherManagementServiceImpl implements TeacherManagementService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public TeacherResponse createTeacher(CreateTeacherRequest request, UUID schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + schoolId));

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DataConflictException("Email " + normalizedEmail + " is already in use");
        }

        String baseUserName = school.getSchoolCode().toLowerCase() + "_gv_";
        String userName;
        do {
            userName = baseUserName + generateRandomNumericString(4);
        } while (userRepository.existsByUserNameIgnoreCase(userName));

        String rawPassword = generateRandomPassword(10);

        User teacher = User.builder()
                .userName(userName)
                .email(normalizedEmail)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(request.getFullName().trim())
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .subjectDepartment(request.getSubjectDepartment().trim())
                .role(UserRole.TEACHER)
                .school(school)
                .mustChangePassword(true)
                .token(0)
                .build();

        User saved = userRepository.save(teacher);
        log.info("Created new teacher: {} ({}) for school: {}", saved.getFullName(), saved.getUserName(), school.getName());

        TeacherResponse response = mapToTeacherResponse(saved);
        response.setInitialPassword(rawPassword);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<TeacherResponse> getTeachers(UUID schoolId, String search, Pageable pageable) {
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        Page<User> teacherPage = userRepository.searchTeachers(schoolId, UserRole.TEACHER, cleanSearch, pageable);

        List<TeacherResponse> content = teacherPage.getContent().stream()
                .map(this::mapToTeacherResponse)
                .toList();

        return PaginatedResponse.<TeacherResponse>builder()
                .content(content)
                .totalElements(teacherPage.getTotalElements())
                .totalPages(teacherPage.getTotalPages())
                .currentPage(teacherPage.getNumber())
                .pageSize(teacherPage.getSize())
                .hasNext(teacherPage.hasNext())
                .hasPrevious(teacherPage.hasPrevious())
                .build();
    }

    @Override
    @Transactional
    public TeacherResponse updateTeacherStatus(UUID teacherId, UUID schoolId, boolean active) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with ID: " + teacherId));

        if (teacher.getSchool() == null || !teacher.getSchool().getId().equals(schoolId)) {
            throw new ResourceNotFoundException("Teacher does not belong to your school");
        }

        if (teacher.getRole() != UserRole.TEACHER) {
            throw new ResourceNotFoundException("Account is not a teacher");
        }

        if (active) {
            teacher.setDeletedAt(null);
        } else {
            teacher.setDeletedAt(LocalDateTime.now());
        }

        User updated = userRepository.save(teacher);
        log.info("Updated teacher status for {}: active={}", updated.getUserName(), active);
        return mapToTeacherResponse(updated);
    }

    private TeacherResponse mapToTeacherResponse(User user) {
        return TeacherResponse.builder()
                .uid(user.getUid())
                .userName(user.getUserName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .subjectDepartment(user.getSubjectDepartment())
                .active(user.getDeletedAt() == null)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder("Ht@");
        for (int i = 3; i < length; i++) {
            sb.append(CHAR_POOL.charAt(secureRandom.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    private String generateRandomNumericString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }
}
