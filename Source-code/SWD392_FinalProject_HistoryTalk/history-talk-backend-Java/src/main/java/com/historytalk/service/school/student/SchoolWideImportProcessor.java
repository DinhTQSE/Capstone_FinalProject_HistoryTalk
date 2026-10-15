package com.historytalk.service.school.student;

import com.historytalk.dto.school.ImportedStudentAccountDto;
import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.dto.school.StudentImportRowDto;
import com.historytalk.entity.classroom.ClassStudent;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.entity.enums.ImportScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SchoolWideImportProcessor extends AbstractStudentImportProcessor {

    private final ClassroomRepository classroomRepository;
    private final ClassStudentRepository classStudentRepository;

    @Override
    public ImportScope getScope() {
        return ImportScope.SCHOOL_WIDE;
    }

    public SchoolWideImportProcessor(
            UserRepository userRepository,
            SchoolRepository schoolRepository,
            PasswordEncoder passwordEncoder,
            ClassroomRepository classroomRepository,
            ClassStudentRepository classStudentRepository) {
        super(userRepository, schoolRepository, passwordEncoder);
        this.classroomRepository = classroomRepository;
        this.classStudentRepository = classStudentRepository;
    }

    @Override
    protected void verifyAndDeductQuota(
            List<StudentImportRowDto> rows,
            StudentImportContext context,
            School school) {

        int baseToken = context.getDefaultInitialToken() != null ? context.getDefaultInitialToken() : 10000;

        int totalTokensNeeded = rows.stream()
                .mapToInt(r -> baseToken + (r.getAdditionalToken() != null ? Math.max(0, r.getAdditionalToken()) : 0))
                .sum();

        int availableTokens = school.getUnallocatedTokenQuota();
        if (availableTokens < totalTokensNeeded) {
            throw new InvalidRequestException(String.format(
                    "Insufficient unallocated token quota: Import requires %,d tokens but the school only has %,d available tokens.",
                    totalTokensNeeded, availableTokens));
        }

        school.setUnallocatedTokenQuota(availableTokens - totalTokensNeeded);
        schoolRepository.save(school);
        log.info("Deducted %,d tokens from school {} quota. Remaining quota: %,d tokens",
                totalTokensNeeded, school.getSchoolCode(), school.getUnallocatedTokenQuota());
    }

    @Override
    protected void persistAccounts(
            List<StudentImportRowDto> validRows,
            StudentImportContext context,
            School school,
            StudentImportResultDto resultDto) {
        super.persistAccounts(validRows, context, school, resultDto);

        // Auto-enroll students into classroom if class_code is present
        List<ImportedStudentAccountDto> successfulAccounts = resultDto.getSuccessfulAccounts();
        for (int i = 0; i < validRows.size() && i < successfulAccounts.size(); i++) {
            StudentImportRowDto row = validRows.get(i);
            ImportedStudentAccountDto acc = successfulAccounts.get(i);

            if (row.getClassCode() != null && !row.getClassCode().trim().isEmpty()) {
                String classCode = row.getClassCode().trim();
                classroomRepository.findByClassCodeIgnoreCaseAndDeletedAtIsNull(classCode)
                        .filter(c -> c.getSchool().getId().equals(school.getId()))
                        .ifPresent(classroom -> {
                            User student = userRepository.findById(acc.getUid()).orElse(null);
                            if (student != null && !classStudentRepository.existsByClassroomIdAndStudentUid(classroom.getId(), student.getUid())) {
                                classStudentRepository.save(ClassStudent.builder()
                                        .classroom(classroom)
                                        .student(student)
                                        .status("ACTIVE")
                                        .build());
                                log.info("Auto-enrolled student {} ({}) into classroom {}",
                                        student.getFullName(), student.getStudentCode(), classroom.getClassCode());
                            }
                        });
            }
        }
    }
}
