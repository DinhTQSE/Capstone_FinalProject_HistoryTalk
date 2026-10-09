package com.historytalk.service.school.student;

import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.dto.school.StudentImportRowDto;
import com.historytalk.entity.school.School;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service("SCHOOL_WIDE_IMPORT")
public class SchoolWideImportProcessor extends AbstractStudentImportProcessor {

    public SchoolWideImportProcessor(
            UserRepository userRepository,
            SchoolRepository schoolRepository,
            PasswordEncoder passwordEncoder) {
        super(userRepository, schoolRepository, passwordEncoder);
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
}
