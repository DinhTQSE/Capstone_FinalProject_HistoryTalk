package com.historytalk.service.classroom;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.ClassroomResponse;
import com.historytalk.dto.classroom.CreateClassroomRequest;
import com.historytalk.dto.classroom.TeacherSummaryDto;
import com.historytalk.dto.classroom.UpdateClassroomRequest;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final ClassStudentRepository classStudentRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final SmartClassCodeGenerator smartClassCodeGenerator;

    @Override
    @Transactional
    public ClassroomResponse createClassroom(CreateClassroomRequest request, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        if (schoolId == null) {
            throw new AccessDeniedException("User does not belong to any school tenant");
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with ID: " + schoolId));

        String trimmedClassName = request.getClassName().trim();

        // 1. Kiá»ƒm tra chá»‘ng trÃ¹ng tÃªn lá»›p trong cÃ¹ng trÆ°á»ng vÃ  nÄƒm há»c
        if (classroomRepository.existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndDeletedAtIsNull(
                schoolId, trimmedClassName, request.getAcademicYear())) {
            throw new DataConflictException(String.format(
                    "Lá»›p há»c \"%s\" Ä‘Ã£ tá»“n táº¡i trong nÄƒm há»c %d cá»§a trÆ°á»ng",
                    trimmedClassName, request.getAcademicYear()));
        }

        // 2. XÃ¡c Ä‘á»‹nh GiÃ¡o viÃªn phá»¥ trÃ¡ch
        User teacher = null;
        if (userPrincipal.getRole() == UserRole.TEACHER) {
            // Náº¿u Teacher tá»± táº¡o lá»›p thÃ¬ máº·c Ä‘á»‹nh gÃ¡n cho chÃ­nh mÃ¬nh
            teacher = userRepository.findById(UUID.fromString(userPrincipal.getUid()))
                    .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));
        } else if (userPrincipal.getRole() == UserRole.SCHOOL_ADMIN) {
            if (request.getTeacherId() != null) {
                teacher = validateAndGetTeacher(request.getTeacherId(), schoolId);
            }
        }

        // 3. Sinh mÃ£ lá»›p há»c thÃ´ng minh (kÃ¨m mÃ£ trÆ°á»ng vÃ  nÄƒm há»c)
        String classCode = smartClassCodeGenerator.generateClassCode(
                school.getSchoolCode(), trimmedClassName, request.getAcademicYear());

        Classroom classroom = Classroom.builder()
                .school(school)
                .teacher(teacher)
                .className(trimmedClassName)
                .classCode(classCode)
                .gradeLevel(request.getGradeLevel())
                .academicYear(request.getAcademicYear())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .dailyTokenQuota(request.getDailyTokenQuota())
                .status("ACTIVE")
                .build();

        Classroom saved = classroomRepository.save(classroom);
        log.info("Created classroom: {} with code: {} in school: {}", saved.getClassName(), saved.getClassCode(), school.getSchoolCode());

        return mapToResponse(saved, 0L);
    }

    @Override
    @Transactional
    public ClassroomResponse updateClassroom(UUID id, UpdateClassroomRequest request, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        if (schoolId == null) {
            throw new AccessDeniedException("User does not belong to any school tenant");
        }
        Classroom classroom = findClassroomInTenant(id, schoolId);

        // Kiá»ƒm tra quyá»n háº¡n
        if (userPrincipal.getRole() == UserRole.TEACHER) {
            UUID currentTeacherUid = UUID.fromString(userPrincipal.getUid());
            if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(currentTeacherUid)) {
                throw new AccessDeniedException("Báº¡n khÃ´ng Ä‘Æ°á»£c phÃ¢n cÃ´ng phá»¥ trÃ¡ch lá»›p há»c nÃ y!");
            }
            if (request.getTeacherId() != null && !request.getTeacherId().equals(currentTeacherUid)) {
                throw new AccessDeniedException("GiÃ¡o viÃªn khÃ´ng cÃ³ quyá»n chuyá»ƒn nhÆ°á»£ng quyá»n phá»¥ trÃ¡ch lá»›p cho ngÆ°á»i khÃ¡c!");
            }
        }

        // Cáº­p nháº­t tÃªn lá»›p / nÄƒm há»c náº¿u cÃ³
        String newClassName = request.getClassName() != null ? request.getClassName().trim() : classroom.getClassName();
        Integer newAcademicYear = request.getAcademicYear() != null ? request.getAcademicYear() : classroom.getAcademicYear();

        if (!newClassName.equalsIgnoreCase(classroom.getClassName()) || !newAcademicYear.equals(classroom.getAcademicYear())) {
            if (classroomRepository.existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndIdNotAndDeletedAtIsNull(
                    schoolId, newClassName, newAcademicYear, id)) {
                throw new DataConflictException(String.format(
                        "Lá»›p há»c \"%s\" Ä‘Ã£ tá»“n táº¡i trong nÄƒm há»c %d cá»§a trÆ°á»ng",
                        newClassName, newAcademicYear));
            }
            classroom.setClassName(newClassName);
            classroom.setAcademicYear(newAcademicYear);
        }

        if (request.getGradeLevel() != null) {
            classroom.setGradeLevel(request.getGradeLevel());
        }

        if (request.getDescription() != null) {
            classroom.setDescription(request.getDescription().trim());
        }

        if (request.getDailyTokenQuota() != null) {
            classroom.setDailyTokenQuota(request.getDailyTokenQuota());
        }

        if (request.getStatus() != null) {
            classroom.setStatus(request.getStatus().trim());
        }

        // Cáº­p nháº­t giÃ¡o viÃªn (chá»‰ School Admin má»›i Ä‘Æ°á»£c Ä‘á»•i)
        if (userPrincipal.getRole() == UserRole.SCHOOL_ADMIN && request.getTeacherId() != null) {
            User teacher = validateAndGetTeacher(request.getTeacherId(), schoolId);
            classroom.setTeacher(teacher);
        }

        Classroom updated = classroomRepository.save(classroom);
        long studentCount = classStudentRepository.countByClassroomIdAndStatus(id, "ACTIVE");

        return mapToResponse(updated, studentCount);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomResponse getClassroomById(UUID id, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        if (schoolId == null) {
            throw new AccessDeniedException("User does not belong to any school tenant");
        }
        Classroom classroom = findClassroomInTenant(id, schoolId);

        // Kiá»ƒm tra phÃ¢n quyá»n truy cáº­p chi tiáº¿t lá»›p
        if (userPrincipal.getRole() == UserRole.TEACHER) {
            UUID currentTeacherUid = UUID.fromString(userPrincipal.getUid());
            if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(currentTeacherUid)) {
                throw new AccessDeniedException("Báº¡n khÃ´ng Ä‘Æ°á»£c phÃ¢n cÃ´ng phá»¥ trÃ¡ch lá»›p há»c nÃ y!");
            }
        } else if (userPrincipal.getRole() == UserRole.SCHOOL_STUDENT) {
            UUID currentStudentUid = UUID.fromString(userPrincipal.getUid());
            if (!classStudentRepository.existsByClassroomIdAndStudentUid(id, currentStudentUid)) {
                throw new AccessDeniedException("Báº¡n khÃ´ng pháº£i lÃ  thÃ nh viÃªn cá»§a lá»›p há»c nÃ y!");
            }
        }

        long studentCount = classStudentRepository.countByClassroomIdAndStatus(id, "ACTIVE");
        return mapToResponse(classroom, studentCount);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ClassroomResponse> getSchoolClassrooms(
            String search, Integer gradeLevel, Integer academicYear, Pageable pageable, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        if (schoolId == null) {
            throw new AccessDeniedException("User does not belong to any school tenant");
        }
        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<Classroom> page;
        if (trimmedSearch != null) {
            page = classroomRepository.findBySchoolIdAndDeletedAtIsNullAndClassNameContainingIgnoreCaseOrSchoolIdAndDeletedAtIsNullAndClassCodeContainingIgnoreCase(
                    schoolId, trimmedSearch, schoolId, trimmedSearch, pageable);
        } else if (gradeLevel != null && academicYear != null) {
            page = classroomRepository.findBySchoolIdAndGradeLevelAndAcademicYearAndDeletedAtIsNull(
                    schoolId, gradeLevel, academicYear, pageable);
        } else if (gradeLevel != null) {
            page = classroomRepository.findBySchoolIdAndGradeLevelAndDeletedAtIsNull(
                    schoolId, gradeLevel, pageable);
        } else if (academicYear != null) {
            page = classroomRepository.findBySchoolIdAndAcademicYearAndDeletedAtIsNull(
                    schoolId, academicYear, pageable);
        } else {
            page = classroomRepository.findBySchoolIdAndDeletedAtIsNull(schoolId, pageable);
        }

        List<ClassroomResponse> content = mapClassroomsBatch(page.getContent());

        return PaginatedResponse.<ClassroomResponse>builder()
                .content(content)
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ClassroomResponse> getTeacherClassrooms(
            Integer academicYear, Pageable pageable, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        if (schoolId == null) {
            throw new AccessDeniedException("User does not belong to any school tenant");
        }
        UUID teacherUid = UUID.fromString(userPrincipal.getUid());

        Page<Classroom> page;
        if (academicYear != null) {
            page = classroomRepository.findBySchoolIdAndTeacher_UidAndAcademicYearAndDeletedAtIsNull(
                    schoolId, teacherUid, academicYear, pageable);
        } else {
            page = classroomRepository.findBySchoolIdAndTeacher_UidAndDeletedAtIsNull(
                    schoolId, teacherUid, pageable);
        }

        List<ClassroomResponse> content = mapClassroomsBatch(page.getContent());

        return PaginatedResponse.<ClassroomResponse>builder()
                .content(content)
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomResponse> getStudentClassrooms(UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID studentUid = UUID.fromString(userPrincipal.getUid());
        List<Classroom> classrooms = classStudentRepository.findClassroomsByStudentUid(studentUid);

        return mapClassroomsBatch(classrooms);
    }

    private List<ClassroomResponse> mapClassroomsBatch(List<Classroom> classrooms) {
        if (classrooms.isEmpty()) {
            return List.of();
        }
        List<UUID> classroomIds = classrooms.stream().map(Classroom::getId).toList();
        java.util.Map<UUID, Long> countMap = classStudentRepository
                .countByClassroomIdsAndStatus(classroomIds, "ACTIVE")
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).longValue()));
        return classrooms.stream()
                .map(c -> mapToResponse(c, countMap.getOrDefault(c.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteClassroom(UUID id, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        if (schoolId == null) {
            throw new AccessDeniedException("User does not belong to any school tenant");
        }
        Classroom classroom = findClassroomInTenant(id, schoolId);

        // Chá»‰ School Admin má»›i Ä‘Æ°á»£c xÃ³a lá»›p há»c
        if (userPrincipal.getRole() != UserRole.SCHOOL_ADMIN) {
            throw new AccessDeniedException("Chá»‰ School Admin má»›i cÃ³ quyá»n xÃ³a lá»›p há»c!");
        }

        classroom.setDeletedAt(LocalDateTime.now());
        classroom.setStatus("DELETED");
        classroomRepository.save(classroom);
        log.info("Soft-deleted classroom {} by School Admin {}", classroom.getClassCode(), userPrincipal.getUid());
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) {
            return principal;
        }
        String userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Vui lÃ²ng Ä‘Äƒng nháº­p Ä‘á»ƒ thá»±c hiá»‡n thao tÃ¡c");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin tÃ i khoáº£n ngÆ°á»i dÃ¹ng"));
        return new UserPrincipal(user);
    }

    private Classroom findClassroomInTenant(UUID id, UUID schoolId) {
        return classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with ID: " + id));
    }

    private User validateAndGetTeacher(UUID teacherId, UUID schoolId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with ID: " + teacherId));

        if (teacher.getRole() != UserRole.TEACHER) {
            throw new InvalidRequestException("User " + teacher.getUserName() + " is not a Teacher");
        }

        if (teacher.getSchool() == null || !schoolId.equals(teacher.getSchool().getId())) {
            throw new InvalidRequestException("Teacher does not belong to the school");
        }

        return teacher;
    }

    private ClassroomResponse mapToResponse(Classroom classroom, Long studentCount) {
        TeacherSummaryDto teacherSummary = null;
        if (classroom.getTeacher() != null) {
            User t = classroom.getTeacher();
            teacherSummary = TeacherSummaryDto.builder()
                    .uid(t.getUid())
                    .fullName(t.getFullName())
                    .email(t.getEmail())
                    .phoneNumber(t.getPhoneNumber())
                    .build();
        }

        Integer dailyTokenQuota = classroom.getDailyTokenQuota();
        if (dailyTokenQuota == null && classroom.getSchool() != null) {
            dailyTokenQuota = classroom.getSchool().getDefaultDailyStudentQuota();
        }

        return ClassroomResponse.builder()
                .id(classroom.getId())
                .schoolId(classroom.getSchool() != null ? classroom.getSchool().getId() : null)
                .schoolName(classroom.getSchool() != null ? classroom.getSchool().getName() : null)
                .schoolCode(classroom.getSchool() != null ? classroom.getSchool().getSchoolCode() : null)
                .className(classroom.getClassName())
                .classCode(classroom.getClassCode())
                .gradeLevel(classroom.getGradeLevel())
                .academicYear(classroom.getAcademicYear())
                .description(classroom.getDescription())
                .dailyTokenQuota(dailyTokenQuota)
                .status(classroom.getStatus())
                .teacher(teacherSummary)
                .studentCount(studentCount != null ? studentCount : 0L)
                .createdAt(classroom.getCreatedAt())
                .updatedAt(classroom.getUpdatedAt())
                .build();
    }
}
