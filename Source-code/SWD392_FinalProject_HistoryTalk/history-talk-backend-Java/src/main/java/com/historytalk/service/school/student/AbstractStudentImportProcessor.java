package com.historytalk.service.school.student;

import com.historytalk.dto.school.ImportedStudentAccountDto;
import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.dto.school.StudentImportRowDto;
import com.historytalk.entity.enums.Gender;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractStudentImportProcessor implements StudentImportProcessor {

    protected final UserRepository userRepository;
    protected final SchoolRepository schoolRepository;
    protected final PasswordEncoder passwordEncoder;

    @Value("${historytalk.saas.student.shadow-email-domain:saas.historytalk.vn}")
    protected String shadowEmailDomain = "saas.historytalk.vn";

    private static final Pattern STUDENT_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,30}$");
    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public StudentImportResultDto processImport(MultipartFile file, StudentImportContext context) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Upload file must not be empty");
        }

        School school = schoolRepository.findById(context.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + context.getSchoolId()));

        StudentImportResultDto resultDto = StudentImportResultDto.builder()
                .errors(new ArrayList<>())
                .successfulAccounts(new ArrayList<>())
                .build();

        // Step 1: Parse Records from file
        List<StudentImportRowDto> rawRows = parseRecords(file);
        resultDto.setTotalRows(rawRows.size());

        if (rawRows.isEmpty()) {
            throw new InvalidRequestException("The file contains no data rows");
        }

        // Step 2: Validate each row
        List<StudentImportRowDto> validRows = validateRows(rawRows, context, resultDto);

        // Step 3: Polymorphic - Verify & deduct Token Quota
        verifyAndDeductQuota(validRows, context, school);

        // Step 4: Persist Accounts & Generate Passwords
        persistAccounts(validRows, context, school, resultDto);

        resultDto.setSuccessCount(resultDto.getSuccessfulAccounts().size());
        resultDto.setFailureCount(resultDto.getErrors().size());
        resultDto.setRemainingSchoolTokens(school.getUnallocatedTokenQuota());

        log.info("Finished student import for school {}: {} succeeded, {} failed",
                school.getSchoolCode(), resultDto.getSuccessCount(), resultDto.getFailureCount());

        return resultDto;
    }

    protected List<StudentImportRowDto> parseRecords(MultipartFile file) {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!originalName.endsWith(".csv")) {
            throw new InvalidRequestException("Unsupported file format. Please upload a valid .csv file");
        }

        List<StudentImportRowDto> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            int rowNumber = 1;
            for (CSVRecord record : parser) {
                rowNumber++;
                String studentCode = getRecordValue(record, "student_code");
                String fullName = getRecordValue(record, "full_name");
                String email = getRecordValue(record, "email");
                String dobStr = getRecordValue(record, "dob");
                String gender = getRecordValue(record, "gender");
                String phoneNumber = getRecordValue(record, "phone_number");
                String password = getRecordValue(record, "password");
                String additionalTokenStr = getRecordValue(record, "additional_token");

                LocalDate dob = null;
                if (dobStr != null && !dobStr.isEmpty()) {
                    try {
                        dob = LocalDate.parse(dobStr);
                    } catch (DateTimeParseException ignored) {
                        // Sẽ validate ở bước sau
                    }
                }

                Integer additionalToken = 0;
                if (additionalTokenStr != null && !additionalTokenStr.isEmpty()) {
                    try {
                        additionalToken = Integer.parseInt(additionalTokenStr);
                    } catch (NumberFormatException ignored) {}
                }

                rows.add(StudentImportRowDto.builder()
                        .rowNumber(rowNumber)
                        .studentCode(studentCode)
                        .fullName(fullName)
                        .email(email)
                        .dob(dob)
                        .gender(gender)
                        .phoneNumber(phoneNumber)
                        .password(password)
                        .additionalToken(additionalToken)
                        .build());
            }
        } catch (Exception e) {
            log.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new InvalidRequestException("Could not read CSV file. Please verify UTF-8 encoding and file format");
        }

        return rows;
    }

    protected List<StudentImportRowDto> validateRows(
            List<StudentImportRowDto> rows,
            StudentImportContext context,
            StudentImportResultDto resultDto) {

        List<StudentImportRowDto> validRows = new ArrayList<>();
        Set<String> seenStudentCodes = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();

        for (StudentImportRowDto row : rows) {
            boolean hasError = false;

            if (row.getFullName() == null || row.getFullName().trim().isEmpty()) {
                addError(resultDto, row.getRowNumber(), row.getStudentCode(), "full_name", "Full name must not be blank");
                hasError = true;
            }

            if (row.getDob() == null) {
                addError(resultDto, row.getRowNumber(), row.getStudentCode(), "dob", "Date of birth format is invalid (expected YYYY-MM-DD)");
                hasError = true;
            }

            if (row.getStudentCode() != null && !row.getStudentCode().trim().isEmpty()) {
                String code = row.getStudentCode().trim();
                if (!STUDENT_CODE_PATTERN.matcher(code).matches()) {
                    addError(resultDto, row.getRowNumber(), code, "student_code", "Student code must contain only letters, numbers, hyphens or underscores (2-30 characters)");
                    hasError = true;
                } else if (!seenStudentCodes.add(code.toLowerCase())) {
                    addError(resultDto, row.getRowNumber(), code, "student_code", "Duplicate student code within the file");
                    hasError = true;
                } else if (userRepository.existsBySchoolIdAndStudentCodeIgnoreCase(context.getSchoolId(), code)) {
                    addError(resultDto, row.getRowNumber(), code, "student_code", "Student code already exists in this school");
                    hasError = true;
                }
            }

            if (row.getEmail() != null && !row.getEmail().trim().isEmpty()) {
                String em = row.getEmail().trim().toLowerCase();
                if (!seenEmails.add(em)) {
                    addError(resultDto, row.getRowNumber(), row.getStudentCode(), "email", "Duplicate email within the file");
                    hasError = true;
                } else if (userRepository.existsByEmailIgnoreCase(em)) {
                    addError(resultDto, row.getRowNumber(), row.getStudentCode(), "email", "Email already in use across the system");
                    hasError = true;
                }
            }

            if (!hasError) {
                validRows.add(row);
            }
        }

        return validRows;
    }

    protected void persistAccounts(
            List<StudentImportRowDto> validRows,
            StudentImportContext context,
            School school,
            StudentImportResultDto resultDto) {

        int baseToken = context.getDefaultInitialToken() != null ? context.getDefaultInitialToken() : 10000;
        int currentYear = Year.now().getValue() % 100;
        long existingStudentCount = userRepository.countBySchoolIdAndRole(school.getId(), UserRole.SCHOOL_STUDENT);

        for (StudentImportRowDto row : validRows) {
            existingStudentCount++;

            String studentCode = (row.getStudentCode() != null && !row.getStudentCode().trim().isEmpty())
                    ? row.getStudentCode().trim()
                    : String.format("%s_HS%02d%05d", school.getSchoolCode(), currentYear, existingStudentCount);

            String userName = String.format("%s_hs_%s", school.getSchoolCode().toLowerCase(), studentCode.toLowerCase().replaceAll("[^a-z0-9]", "_"));

            String email = (row.getEmail() != null && !row.getEmail().trim().isEmpty())
                    ? row.getEmail().trim().toLowerCase()
                    : String.format("%s@%s", userName, shadowEmailDomain);

            String rawPassword = (row.getPassword() != null && row.getPassword().trim().length() >= 6)
                    ? row.getPassword().trim()
                    : studentCode + "@2026";

            int extraToken = row.getAdditionalToken() != null ? Math.max(0, row.getAdditionalToken()) : 0;
            int totalTokens = baseToken + extraToken;

            Gender gender = null;
            if (row.getGender() != null && !row.getGender().trim().isEmpty()) {
                String g = row.getGender().trim().toUpperCase();
                if ("MALE".equals(g)) {
                    gender = Gender.MALE;
                } else if ("FEMALE".equals(g)) {
                    gender = Gender.FEMALE;
                }
            }

            User student = User.builder()
                    .userName(userName)
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword))
                    .fullName(row.getFullName().trim())
                    .dob(row.getDob())
                    .gender(gender)
                    .phoneNumber(row.getPhoneNumber() != null ? row.getPhoneNumber().trim() : null)
                    .studentCode(studentCode)
                    .role(UserRole.SCHOOL_STUDENT)
                    .school(school)
                    .token(totalTokens)
                    .mustChangePassword(true)
                    .build();

            User saved = userRepository.save(student);

            resultDto.getSuccessfulAccounts().add(ImportedStudentAccountDto.builder()
                    .uid(saved.getUid())
                    .studentCode(studentCode)
                    .userName(userName)
                    .fullName(saved.getFullName())
                    .email(email)
                    .allocatedToken(totalTokens)
                    .initialPassword(rawPassword)
                    .build());
        }
    }

    protected void addError(StudentImportResultDto resultDto, int rowNumber, String studentCode, String field, String message) {
        resultDto.getErrors().add(StudentImportResultDto.ImportErrorDetail.builder()
                .rowNumber(rowNumber)
                .studentCode(studentCode)
                .field(field)
                .message(message)
                .build());
    }

    private String getRecordValue(CSVRecord record, String header) {
        try {
            return record.isMapped(header) ? record.get(header) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Các bước ĐA HÌNH
    protected abstract void verifyAndDeductQuota(List<StudentImportRowDto> rows, StudentImportContext context, School school);
}
