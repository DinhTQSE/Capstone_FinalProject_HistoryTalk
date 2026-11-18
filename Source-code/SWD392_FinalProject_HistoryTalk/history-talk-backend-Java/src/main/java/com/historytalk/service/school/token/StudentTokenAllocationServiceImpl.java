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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
            throw new InvalidRequestException("ID trường học không được để trống");
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + schoolId));

        // 1. Thu thập danh sách ID học sinh
        Set<UUID> targetStudentIds = new LinkedHashSet<>();
        if (request.getStudentId() != null) {
            targetStudentIds.add(request.getStudentId());
        }
        if (request.getStudentIds() != null && !request.getStudentIds().isEmpty()) {
            targetStudentIds.addAll(request.getStudentIds());
        }

        if (targetStudentIds.isEmpty()) {
            throw new InvalidRequestException("Vui lòng cung cấp studentId hoặc studentIds để cấp token");
        }

        int tokenAmount = request.getTokenAmount();
        int studentCount = targetStudentIds.size();
        int totalTokensNeeded = studentCount * tokenAmount;

        // 2. Kiểm tra quỹ Token khả dụng của trường (chỉ kiểm tra đủ sức chứa, KHÔNG trừ trước)
        int availableTokens = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        if (availableTokens < tokenAmount) {
            throw new InvalidRequestException(String.format(
                    "Không đủ hạn mức token: quỹ chung của trường chỉ còn %,d token.",
                    availableTokens));
        }

        // 3. Truy vấn và xác thực học sinh
        List<User> students = userRepository.findAllById(targetStudentIds);
        if (students.size() != targetStudentIds.size()) {
            throw new ResourceNotFoundException("Không tìm thấy một hoặc nhiều ID học sinh");
        }

        for (User student : students) {
            if (student.getRole() != UserRole.SCHOOL_STUDENT) {
                throw new InvalidRequestException(String.format(
                        "Người dùng %s không phải là học sinh (Vai trò: %s)", student.getUserName(), student.getRole()));
            }
            if (student.getSchool() == null || !schoolId.equals(student.getSchool().getId())) {
                throw new InvalidRequestException(String.format(
                        "Học sinh %s không thuộc trường %s", student.getUserName(), school.getSchoolCode()));
            }
        }

        // 4. Pure Shared Pool: Không trừ trước quỹ trường (school.unallocatedTokenQuota giữ nguyên).
        // Tăng dailyBonusTokens của học sinh để nâng trần sử dụng trong ngày; chỉ trừ quỹ trường khi học sinh thực sự chat AI.
        int defaultDailyQuota = (school.getDefaultDailyStudentQuota() != null && school.getDefaultDailyStudentQuota() > 0)
                ? school.getDefaultDailyStudentQuota()
                : 1000;

        for (User student : students) {
            boolean isNewDay = TokenDay.isNewDay(student.getLastTokenResetAt());
            if (isNewDay) {
                student.setDailyTokensUsed(0);
                student.setDailyBonusTokens(0);
                student.setLastTokenResetAt(LocalDateTime.now(ZoneOffset.UTC));
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
                .message(String.format("Đã cấp %,d token thưởng hằng ngày cho %d học sinh (từ quỹ chung)", totalTokensNeeded, studentCount))
                .build();
    }
}
