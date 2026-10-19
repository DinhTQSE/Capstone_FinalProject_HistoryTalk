package com.historytalk.service.classroom;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.BatchEnrollResultResponse;
import com.historytalk.dto.classroom.BatchEnrollStudentRequest;
import com.historytalk.dto.classroom.ClassroomStudentResponse;
import com.historytalk.dto.classroom.TransferStudentRequest;
import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.entity.classroom.ClassStudent;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.student.StudentImportService;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomStudentServiceImpl implements ClassroomStudentService {

    private final ClassroomRepository classroomRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final StudentImportService studentImportService;

    @Override
    @Transactional
    public BatchEnrollResultResponse enrollStudentsBatch(UUID classroomId, BatchEnrollStudentRequest request, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = resolveSchoolId(userPrincipal);
        Classroom classroom = validateAndGetClassroom(classroomId, schoolId, userPrincipal);

        int totalRequested = request.getStudentIds().size();
        int successCount = 0;
        int alreadyEnrolledCount = 0;
        List<String> notes = new ArrayList<>();

        for (UUID studentId : request.getStudentIds()) {
            User student = userRepository.findById(studentId)
                    .orElse(null);

            if (student == null || student.getDeletedAt() != null) {
                notes.add(String.format("Học sinh ID %s không tồn tại hoặc đã bị xóa", studentId));
                continue;
            }

            if (student.getSchool() == null || !schoolId.equals(student.getSchool().getId())) {
                notes.add(String.format("Học sinh %s không thuộc trường của lớp học này", student.getFullName()));
                continue;
            }

            if (student.getRole() != UserRole.SCHOOL_STUDENT) {
                notes.add(String.format("Người dùng %s không phải là Học sinh", student.getFullName()));
                continue;
            }

            Optional<ClassStudent> existingOpt = classStudentRepository.findByClassroomIdAndStudentUid(classroomId, studentId);
            if (existingOpt.isPresent() && "ACTIVE".equals(existingOpt.get().getStatus())) {
                alreadyEnrolledCount++;
                notes.add(String.format("Học sinh %s (%s) đã có trong lớp", student.getFullName(), student.getStudentCode()));
                continue;
            }

            List<Classroom> activeClassrooms = classStudentRepository.findActiveClassroomsByStudentUid(studentId);
            Optional<Classroom> otherActiveClassOpt = activeClassrooms.stream()
                    .filter(c -> !c.getId().equals(classroomId))
                    .findFirst();
            if (otherActiveClassOpt.isPresent()) {
                Classroom otherClass = otherActiveClassOpt.get();
                alreadyEnrolledCount++;
                notes.add(String.format(
                        "Học sinh %s (%s) đang học ở lớp %s (mỗi học sinh chỉ được ở 1 lớp đang hoạt động, vui lòng dùng tính năng Chuyển lớp)",
                        student.getFullName(), student.getStudentCode(), otherClass.getClassName()));
                continue;
            }

            if (existingOpt.isPresent()) {
                ClassStudent existing = existingOpt.get();
                existing.setStatus("ACTIVE");
                classStudentRepository.save(existing);
                successCount++;
                continue;
            }

            ClassStudent classStudent = ClassStudent.builder()
                    .classroom(classroom)
                    .student(student)
                    .status("ACTIVE")
                    .build();

            classStudentRepository.save(classStudent);
            successCount++;
        }

        long currentCount = classStudentRepository.countByClassroomIdAndStatus(classroomId, "ACTIVE");

        log.info("Batch enrolled students to class {}: requested={}, success={}, alreadyEnrolled={}",
                classroom.getClassCode(), totalRequested, successCount, alreadyEnrolledCount);

        return BatchEnrollResultResponse.builder()
                .classroomId(classroom.getId())
                .className(classroom.getClassName())
                .totalRequested(totalRequested)
                .successCount(successCount)
                .alreadyEnrolledCount(alreadyEnrolledCount)
                .currentStudentCount(currentCount)
                .notes(notes)
                .build();
    }

    @Override
    @Transactional
    public void removeStudentFromClass(UUID classroomId, UUID studentId, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = resolveSchoolId(userPrincipal);
        validateAndGetClassroom(classroomId, schoolId, userPrincipal);

        ClassStudent classStudent = classStudentRepository.findByClassroomIdAndStudentUid(classroomId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Học sinh không có trong lớp học này"));

        classStudent.setStatus("INACTIVE");
        classStudentRepository.save(classStudent);
        log.info("Soft removed student {} from classroom {}", studentId, classroomId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ClassroomStudentResponse> getClassroomStudents(
            UUID classroomId, String search, Pageable pageable, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = resolveSchoolId(userPrincipal);
        validateAndGetClassroom(classroomId, schoolId, userPrincipal);

        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<ClassStudent> page;
        if (trimmedSearch != null) {
            page = classStudentRepository.searchStudentsInClassroom(
                    classroomId, schoolId, trimmedSearch, pageable);
        } else {
            page = classStudentRepository.findStudentsInClassroom(
                    classroomId, schoolId, pageable);
        }

        List<ClassroomStudentResponse> content = page.getContent().stream()
                .map(this::mapToClassroomStudentResponse)
                .toList();

        return PaginatedResponse.<ClassroomStudentResponse>builder()
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
    public PaginatedResponse<StudentResponse> getAvailableStudents(
            UUID classroomId, String search, Pageable pageable, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = resolveSchoolId(userPrincipal);
        validateAndGetClassroom(classroomId, schoolId, userPrincipal);

        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<User> page;
        if (trimmedSearch != null) {
            page = userRepository.searchAvailableStudentsForClassroom(
                    schoolId, classroomId, trimmedSearch, pageable);
        } else {
            page = userRepository.findAvailableStudentsForClassroom(
                    schoolId, classroomId, pageable);
        }

        List<StudentResponse> content = page.getContent().stream()
                .map(this::mapToStudentResponse)
                .toList();

        return PaginatedResponse.<StudentResponse>builder()
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
    @Transactional
    public BatchEnrollResultResponse transferStudents(UUID fromClassroomId, TransferStudentRequest request, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = resolveSchoolId(userPrincipal);
        Classroom fromClass = validateAndGetClassroom(fromClassroomId, schoolId, userPrincipal);

        if (fromClassroomId.equals(request.getTargetClassroomId())) {
            throw new InvalidRequestException("Lớp học đích không được trùng với lớp học hiện tại");
        }

        Classroom targetClass = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(request.getTargetClassroomId(), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Target classroom not found with ID: " + request.getTargetClassroomId()));

        int successCount = 0;
        List<String> notes = new ArrayList<>();

        for (UUID studentId : request.getStudentIds()) {
            ClassStudent enrollment = classStudentRepository.findByClassroomIdAndStudentUid(fromClassroomId, studentId)
                    .orElse(null);

            if (enrollment == null) {
                notes.add(String.format("Học sinh ID %s không có trong lớp %s", studentId, fromClass.getClassName()));
                continue;
            }

            // Mark transferred in source class
            enrollment.setStatus("TRANSFERRED");
            classStudentRepository.save(enrollment);

            // Add to target class or reactivate if exists
            Optional<ClassStudent> existingInTarget = classStudentRepository.findByClassroomIdAndStudentUid(targetClass.getId(), studentId);
            if (existingInTarget.isPresent()) {
                ClassStudent csTarget = existingInTarget.get();
                csTarget.setStatus("ACTIVE");
                classStudentRepository.save(csTarget);
            } else {
                classStudentRepository.save(ClassStudent.builder()
                        .classroom(targetClass)
                        .student(enrollment.getStudent())
                        .status("ACTIVE")
                        .build());
            }

            successCount++;
        }

        long targetCount = classStudentRepository.countByClassroomIdAndStatus(targetClass.getId(), "ACTIVE");

        log.info("Transferred {} students from class {} to class {}",
                successCount, fromClass.getClassCode(), targetClass.getClassCode());

        return BatchEnrollResultResponse.builder()
                .classroomId(targetClass.getId())
                .className(targetClass.getClassName())
                .totalRequested(request.getStudentIds().size())
                .successCount(successCount)
                .alreadyEnrolledCount(0)
                .currentStudentCount(targetCount)
                .notes(notes)
                .build();
    }

    @Override
    @Transactional
    public StudentImportResultDto importClassroomStudents(
            UUID classroomId, MultipartFile file, Integer defaultInitialToken, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        return studentImportService.importToClassroom(classroomId, file, defaultInitialToken, userPrincipal);
    }

    private Classroom validateAndGetClassroom(UUID classroomId, UUID schoolId, UserPrincipal principal) {
        Classroom classroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with ID: " + classroomId));

        if (principal.getRole() == UserRole.TEACHER) {
            UUID currentTeacherUid = UUID.fromString(principal.getUid());
            if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(currentTeacherUid)) {
                throw new AccessDeniedException("Bạn không được phân công phụ trách lớp học này!");
            }
        }

        return classroom;
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
        throw new AccessDeniedException("User does not belong to any school tenant");
    }

    private ClassroomStudentResponse mapToClassroomStudentResponse(ClassStudent cs) {
        User u = cs.getStudent();
        return ClassroomStudentResponse.builder()
                .studentId(u.getUid())
                .studentCode(u.getStudentCode())
                .userName(u.getUserName())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .dob(u.getDob())
                .gender(u.getGender())
                .phoneNumber(u.getPhoneNumber())
                .token(u.getToken())
                .joinedAt(cs.getJoinedAt())
                .status(cs.getStatus())
                .build();
    }

    private StudentResponse mapToStudentResponse(User user) {
        return StudentResponse.builder()
                .uid(user.getUid())
                .studentCode(user.getStudentCode())
                .userName(user.getUserName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .dob(user.getDob())
                .gender(user.getGender())
                .phoneNumber(user.getPhoneNumber())
                .token(user.getToken())
                .active(user.getDeletedAt() == null)
                .schoolId(user.getSchool() != null ? user.getSchool().getId() : null)
                .schoolName(user.getSchool() != null ? user.getSchool().getName() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
