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
        LocalDate subEnd = school.getSubscriptionEndDate();
        boolean isEligible;
        if (subEnd != null) {
            LocalDate windowStart = subEnd.minusDays(31);
            isEligible = (!refDate.isBefore(windowStart) && !refDate.isAfter(subEnd.plusDays(30))) || (refDate.getMonth() == Month.MAY);
        } else {
            isEligible = (refDate.getMonth() == Month.MAY);
        }

        if (!isEligible) {
            throw new InvalidRequestException(
                    "Việc gia hạn cho năm học kế tiếp phải được thực hiện trong tháng 5 hoặc trong vòng 30 ngày trước khi gói hiện tại hết hạn.");
        }

        school.setRenewedForNextYear(true);
        school.setNextPackageType(request.getPackageType());

        if (request.getPackageType() == EnterprisePackage.ENTERPRISE_CUSTOM) {
            Integer customQuota = request.getCustomTokenQuota();
            if (customQuota != null && customQuota > 0) {
                school.setNextCustomTokenQuota(customQuota);
            } else {
                // Scenario 1: Retain existing custom contracted scale
                Integer existingQuota = school.getBaseCustomTokenQuota() != null
                        ? school.getBaseCustomTokenQuota()
                        : school.getTotalSchoolTokenQuota();
                if (existingQuota == null || existingQuota <= 0) {
                    throw new InvalidRequestException(
                            "Vui lòng nhập định mức token tùy chỉnh (customTokenQuota) khi gia hạn gói Tùy chỉnh.");
                }
                school.setNextCustomTokenQuota(existingQuota);
            }

            if (request.getMaxStudents() != null) {
                school.setNextMaxStudents(request.getMaxStudents());
            }
            if (request.getMaxTeachers() != null) {
                school.setNextMaxTeachers(request.getMaxTeachers());
            }
            if (request.getDefaultDailyStudentQuota() != null) {
                school.setNextDailyStudentQuota(request.getDefaultDailyStudentQuota());
            }
            if (request.getDefaultDailyTeacherQuota() != null) {
                school.setNextDailyTeacherQuota(request.getDefaultDailyTeacherQuota());
            }
            if (request.getSubscriptionStartDate() != null) {
                school.setNextSubscriptionStartDate(request.getSubscriptionStartDate());
            }
            if (request.getSubscriptionEndDate() != null) {
                school.setNextSubscriptionEndDate(request.getSubscriptionEndDate());
            }
        } else {
            // Standard package selected: clear custom overrides
            school.setNextCustomTokenQuota(null);
            school.setNextMaxStudents(request.getMaxStudents());
            school.setNextMaxTeachers(request.getMaxTeachers());
            school.setNextDailyStudentQuota(request.getDefaultDailyStudentQuota());
            school.setNextDailyTeacherQuota(request.getDefaultDailyTeacherQuota());
            school.setNextSubscriptionStartDate(request.getSubscriptionStartDate());
            school.setNextSubscriptionEndDate(request.getSubscriptionEndDate());
        }

        School saved = schoolRepository.save(school);
        log.info("School {} renewed subscription in May for next academic year with package {}, nextCustomQuota={}",
                school.getSchoolCode(), request.getPackageType(), school.getNextCustomTokenQuota());

        return mapToSchoolResponse(saved);
    }

    @Override
    @Transactional
    public SchoolResponse renewSchoolSubscriptionForSchoolAdmin(
            UserPrincipal schoolAdminPrincipal, RenewSchoolSubscriptionRequest request) {
        UserPrincipal principal = resolvePrincipal(schoolAdminPrincipal);
        UUID schoolId = resolveSchoolId(principal);
        return renewSchoolSubscription(schoolId, request);
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
        LocalDate subEnd = school.getSubscriptionEndDate();
        boolean canActivate = (subEnd != null && !refDate.isBefore(subEnd)) || refDate.getMonthValue() >= 9;
        if (!canActivate) {
            throw new InvalidRequestException("Gói gia hạn năm học mới chỉ được kích hoạt sau khi gói cũ kết thúc (hoặc bắt đầu từ ngày 01/09).");
        }

        // Pure Shared Pool: All unspent tokens are already inside school.unallocatedTokenQuota.
        int remainingOldPool = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        int rolloverTokens = (int) Math.floor(remainingOldPool * MAX_ROLLOVER_RATE);

        EnterprisePackage nextPkg = school.getNextPackageType();
        int newPackageTokens;
        if (nextPkg == EnterprisePackage.ENTERPRISE_CUSTOM) {
            Integer customQuota = school.getNextCustomTokenQuota();
            if (customQuota == null || customQuota <= 0) {
                customQuota = school.getBaseCustomTokenQuota() != null
                        ? school.getBaseCustomTokenQuota()
                        : school.getTotalSchoolTokenQuota();
            }
            if (customQuota == null || customQuota <= 0) {
                throw new InvalidRequestException("Không tìm thấy hạn mức token tùy chỉnh hợp lệ cho năm học mới.");
            }
            newPackageTokens = customQuota;
            school.setBaseCustomTokenQuota(customQuota);
        } else {
            newPackageTokens = nextPkg.getTokens();
            school.setBaseCustomTokenQuota(null);
        }

        int newTotalQuota = newPackageTokens + rolloverTokens;

        LocalDate newStart;
        LocalDate newEnd;
        if (school.getNextSubscriptionStartDate() != null && school.getNextSubscriptionEndDate() != null) {
            newStart = school.getNextSubscriptionStartDate();
            newEnd = school.getNextSubscriptionEndDate();
        } else if (school.getSubscriptionStartDate() != null && school.getSubscriptionEndDate() != null) {
            long cycleDays = java.time.temporal.ChronoUnit.DAYS.between(
                    school.getSubscriptionStartDate(), school.getSubscriptionEndDate());
            newStart = refDate;
            newEnd = newStart.plusDays(cycleDays);
        } else {
            int startYear = refDate.getYear();
            newStart = LocalDate.of(startYear, 9, 1);
            newEnd = LocalDate.of(startYear + 1, 5, 31);
        }

        // Apply updated caps if configured
        if (school.getNextMaxStudents() != null) {
            school.setMaxStudents(school.getNextMaxStudents());
        } else if (nextPkg != EnterprisePackage.ENTERPRISE_CUSTOM && nextPkg.getRecommendedStudents() > 0) {
            school.setMaxStudents(nextPkg.getRecommendedStudents());
        }

        if (school.getNextMaxTeachers() != null) {
            school.setMaxTeachers(school.getNextMaxTeachers());
        } else if (nextPkg != EnterprisePackage.ENTERPRISE_CUSTOM && nextPkg.getRecommendedTeachers() > 0) {
            school.setMaxTeachers(nextPkg.getRecommendedTeachers());
        }

        if (school.getNextDailyStudentQuota() != null) {
            school.setDefaultDailyStudentQuota(school.getNextDailyStudentQuota());
        } else if (nextPkg.getDefaultDailyQuota() > 0) {
            school.setDefaultDailyStudentQuota(nextPkg.getDefaultDailyQuota());
        }

        if (school.getNextDailyTeacherQuota() != null) {
            school.setDefaultDailyTeacherQuota(school.getNextDailyTeacherQuota());
        } else if (nextPkg.getDefaultDailyTeacherQuota() > 0) {
            school.setDefaultDailyTeacherQuota(nextPkg.getDefaultDailyTeacherQuota());
        }

        school.setPackageType(nextPkg);
        school.setTotalSchoolTokenQuota(newTotalQuota);
        school.setUnallocatedTokenQuota(newTotalQuota);
        school.setSubscriptionStartDate(newStart);
        school.setSubscriptionEndDate(newEnd);
        school.setRenewedForNextYear(false);
        school.setNextPackageType(null);
        school.setNextCustomTokenQuota(null);
        school.setNextMaxStudents(null);
        school.setNextMaxTeachers(null);
        school.setNextDailyStudentQuota(null);
        school.setNextDailyTeacherQuota(null);
        school.setNextSubscriptionStartDate(null);
        school.setNextSubscriptionEndDate(null);
        school.setStatus(SchoolStatus.ACTIVE);

        School saved = schoolRepository.save(school);
        log.info("Activated new academic year subscription for school {} ({} to {}): rollover20%={}, newTotal={}",
                school.getSchoolCode(), newStart, newEnd, rolloverTokens, newTotalQuota);

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
        if (request.getDefaultDailyTeacherQuota() != null) {
            school.setDefaultDailyTeacherQuota(request.getDefaultDailyTeacherQuota());
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

        // Reset daily counters on student accounts (tokens remain 100% in school.unallocatedTokenQuota)
        resetAllStudentDailyCounters(school);

        // Deactivate graduated Grade 12 students so they cannot log in anymore
        int deactivatedCount = 0;
        for (UUID gradUid : graduatedStudentUids) {
            User student = userRepository.findById(gradUid).orElse(null);
            if (student != null && student.getAccountStatus() != AccountStatus.DEACTIVATED) {
                deactivateStudentInternal(student, "GRADUATED_GRADE_12");
                deactivatedCount++;
            }
        }

        schoolRepository.save(school);

        log.info("Academic year rollover completed for school {} (year {}): archivedClasses={}, deactivatedGraduates={}, remainingPool={}",
                school.getSchoolCode(), request.getCompletedAcademicYear(), archivedCount, deactivatedCount, school.getUnallocatedTokenQuota());

        return AcademicYearRolloverResponse.builder()
                .schoolId(schoolId)
                .completedAcademicYear(request.getCompletedAcademicYear())
                .archivedClassroomsCount(archivedCount)
                .reclaimedTokensToPool(0L)
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

        deactivateStudentInternal(student, request.getReason().trim());
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

    private void resetAllStudentDailyCounters(School school) {
        List<User> students = userRepository.findBySchoolIdAndRoleAndDeletedAtIsNull(
                school.getId(), UserRole.SCHOOL_STUDENT);
        if (!students.isEmpty()) {
            for (User s : students) {
                s.setToken(0);
                s.setDailyTokensUsed(0);
                s.setDailyBonusTokens(0);
            }
            userRepository.saveAll(students);
        }
    }

    private void deactivateStudentInternal(User student, String reason) {
        student.setToken(0);
        student.setDailyTokensUsed(0);
        student.setDailyBonusTokens(0);
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
                .defaultDailyTeacherQuota(school.getDefaultDailyTeacherQuota())
                .subscriptionStartDate(school.getSubscriptionStartDate())
                .subscriptionEndDate(school.getSubscriptionEndDate())
                .renewedForNextYear(school.getRenewedForNextYear())
                .nextPackageType(school.getNextPackageType())
                .nextCustomTokenQuota(school.getNextCustomTokenQuota())
                .baseCustomTokenQuota(school.getBaseCustomTokenQuota())
                .maxStudents(school.getMaxStudents())
                .maxTeachers(school.getMaxTeachers())
                .localHistoryPolicyAccepted(school.getLocalHistoryPolicyAccepted())
                .status(school.getStatus())
                .createdAt(school.getCreatedAt() != null ? school.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}
