package com.historytalk.service.classroom;

import com.historytalk.dto.school.ImportedStudentAccountDto;
import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.dto.school.StudentImportRowDto;
import com.historytalk.entity.classroom.ClassStudent;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.Gender;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.service.school.student.AbstractStudentImportProcessor;
import com.historytalk.entity.enums.ImportScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Year;
import java.util.*;

@Slf4j
@Service
public class ClassroomImportProcessor extends AbstractStudentImportProcessor {

    private final ClassroomRepository classroomRepository;
    private final ClassStudentRepository classStudentRepository;

    @Override
    public ImportScope getScope() {
        return ImportScope.CLASSROOM;
    }

    public ClassroomImportProcessor(
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
    @Transactional
    public StudentImportResultDto processImport(MultipartFile file, StudentImportContext context) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Upload file must not be empty");
        }
        if (context.getClassroomId() == null) {
            throw new InvalidRequestException("Classroom ID must be specified for classroom import");
        }

        School school = schoolRepository.findById(context.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + context.getSchoolId()));

        Classroom classroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(context.getClassroomId(), context.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with ID: " + context.getClassroomId()));

        StudentImportResultDto resultDto = StudentImportResultDto.builder()
                .errors(new ArrayList<>())
                .successfulAccounts(new ArrayList<>())
                .build();

        List<StudentImportRowDto> rawRows = parseRecords(file);
        resultDto.setTotalRows(rawRows.size());
        if (rawRows.isEmpty()) {
            throw new InvalidRequestException("The file contains no data rows");
        }

        List<RowPlan> plans = new ArrayList<>();
        Set<String> seenCodesInFile = new HashSet<>();
        Set<String> seenEmailsInFile = new HashSet<>();

        for (StudentImportRowDto row : rawRows) {
            boolean hasError = false;
            String rawCode = row.getStudentCode() != null ? row.getStudentCode().trim() : null;
            String rawEmail = row.getEmail() != null ? row.getEmail().trim().toLowerCase() : null;

            // 1. Check if student already exists in this school
            Optional<User> existingStudentOpt = Optional.empty();
            if (rawCode != null && !rawCode.isEmpty()) {
                existingStudentOpt = userRepository.findBySchoolIdAndStudentCodeIgnoreCase(school.getId(), rawCode);
            }
            if (existingStudentOpt.isEmpty() && rawEmail != null && !rawEmail.isEmpty()) {
                existingStudentOpt = userRepository.findBySchoolIdAndEmailIgnoreCase(school.getId(), rawEmail);
            }

            if (existingStudentOpt.isPresent()) {
                // CASE 1: Học sinh ĐÃ TỒN TẠI trong trường
                User existingStudent = existingStudentOpt.get();
                if (classStudentRepository.existsByClassroomIdAndStudentUid(classroom.getId(), existingStudent.getUid())) {
                    plans.add(new RowPlan(row, existingStudent, true, true));
                } else {
                    plans.add(new RowPlan(row, existingStudent, true, false));
                }
            } else {
                // CASE 2: Học sinh MỚI TINH cần onboarding
                if (row.getFullName() == null || row.getFullName().trim().isEmpty()) {
                    addError(resultDto, row.getRowNumber(), rawCode, "full_name", "Full name must not be blank");
                    hasError = true;
                }
                if (row.getDob() == null) {
                    addError(resultDto, row.getRowNumber(), rawCode, "dob", "Date of birth format is invalid (expected YYYY-MM-DD)");
                    hasError = true;
                }
                if (rawCode != null && !rawCode.isEmpty()) {
                    if (!rawCode.matches("^[A-Za-z0-9_-]{2,30}$")) {
                        addError(resultDto, row.getRowNumber(), rawCode, "student_code", "Student code must contain only letters, numbers, hyphens or underscores (2-30 characters)");
                        hasError = true;
                    } else if (!seenCodesInFile.add(rawCode.toLowerCase())) {
                        addError(resultDto, row.getRowNumber(), rawCode, "student_code", "Duplicate student code within the file");
                        hasError = true;
                    }
                }
                if (rawEmail != null && !rawEmail.isEmpty()) {
                    if (!seenEmailsInFile.add(rawEmail)) {
                        addError(resultDto, row.getRowNumber(), rawCode, "email", "Duplicate email within the file");
                        hasError = true;
                    } else if (userRepository.existsByEmailIgnoreCase(rawEmail)) {
                        addError(resultDto, row.getRowNumber(), rawCode, "email", "Email already in use across the system");
                        hasError = true;
                    }
                }

                if (!hasError) {
                    plans.add(new RowPlan(row, null, false, false));
                }
            }
        }

        // Deduct quota ONLY for new students
        int baseToken = context.getDefaultInitialToken() != null ? context.getDefaultInitialToken() : 10000;
        int totalTokensNeededForNew = plans.stream()
                .filter(p -> !p.isExisting)
                .mapToInt(p -> baseToken + (p.row.getAdditionalToken() != null ? Math.max(0, p.row.getAdditionalToken()) : 0))
                .sum();

        int availableTokens = school.getUnallocatedTokenQuota();
        if (availableTokens < totalTokensNeededForNew) {
            throw new InvalidRequestException(String.format(
                    "Insufficient unallocated token quota: Requires %,d tokens for new students, but the school only has %,d available tokens.",
                    totalTokensNeededForNew, availableTokens));
        }

        if (totalTokensNeededForNew > 0) {
            school.setUnallocatedTokenQuota(availableTokens - totalTokensNeededForNew);
            schoolRepository.save(school);
            log.info("Deducted %,d tokens for new students in classroom import. Remaining quota: %,d tokens",
                    totalTokensNeededForNew, school.getUnallocatedTokenQuota());
        }

        // Persist & Enroll
        int currentYear = Year.now().getValue() % 100;
        long existingStudentCount = userRepository.countBySchoolIdAndRole(school.getId(), UserRole.SCHOOL_STUDENT);

        for (RowPlan plan : plans) {
            if (plan.isExisting) {
                User student = plan.existingStudent;
                if (!plan.alreadyEnrolled) {
                    classStudentRepository.save(ClassStudent.builder()
                            .classroom(classroom)
                            .student(student)
                            .status("ACTIVE")
                            .build());
                    log.info("Enrolled existing student {} ({}) into class {}", student.getFullName(), student.getStudentCode(), classroom.getClassCode());
                }
                resultDto.getSuccessfulAccounts().add(ImportedStudentAccountDto.builder()
                        .uid(student.getUid())
                        .studentCode(student.getStudentCode())
                        .userName(student.getUserName())
                        .fullName(student.getFullName())
                        .email(student.getEmail())
                        .allocatedToken(0)
                        .initialPassword(plan.alreadyEnrolled ? "(Đã có trong lớp từ trước)" : "(Tài khoản hiện có)")
                        .build());
            } else {
                existingStudentCount++;
                StudentImportRowDto row = plan.row;
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
                    if ("MALE".equals(g)) gender = Gender.MALE;
                    else if ("FEMALE".equals(g)) gender = Gender.FEMALE;
                }

                User newStudent = User.builder()
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

                User savedStudent = userRepository.save(newStudent);

                classStudentRepository.save(ClassStudent.builder()
                        .classroom(classroom)
                        .student(savedStudent)
                        .status("ACTIVE")
                        .build());

                resultDto.getSuccessfulAccounts().add(ImportedStudentAccountDto.builder()
                        .uid(savedStudent.getUid())
                        .studentCode(studentCode)
                        .userName(userName)
                        .fullName(savedStudent.getFullName())
                        .email(email)
                        .allocatedToken(totalTokens)
                        .initialPassword(rawPassword)
                        .build());
            }
        }

        resultDto.setSuccessCount(resultDto.getSuccessfulAccounts().size());
        resultDto.setFailureCount(resultDto.getErrors().size());
        resultDto.setRemainingSchoolTokens(school.getUnallocatedTokenQuota());

        return resultDto;
    }

    @Override
    protected void verifyAndDeductQuota(List<StudentImportRowDto> rows, StudentImportContext context, School school) {
        // Handled in processImport
    }

    private static class RowPlan {
        StudentImportRowDto row;
        User existingStudent;
        boolean isExisting;
        boolean alreadyEnrolled;

        RowPlan(StudentImportRowDto row, User existingStudent, boolean isExisting, boolean alreadyEnrolled) {
            this.row = row;
            this.existingStudent = existingStudent;
            this.isExisting = isExisting;
            this.alreadyEnrolled = alreadyEnrolled;
        }
    }
}
