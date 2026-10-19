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

        // 1. Kiểm tra chống trùng tên lớp trong cùng trường và năm học
        if (classroomRepository.existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndDeletedAtIsNull(
                schoolId, trimmedClassName, request.getAcademicYear())) {
            throw new DataConflictException(String.format(
                    "Lớp học \"%s\" đã tồn tại trong năm học %d của trường",
                    trimmedClassName, request.getAcademicYear()));
        }

        // 2. Xác định Giáo viên phụ trách
        User teacher = null;
        if (userPrincipal.getRole() == UserRole.TEACHER) {
            // Nếu Teacher tự tạo lớp thì mặc định gán cho chính mình
            teacher = userRepository.findById(UUID.fromString(userPrincipal.getUid()))
                    .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));
        } else if (userPrincipal.getRole() == UserRole.SCHOOL_ADMIN) {
            if (request.getTeacherId() != null) {
                teacher = validateAndGetTeacher(request.getTeacherId(), schoolId);
            }
        }

        // 3. Sinh mã lớp học thông minh (kèm mã trường và năm học)
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

        // Kiểm tra quyền hạn
        if (userPrincipal.getRole() == UserRole.TEACHER) {
            UUID currentTeacherUid = UUID.fromString(userPrincipal.getUid());
            if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(currentTeacherUid)) {
                throw new AccessDeniedException("Bạn không được phân công phụ trách lớp học này!");
            }
            if (request.getTeacherId() != null && !request.getTeacherId().equals(currentTeacherUid)) {
                throw new AccessDeniedException("Giáo viên không có quyền chuyển nhượng quyền phụ trách lớp cho người khác!");
            }
        }

        // Cập nhật tên lớp / năm học nếu có
        String newClassName = request.getClassName() != null ? request.getClassName().trim() : classroom.getClassName();
        Integer newAcademicYear = request.getAcademicYear() != null ? request.getAcademicYear() : classroom.getAcademicYear();

        if (!newClassName.equalsIgnoreCase(classroom.getClassName()) || !newAcademicYear.equals(classroom.getAcademicYear())) {
            if (classroomRepository.existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndIdNotAndDeletedAtIsNull(
                    schoolId, newClassName, newAcademicYear, id)) {
                throw new DataConflictException(String.format(
                        "Lớp học \"%s\" đã tồn tại trong năm học %d của trường",
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

        if (request.getStatus() != null) {
            classroom.setStatus(request.getStatus().trim());
        }

        // Cập nhật giáo viên (chỉ School Admin mới được đổi)
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

        // Kiểm tra phân quyền truy cập chi tiết lớp
        if (userPrincipal.getRole() == UserRole.TEACHER) {
            UUID currentTeacherUid = UUID.fromString(userPrincipal.getUid());
            if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(currentTeacherUid)) {
                throw new AccessDeniedException("Bạn không được phân công phụ trách lớp học này!");
            }
        } else if (userPrincipal.getRole() == UserRole.SCHOOL_STUDENT) {
            UUID currentStudentUid = UUID.fromString(userPrincipal.getUid());
            if (!classStudentRepository.existsByClassroomIdAndStudentUid(id, currentStudentUid)) {
                throw new AccessDeniedException("Bạn không phải là thành viên của lớp học này!");
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

        List<ClassroomResponse> content = page.getContent().stream()
                .map(c -> mapToResponse(c, classStudentRepository.countByClassroomIdAndStatus(c.getId(), "ACTIVE")))
                .collect(Collectors.toList());

        return PaginatedResponse.<ClassroomResponse>builder()
                .content(content)
                .currentPage(page.getNumber())
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

        List<ClassroomResponse> content = page.getContent().stream()
                .map(c -> mapToResponse(c, classStudentRepository.countByClassroomIdAndStatus(c.getId(), "ACTIVE")))
                .collect(Collectors.toList());

        return PaginatedResponse.<ClassroomResponse>builder()
                .content(content)
                .currentPage(page.getNumber())
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

        return classrooms.stream()
                .map(c -> mapToResponse(c, classStudentRepository.countByClassroomIdAndStatus(c.getId(), "ACTIVE")))
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

        // Chỉ School Admin mới được xóa lớp học
        if (userPrincipal.getRole() != UserRole.SCHOOL_ADMIN) {
            throw new AccessDeniedException("Chỉ School Admin mới có quyền xóa lớp học!");
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
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
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
                .status(classroom.getStatus())
                .teacher(teacherSummary)
                .studentCount(studentCount != null ? studentCount : 0L)
                .createdAt(classroom.getCreatedAt())
                .updatedAt(classroom.getUpdatedAt())
                .build();
    }
}
