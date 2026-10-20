package com.historytalk.service.school.token;

import com.historytalk.dto.school.AllocateStudentTokenRequest;
import com.historytalk.dto.school.AllocateStudentTokenResponse;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.school.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentTokenAllocationServiceImpl implements StudentTokenAllocationService {

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;

    @Override
    @Transactional
    public AllocateStudentTokenResponse allocateTokens(UUID schoolId, AllocateStudentTokenRequest request) {
        if (schoolId == null) {
            throw new InvalidRequestException("School ID must not be null");
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + schoolId));

        // 1. Thu thập danh sách ID học sinh
        Set<UUID> targetStudentIds = new LinkedHashSet<>();
        if (request.getStudentId() != null) {
            targetStudentIds.add(request.getStudentId());
        }
        if (request.getStudentIds() != null && !request.getStudentIds().isEmpty()) {
            targetStudentIds.addAll(request.getStudentIds());
        }

        if (targetStudentIds.isEmpty()) {
            throw new InvalidRequestException("Please provide studentId or studentIds to allocate tokens");
        }

        int tokenAmount = request.getTokenAmount();
        int studentCount = targetStudentIds.size();
        int totalTokensNeeded = studentCount * tokenAmount;

        // 2. Kiểm tra quỹ Token khả dụng của trường (chỉ kiểm tra đủ sức chứa, KHÔNG trừ trước)
        int availableTokens = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        if (availableTokens < tokenAmount) {
            throw new InvalidRequestException(String.format(
                    "Insufficient token quota: School shared pool only has %,d available tokens.",
                    availableTokens));
        }

        // 3. Truy vấn và xác thực học sinh
        List<User> students = userRepository.findAllById(targetStudentIds);
        if (students.size() != targetStudentIds.size()) {
            throw new ResourceNotFoundException("One or more student IDs could not be found");
        }

        for (User student : students) {
            if (student.getRole() != UserRole.SCHOOL_STUDENT) {
                throw new InvalidRequestException(String.format(
                        "User %s is not a student (Role: %s)", student.getUserName(), student.getRole()));
            }
            if (student.getSchool() == null || !schoolId.equals(student.getSchool().getId())) {
                throw new InvalidRequestException(String.format(
                        "Student %s does not belong to school %s", student.getUserName(), school.getSchoolCode()));
            }
        }

        // 4. Pure Shared Pool: Không trừ trước quỹ trường (school.unallocatedTokenQuota giữ nguyên).
        // Tăng dailyBonusTokens của học sinh để nâng trần sử dụng trong ngày; chỉ trừ quỹ trường khi học sinh thực sự chat AI.
        LocalDate today = LocalDate.now();
        int defaultDailyQuota = (school.getDefaultDailyStudentQuota() != null && school.getDefaultDailyStudentQuota() > 0)
                ? school.getDefaultDailyStudentQuota()
                : 1000;

        for (User student : students) {
            boolean isNewDay = student.getLastTokenResetAt() == null
                    || student.getLastTokenResetAt().toLocalDate().isBefore(today);
            if (isNewDay) {
                student.setDailyTokensUsed(0);
                student.setDailyBonusTokens(0);
                student.setLastTokenResetAt(LocalDateTime.now());
            }

            int currentBonus = student.getDailyBonusTokens() != null ? Math.max(0, student.getDailyBonusTokens()) : 0;
            int updatedBonus = currentBonus + tokenAmount;
            student.setDailyBonusTokens(updatedBonus);

            int baseLimit = defaultDailyQuota;
            if (classStudentRepository != null && student.getUid() != null) {
                List<Classroom> activeClasses = classStudentRepository.findActiveClassroomsByStudentUid(student.getUid());
                for (Classroom c : activeClasses) {
                    if (c.getDailyTokenQuota() != null && c.getDailyTokenQuota() > 0) {
                        baseLimit = c.getDailyTokenQuota();
                        break;
                    }
                }
            }
            int usedToday = student.getDailyTokensUsed() != null ? Math.max(0, student.getDailyTokensUsed()) : 0;
            int remainingDaily = Math.max(0, (baseLimit + updatedBonus) - usedToday);
            student.setToken(Math.min(remainingDaily, availableTokens));
        }
        userRepository.saveAll(students);

        log.info("Successfully granted {} daily bonus tokens ({}/student) to {} students of school {}. Shared pool remains: {}",
                totalTokensNeeded, tokenAmount, studentCount, school.getSchoolCode(), availableTokens);

        return AllocateStudentTokenResponse.builder()
                .schoolId(school.getId())
                .totalTokensAllocated(totalTokensNeeded)
                .affectedStudentCount(studentCount)
                .tokenAmountPerStudent(tokenAmount)
                .remainingSchoolTokens(availableTokens)
                .message(String.format("Granted %,d daily bonus tokens to %d students successfully (Pure Shared Pool)", totalTokensNeeded, studentCount))
                .build();
    }
}
