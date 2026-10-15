package com.historytalk.service.school.student;

import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.ImportScope;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import org.springframework.security.access.AccessDeniedException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StudentImportServiceImpl implements StudentImportService {

    private final Map<ImportScope, StudentImportProcessor> processorMap;
    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;

    @Value("${historytalk.saas.student.default-initial-token:10000}")
    private Integer configuredDefaultToken;

    public StudentImportServiceImpl(
            List<StudentImportProcessor> processors,
            UserRepository userRepository,
            ClassroomRepository classroomRepository) {
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.processorMap = processors.stream()
                .collect(Collectors.toMap(StudentImportProcessor::getScope, Function.identity()));
        log.info("Initialized StudentImportService with scopes: {}", processorMap.keySet());
    }

    @Override
    @Transactional
    public StudentImportResultDto importStudents(ImportScope scope, MultipartFile file, StudentImportContext context) {
        StudentImportProcessor processor = processorMap.get(scope);
        if (processor == null) {
            throw new UnsupportedOperationException("No import processor registered for scope: " + scope);
        }
        return processor.processImport(file, context);
    }

    @Override
    @Transactional
    public StudentImportResultDto importSchoolWide(
            MultipartFile file,
            Integer defaultInitialToken,
            UserPrincipal principal) {
        StudentImportContext context = buildBaseContext(defaultInitialToken, principal);
        return importStudents(ImportScope.SCHOOL_WIDE, file, context);
    }

    @Override
    @Transactional
    public StudentImportResultDto importToClassroom(
            UUID classroomId,
            MultipartFile file,
            Integer defaultInitialToken,
            UserPrincipal principal) {
        UUID schoolId = resolveSchoolId(principal);
        validateClassroomAccess(classroomId, schoolId, principal);

        StudentImportContext context = buildBaseContext(defaultInitialToken, principal);
        context.setClassroomId(classroomId);

        return importStudents(ImportScope.CLASSROOM, file, context);
    }

    private StudentImportContext buildBaseContext(Integer defaultInitialToken, UserPrincipal principal) {
        int effectiveDefaultToken = (defaultInitialToken != null && defaultInitialToken >= 0)
                ? defaultInitialToken
                : configuredDefaultToken;

        UUID schoolId;
        String schoolCode;
        UUID operatorId;
        UserRole operatorRole;

        if (principal != null && principal.getSchoolId() != null) {
            schoolId = principal.getSchoolId();
            schoolCode = principal.getSchoolCode();
            operatorId = UUID.fromString(principal.getUid());
            operatorRole = principal.getRole();
        } else {
            User schoolAdmin = resolveSchoolAdmin(principal);
            schoolId = schoolAdmin.getSchool().getId();
            schoolCode = schoolAdmin.getSchool().getSchoolCode();
            operatorId = schoolAdmin.getUid();
            operatorRole = schoolAdmin.getRole();
        }

        return StudentImportContext.builder()
                .schoolId(schoolId)
                .schoolCode(schoolCode)
                .operatorId(operatorId)
                .operatorRole(operatorRole)
                .defaultInitialToken(effectiveDefaultToken)
                .build();
    }

    private UUID resolveSchoolId(UserPrincipal principal) {
        if (principal != null && principal.getSchoolId() != null) {
            return principal.getSchoolId();
        }
        return resolveSchoolAdmin(principal).getSchool().getId();
    }

    private Classroom validateClassroomAccess(UUID classroomId, UUID schoolId, UserPrincipal principal) {
        Classroom classroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with ID: " + classroomId));

        if (principal != null && principal.getRole() == UserRole.TEACHER) {
            UUID currentTeacherUid = UUID.fromString(principal.getUid());
            if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(currentTeacherUid)) {
                throw new AccessDeniedException("Bạn không được phân công phụ trách lớp học này!");
            }
        }

        return classroom;
    }

    private User resolveSchoolAdmin(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập với tài khoản Quản trị viên Trường");
        }
        User user = userRepository.findById(UUID.fromString(principal.getUid()))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
        if (user.getSchool() == null) {
            throw new InvalidRequestException("Tài khoản chưa được liên kết với bất kỳ trường học nào");
        }
        return user;
    }
}
