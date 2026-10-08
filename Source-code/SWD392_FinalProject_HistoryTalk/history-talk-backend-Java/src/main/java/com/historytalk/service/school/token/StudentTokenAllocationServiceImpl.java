package com.historytalk.service.school.token;

import com.historytalk.dto.school.AllocateStudentTokenRequest;
import com.historytalk.dto.school.AllocateStudentTokenResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentTokenAllocationServiceImpl implements StudentTokenAllocationService {

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

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

        // 2. Kiểm tra quỹ Token khả dụng của trường
        int availableTokens = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        if (availableTokens < totalTokensNeeded) {
            throw new InvalidRequestException(String.format(
                    "Insufficient token quota: Requires %,d tokens for %d students, but the school only has %,d available tokens.",
                    totalTokensNeeded, studentCount, availableTokens));
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

        // 4. Trừ quỹ trường và cộng token cho học sinh
        school.setUnallocatedTokenQuota(availableTokens - totalTokensNeeded);
        schoolRepository.save(school);

        for (User student : students) {
            int currentToken = student.getToken() != null ? student.getToken() : 0;
            student.setToken(currentToken + tokenAmount);
        }
        userRepository.saveAll(students);

        log.info("Successfully allocated {} tokens ({}/student) to {} students of school {}. Remaining school quota: {}",
                totalTokensNeeded, tokenAmount, studentCount, school.getSchoolCode(), school.getUnallocatedTokenQuota());

        return AllocateStudentTokenResponse.builder()
                .schoolId(school.getId())
                .totalTokensAllocated(totalTokensNeeded)
                .affectedStudentCount(studentCount)
                .tokenAmountPerStudent(tokenAmount)
                .remainingSchoolTokens(school.getUnallocatedTokenQuota())
                .message(String.format("Allocated %,d tokens to %d students successfully", totalTokensNeeded, studentCount))
                .build();
    }
}
