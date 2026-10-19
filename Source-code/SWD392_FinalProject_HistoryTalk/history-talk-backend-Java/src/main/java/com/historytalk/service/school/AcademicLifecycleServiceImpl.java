package com.historytalk.service.school;

import com.historytalk.dto.school.AcademicYearRolloverRequest;
import com.historytalk.dto.school.AcademicYearRolloverResponse;
import com.historytalk.dto.school.DeactivateStudentRequest;
import com.historytalk.dto.school.RenewSchoolSubscriptionRequest;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.dto.school.TopUpSchoolTokensRequest;
import com.historytalk.dto.school.UpdateSchoolTokenPolicyRequest;
import com.historytalk.entity.classroom.ClassStudent;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.AccountStatus;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicLifecycleServiceImpl implements AcademicLifecycleService {

    private static final double MAX_ROLLOVER_RATE = 0.20;

    private final SchoolRepository schoolRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public SchoolResponse renewSchoolSubscription(UUID schoolId, RenewSchoolSubscriptionRequest request) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + schoolId));

        LocalDate refDate = request.getReferenceDate() != null ? request.getReferenceDate() : LocalDate.now();
        if (refDate.getMonth() != Month.MAY) {
            throw new InvalidRequestException(
                    "Theo quy định gói trường học (01/09 - 31/05), việc gia hạn cho năm học kế tiếp phải được thực hiện trong tháng 5 (01/05 - 31/05).");
        }

        school.setRenewedForNextYear(true);
        school.setNextPackageType(request.getPackageType());

        School saved = schoolRepository.save(school);
        log.info("School {} renewed subscription in May for next academic year with package {}",
                school.getSchoolCode(), request.getPackageType());

        return mapToSchoolResponse(saved);
    }

    @Override
    @Transactional
    public SchoolResponse activateRenewedSubscription(UUID schoolId, LocalDate referenceDate) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + schoolId));

        if (!Boolean.TRUE.equals(school.getRenewedForNextYear()) || school.getNextPackageType() == null) {
            throw new InvalidRequestException("Trường học chưa đăng ký gia hạn gói cho năm học mới.");
        }

        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();
        if (refDate.getMonthValue() < 9) {
            throw new InvalidRequestException("Gói gia hạn năm học mới chỉ được kích hoạt bắt đầu từ ngày 01/09.");
        }

        // Reclaim any remaining student daily tokens before calculating 20% rollover
        long reclaimed = reclaimAllStudentTokensToPool(school);

        int remainingOldPool = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        int rolloverTokens = (int) Math.floor(remainingOldPool * MAX_ROLLOVER_RATE);

        EnterprisePackage nextPkg = school.getNextPackageType();
        int newPackageTokens = nextPkg.getTokens();
        int newTotalQuota = newPackageTokens + rolloverTokens;

        int startYear = refDate.getYear();
        LocalDate newStart = LocalDate.of(startYear, 9, 1);
        LocalDate newEnd = LocalDate.of(startYear + 1, 5, 31);

        school.setPackageType(nextPkg);
        school.setTotalSchoolTokenQuota(newTotalQuota);
        school.setUnallocatedTokenQuota(newTotalQuota);
        school.setSubscriptionStartDate(newStart);
        school.setSubscriptionEndDate(newEnd);
        school.setRenewedForNextYear(false);
        school.setNextPackageType(null);
        school.setStatus(SchoolStatus.ACTIVE);

        School saved = schoolRepository.save(school);
        log.info("Activated new academic year subscription for school {} ({} to {}): reclaimed={}, rollover20%={}, newTotal={}",
                school.getSchoolCode(), newStart, newEnd, reclaimed, rolloverTokens, newTotalQuota);

        return mapToSchoolResponse(saved);
    }

    @Override
    @Transactional
    public SchoolResponse topUpSchoolTokens(UUID schoolId, TopUpSchoolTokensRequest request) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + schoolId));

        int addTokens = request.getAdditionalTokens();
        int currentTotal = school.getTotalSchoolTokenQuota() != null ? school.getTotalSchoolTokenQuota() : 0;
        int currentUnallocated = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;

        school.setTotalSchoolTokenQuota(currentTotal + addTokens);
        school.setUnallocatedTokenQuota(currentUnallocated + addTokens);

        School saved = schoolRepository.save(school);
        log.info("Top-up +{} tokens for school {}. New unallocated pool: {}",
                addTokens, school.getSchoolCode(), saved.getUnallocatedTokenQuota());

        return mapToSchoolResponse(saved);
    }

    @Override
    @Transactional
    public SchoolResponse updateSchoolTokenPolicy(
            UserPrincipal schoolAdminPrincipal, UpdateSchoolTokenPolicyRequest request) {
        UserPrincipal principal = resolvePrincipal(schoolAdminPrincipal);
        UUID schoolId = resolveSchoolId(principal);

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + schoolId));

        if (request.getDefaultDailyStudentQuota() != null) {
            school.setDefaultDailyStudentQuota(request.getDefaultDailyStudentQuota());
            schoolRepository.save(school);
        }

        if (request.getClassroomId() != null && request.getClassroomDailyQuota() != null) {
            Classroom classroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(request.getClassroomId(), schoolId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học trong trường"));
            classroom.setDailyTokenQuota(request.getClassroomDailyQuota());
            classroomRepository.save(classroom);
        } else if (request.getGradeLevel() != null && request.getClassroomDailyQuota() != null) {
            List<Classroom> gradeClassrooms = classroomRepository.findBySchoolIdAndGradeLevelAndDeletedAtIsNull(
                    schoolId, request.getGradeLevel());
            for (Classroom c : gradeClassrooms) {
                c.setDailyTokenQuota(request.getClassroomDailyQuota());
            }
            classroomRepository.saveAll(gradeClassrooms);
        }

        return mapToSchoolResponse(school);
    }

    @Override
    @Transactional
    public AcademicYearRolloverResponse rolloverAcademicYear(
            UserPrincipal schoolAdminPrincipal, AcademicYearRolloverRequest request) {
        UserPrincipal principal = resolvePrincipal(schoolAdminPrincipal);
        UUID schoolId = resolveSchoolId(principal);

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học với ID: " + schoolId));

        List<Classroom> oldYearClasses = classroomRepository.findBySchoolIdAndAcademicYearAndDeletedAtIsNull(
                schoolId, request.getCompletedAcademicYear());

        int archivedCount = 0;
        Set<UUID> graduatedStudentUids = new HashSet<>();

        for (Classroom c : oldYearClasses) {
            if (!"ARCHIVED".equals(c.getStatus())) {
                c.setStatus("ARCHIVED");
                archivedCount++;
            }
            if (Boolean.TRUE.equals(request.getDeactivateGraduatedGrade12())
                    && Integer.valueOf(12).equals(c.getGradeLevel())) {
                List<UUID> studentUids = classStudentRepository.findEnrolledStudentUids(c.getId());
                graduatedStudentUids.addAll(studentUids);
            }
        }
        classroomRepository.saveAll(oldYearClasses);

        // Reclaim all daily tokens remaining on student accounts back to the School Shared Pool
        long reclaimedTokens = reclaimAllStudentTokensToPool(school);

        // Deactivate graduated Grade 12 students so they cannot log in anymore
        int deactivatedCount = 0;
        for (UUID gradUid : graduatedStudentUids) {
            User student = userRepository.findById(gradUid).orElse(null);
            if (student != null && student.getAccountStatus() != AccountStatus.DEACTIVATED) {
                deactivateStudentInternal(student, school, "GRADUATED_GRADE_12");
                deactivatedCount++;
            }
        }

        schoolRepository.save(school);

        log.info("Academic year rollover completed for school {} (year {}): archivedClasses={}, reclaimedTokens={}, deactivatedGraduates={}",
                school.getSchoolCode(), request.getCompletedAcademicYear(), archivedCount, reclaimedTokens, deactivatedCount);

        return AcademicYearRolloverResponse.builder()
                .schoolId(schoolId)
                .completedAcademicYear(request.getCompletedAcademicYear())
                .archivedClassroomsCount(archivedCount)
                .reclaimedTokensToPool(reclaimedTokens)
                .deactivatedGraduatedStudentsCount(deactivatedCount)
                .remainingSchoolTokenPool(school.getUnallocatedTokenQuota())
                .build();
    }

    @Override
    @Transactional
    public StudentResponse deactivateStudentAccount(
            UserPrincipal schoolAdminPrincipal, UUID studentUid, DeactivateStudentRequest request) {
        UserPrincipal principal = resolvePrincipal(schoolAdminPrincipal);
        UUID schoolId = resolveSchoolId(principal);

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường học"));

        User student = userRepository.findById(studentUid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh với ID: " + studentUid));

        if (student.getSchool() == null || !schoolId.equals(student.getSchool().getId())
                || student.getRole() != UserRole.SCHOOL_STUDENT) {
            throw new AccessDeniedException("Học sinh không thuộc trường của bạn.");
        }

        deactivateStudentInternal(student, school, request.getReason().trim());
        schoolRepository.save(school);
        User saved = userRepository.save(student);

        log.info("Deactivated student {} ({}) in school {} for reason: {}",
                saved.getUserName(), saved.getStudentCode(), school.getSchoolCode(), request.getReason());

        return StudentResponse.builder()
                .uid(saved.getUid())
                .studentCode(saved.getStudentCode())
                .userName(saved.getUserName())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .dob(saved.getDob())
                .gender(saved.getGender())
                .phoneNumber(saved.getPhoneNumber())
                .token(saved.getToken())
                .active(false)
                .schoolId(school.getId())
                .schoolName(school.getName())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    private long reclaimAllStudentTokensToPool(School school) {
        List<User> students = userRepository.findBySchoolIdAndRoleAndDeletedAtIsNull(
                school.getId(), UserRole.SCHOOL_STUDENT);
        long totalReclaimed = 0L;
        for (User s : students) {
            int remaining = s.getToken() != null ? Math.max(0, s.getToken()) : 0;
            if (remaining > 0) {
                totalReclaimed += remaining;
                s.setToken(0);
            }
        }
        if (totalReclaimed > 0) {
            int currentPool = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
            school.setUnallocatedTokenQuota((int) Math.min(Integer.MAX_VALUE, currentPool + totalReclaimed));
            userRepository.saveAll(students);
        }
        return totalReclaimed;
    }

    private void deactivateStudentInternal(User student, School school, String reason) {
        int remainingTokens = student.getToken() != null ? Math.max(0, student.getToken()) : 0;
        if (remainingTokens > 0) {
            int currentPool = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
            school.setUnallocatedTokenQuota(currentPool + remainingTokens);
            student.setToken(0);
        }

        student.setAccountStatus(AccountStatus.DEACTIVATED);
        student.setDeactivatedReason(reason);
        student.setDeletedAt(LocalDateTime.now());

        List<ClassStudent> activeEnrollments = classStudentRepository.findByStudentUidAndStatus(student.getUid(), "ACTIVE");
        for (ClassStudent cs : activeEnrollments) {
            cs.setStatus("INACTIVE");
        }
        if (!activeEnrollments.isEmpty()) {
            classStudentRepository.saveAll(activeEnrollments);
        }
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) {
            return principal;
        }
        String userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
        return new UserPrincipal(user);
    }

    private UUID resolveSchoolId(UserPrincipal principal) {
        if (principal != null && principal.getSchoolId() != null) {
            return principal.getSchoolId();
        }
        throw new AccessDeniedException("Tài khoản không thuộc trường học nào.");
    }

    private SchoolResponse mapToSchoolResponse(School school) {
        return SchoolResponse.builder()
                .id(school.getId())
                .name(school.getName())
                .schoolCode(school.getSchoolCode())
                .address(school.getAddress())
                .contactEmail(school.getContactEmail())
                .contactPhone(school.getContactPhone())
                .packageType(school.getPackageType())
                .recommendedStudents(school.getPackageType() != null ? school.getPackageType().getRecommendedStudents() : null)
                .totalSchoolTokenQuota(school.getTotalSchoolTokenQuota())
                .unallocatedTokenQuota(school.getUnallocatedTokenQuota())
                .defaultDailyStudentQuota(school.getDefaultDailyStudentQuota())
                .subscriptionStartDate(school.getSubscriptionStartDate())
                .subscriptionEndDate(school.getSubscriptionEndDate())
                .renewedForNextYear(school.getRenewedForNextYear())
                .nextPackageType(school.getNextPackageType())
                .localHistoryPolicyAccepted(school.getLocalHistoryPolicyAccepted())
                .status(school.getStatus())
                .createdAt(school.getCreatedAt() != null ? school.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}
