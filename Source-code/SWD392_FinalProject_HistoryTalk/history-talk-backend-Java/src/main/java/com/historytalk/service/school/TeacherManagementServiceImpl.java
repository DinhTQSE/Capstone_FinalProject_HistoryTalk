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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + schoolId));

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DataConflictException("Email " + normalizedEmail + " đã được sử dụng");
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
                .role(UserRole.TEACHER)
                .school(school)
                .mustChangePassword(true)
                .token(Math.min(school.getDefaultDailyTeacherQuota() != null ? school.getDefaultDailyTeacherQuota() : 10000,
                        school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 10000))
                .dailyTokensUsed(0)
                .dailyBonusTokens(0)
                .lastTokenResetAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        User saved = userRepository.save(teacher);
        log.info("Created new teacher: {} ({}) for school: {}", saved.getFullName(), saved.getUserName(), school.getName());

        TeacherResponse response = mapToTeacherResponse(saved);
        response.setInitialPassword(rawPassword);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<TeacherResponse> getTeachers(UUID schoolId, String search, Boolean active, Pageable pageable) {
        if (!schoolRepository.existsById(schoolId)) {
            throw new ResourceNotFoundException("Không tìm thấy trường học với ID: " + schoolId);
        }

        String keyword = (search != null) ? search.trim() : "";
        Page<User> teacherPage = userRepository.searchSchoolUsersWithFilter(
                schoolId, UserRole.TEACHER, active, keyword, pageable);

        List<TeacherResponse> content = teacherPage.getContent().stream()
                .map(this::mapToTeacherResponse)
                .toList();

        return PaginatedResponse.<TeacherResponse>builder()
                .content(content)
                .totalElements(teacherPage.getTotalElements())
                .totalPages(teacherPage.getTotalPages())
                .currentPage(teacherPage.getNumber() + 1)
                .pageSize(teacherPage.getSize())
                .hasNext(teacherPage.hasNext())
                .hasPrevious(teacherPage.hasPrevious())
                .build();
    }

    @Override
    @Transactional
    public TeacherResponse updateTeacher(UUID teacherId, UUID schoolId, UpdateTeacherRequest request) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo viên với ID: " + teacherId));

        if (teacher.getSchool() == null || !teacher.getSchool().getId().equals(schoolId)) {
            throw new ResourceNotFoundException("Giáo viên không thuộc trường của bạn");
        }

        if (teacher.getRole() != UserRole.TEACHER) {
            throw new ResourceNotFoundException("Tài khoản không phải là giáo viên");
        }

        String newEmail = request.getEmail().trim().toLowerCase();
        if (!newEmail.equalsIgnoreCase(teacher.getEmail()) && userRepository.existsByEmailIgnoreCaseAndUidNot(newEmail, teacherId)) {
            throw new DataConflictException("Email " + newEmail + " đã được sử dụng bởi một tài khoản khác");
        }

        teacher.setFullName(request.getFullName().trim());
        teacher.setEmail(newEmail);
        teacher.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null);

        User saved = userRepository.save(teacher);
        log.info("Updated teacher profile for {}: {}", saved.getUserName(), saved.getFullName());
        return mapToTeacherResponse(saved);
    }

    @Override
    @Transactional
    public void deleteTeacher(UUID teacherId, UUID schoolId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo viên với ID: " + teacherId));

        if (teacher.getSchool() == null || !teacher.getSchool().getId().equals(schoolId)) {
            throw new ResourceNotFoundException("Giáo viên không thuộc trường của bạn");
        }

        if (teacher.getRole() != UserRole.TEACHER) {
            throw new ResourceNotFoundException("Tài khoản không phải là giáo viên");
        }

        teacher.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        userRepository.save(teacher);
        log.info("Soft-deleted teacher: {}", teacher.getUserName());
    }

    @Override
    @Transactional
    public ResetPasswordResponse resetTeacherPassword(UUID teacherId, UUID schoolId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo viên với ID: " + teacherId));

        if (teacher.getSchool() == null || !teacher.getSchool().getId().equals(schoolId)) {
            throw new ResourceNotFoundException("Giáo viên không thuộc trường của bạn");
        }

        if (teacher.getRole() != UserRole.TEACHER) {
            throw new ResourceNotFoundException("Tài khoản không phải là giáo viên");
        }

        String rawPassword = generateRandomPassword(10);
        teacher.setPassword(passwordEncoder.encode(rawPassword));
        teacher.setMustChangePassword(true);
        userRepository.save(teacher);

        log.info("Reset password for teacher: {}", teacher.getUserName());
        return ResetPasswordResponse.builder()
                .uid(teacher.getUid())
                .userName(teacher.getUserName())
                .fullName(teacher.getFullName())
                .email(teacher.getEmail())
                .initialPassword(rawPassword)
                .message("Đặt lại mật khẩu giáo viên thành công. Vui lòng gửi mật khẩu tạm thời này cho giáo viên.")
                .build();
    }

    @Override
    @Transactional
    public TeacherResponse updateTeacherStatus(UUID teacherId, UUID schoolId, boolean active) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo viên với ID: " + teacherId));

        if (teacher.getSchool() == null || !teacher.getSchool().getId().equals(schoolId)) {
            throw new ResourceNotFoundException("Giáo viên không thuộc trường của bạn");
        }

        if (teacher.getRole() != UserRole.TEACHER) {
            throw new ResourceNotFoundException("Tài khoản không phải là giáo viên");
        }

        if (active) {
            teacher.setDeletedAt(null);
        } else {
            teacher.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
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
                .active(user.getDeletedAt() == null)
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt() : java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
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
