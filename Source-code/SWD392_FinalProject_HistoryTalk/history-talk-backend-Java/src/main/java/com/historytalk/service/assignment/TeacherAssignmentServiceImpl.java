package com.historytalk.service.assignment;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.assignment.*;
import com.historytalk.entity.assignment.Assignment;
import com.historytalk.entity.assignment.AssignmentSubmission;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.AssignmentStatus;
import com.historytalk.entity.enums.AssignmentType;
import com.historytalk.entity.enums.SubmissionStatus;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.assignment.AssignmentRepository;
import com.historytalk.repository.assignment.AssignmentSubmissionRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.classroom.ClassroomRepository;
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
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherAssignmentServiceImpl implements TeacherAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final HistoricalContextRepository historicalContextRepository;
    private final CharacterRepository characterRepository;
    private final QuizRepository quizRepository;

    @Override
    @Transactional
    public TeacherAssignmentResponse createAssignment(CreateAssignmentRequest request, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        if (schoolId == null) {
            throw new AccessDeniedException("Tài khoản không thuộc bất kỳ trường học nào");
        }
        UUID teacherUid = UUID.fromString(userPrincipal.getUid());

        // 1. Validate Teacher sở hữu lớp học
        Classroom classroom = validateAndGetClassroomForTeacher(request.getClassId(), schoolId, teacherUid);

        // 2. Validate Target ID theo loại bài tập & lấy Title
        String targetTitle = validateAndGetTargetTitle(request.getAssignmentType(), request.getTargetId(), schoolId);

        // 3. Validate Deadline
        if (!request.getDeadline().isAfter(LocalDateTime.now())) {
            throw new InvalidRequestException("Hạn nộp bài phải ở thời điểm tương lai");
        }

        int minRequirement = request.getMinRequirement() != null && request.getMinRequirement() > 0
                ? request.getMinRequirement()
                : switch (request.getAssignmentType()) {
                    case AI_CHAT_ROLEPLAY -> 5;
                    case QUIZ_TEST -> 1;
                    case CONTEXT_READING -> 180;
                };

        User teacher = userRepository.findById(teacherUid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Giáo viên"));

        // 4. Lưu Assignment
        Assignment assignment = Assignment.builder()
                .classroom(classroom)
                .teacher(teacher)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .assignmentType(request.getAssignmentType())
                .targetId(request.getTargetId())
                .deadline(request.getDeadline())
                .minRequirement(minRequirement)
                .status(AssignmentStatus.ACTIVE)
                .build();

        Assignment savedAssignment = assignmentRepository.save(assignment);

        // 5. Eager Initialization: Khởi tạo Submission NOT_STARTED cho toàn bộ học sinh ACTIVE trong lớp
        List<UUID> activeStudentUids = classStudentRepository.findEnrolledStudentUids(classroom.getId());
        List<User> students = userRepository.findAllById(activeStudentUids);

        List<AssignmentSubmission> submissions = new ArrayList<>();
        for (User student : students) {
            submissions.add(AssignmentSubmission.builder()
                    .assignment(savedAssignment)
                    .student(student)
                    .status(SubmissionStatus.NOT_STARTED)
                    .build());
        }

        if (!submissions.isEmpty()) {
            assignmentSubmissionRepository.saveAll(submissions);
        }

        log.info("Created assignment {} for classroom {} with {} eager submissions",
                savedAssignment.getId(), classroom.getClassCode(), submissions.size());

        return TeacherAssignmentResponse.builder()
                .assignmentId(savedAssignment.getId())
                .classroomId(classroom.getId())
                .className(classroom.getClassName())
                .title(savedAssignment.getTitle())
                .description(savedAssignment.getDescription())
                .assignmentType(savedAssignment.getAssignmentType())
                .targetId(savedAssignment.getTargetId())
                .targetTitle(targetTitle)
                .deadline(savedAssignment.getDeadline())
                .minRequirement(savedAssignment.getMinRequirement())
                .status(savedAssignment.getStatus())
                .totalStudents(submissions.size())
                .submittedCount(0)
                .lateSubmittedCount(0)
                .inProgressCount(0)
                .notStartedCount(submissions.size())
                .createdAt(savedAssignment.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<TeacherAssignmentResponse> getAssignmentsByClassroom(
            UUID classroomId, AssignmentStatus status, Pageable pageable, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        UUID teacherUid = UUID.fromString(userPrincipal.getUid());

        Classroom classroom = validateAndGetClassroomForTeacher(classroomId, schoolId, teacherUid);

        Page<Assignment> page = assignmentRepository.findAssignmentsByClassroomAndOptionalStatus(
                classroomId, status, pageable);

        List<TeacherAssignmentResponse> content = page.getContent().stream()
                .map(a -> mapToTeacherAssignmentResponse(a, classroom.getClassName()))
                .toList();

        return PaginatedResponse.<TeacherAssignmentResponse>builder()
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
    public AssignmentGradebookResponse getAssignmentGradebook(UUID assignmentId, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        UUID teacherUid = UUID.fromString(userPrincipal.getUid());

        Assignment assignment = assignmentRepository.findByIdAndDeletedAtIsNull(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài tập không tồn tại hoặc đã bị xóa"));

        Classroom classroom = validateAndGetClassroomForTeacher(assignment.getClassroom().getId(), schoolId, teacherUid);
        String targetTitle = getTargetTitleSafely(assignment.getAssignmentType(), assignment.getTargetId());

        List<AssignmentSubmission> submissions = assignmentSubmissionRepository.findByAssignmentId(assignmentId);

        List<SubmissionItemResponse> submissionItems = submissions.stream()
                .map(sub -> SubmissionItemResponse.builder()
                        .submissionId(sub.getId())
                        .studentId(sub.getStudent().getUid())
                        .studentCode(sub.getStudent().getStudentCode())
                        .fullName(sub.getStudent().getFullName())
                        .email(sub.getStudent().getEmail())
                        .status(sub.getStatus())
                        .score(sub.getScore())
                        .messageCount(sub.getMessageCount())
                        .readingDurationSeconds(sub.getReadingDurationSeconds())
                        .startedAt(sub.getStartedAt())
                        .submittedAt(sub.getSubmittedAt())
                        .build())
                .toList();

        int submittedCount = (int) submissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED || s.getStatus() == SubmissionStatus.LATE_SUBMITTED)
                .count();

        return AssignmentGradebookResponse.builder()
                .assignmentId(assignment.getId())
                .classroomId(classroom.getId())
                .className(classroom.getClassName())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .assignmentType(assignment.getAssignmentType())
                .targetId(assignment.getTargetId())
                .targetTitle(targetTitle)
                .deadline(assignment.getDeadline())
                .minRequirement(assignment.getMinRequirement())
                .status(assignment.getStatus())
                .totalAssignedStudents(submissions.size())
                .totalSubmittedStudents(submittedCount)
                .submissions(submissionItems)
                .build();
    }

    @Override
    @Transactional
    public TeacherAssignmentResponse updateAssignment(UUID assignmentId, UpdateAssignmentRequest request, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        UUID teacherUid = UUID.fromString(userPrincipal.getUid());

        Assignment assignment = assignmentRepository.findByIdAndDeletedAtIsNull(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài tập không tồn tại hoặc đã bị xóa"));

        validateAndGetClassroomForTeacher(assignment.getClassroom().getId(), schoolId, teacherUid);

        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            assignment.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            assignment.setDescription(request.getDescription());
        }
        if (request.getMinRequirement() != null && request.getMinRequirement() > 0) {
            assignment.setMinRequirement(request.getMinRequirement());
        }
        if (request.getStatus() != null) {
            assignment.setStatus(request.getStatus());
        }

        // Gia hạn Deadline & Auto-Revert LATE_SUBMITTED sang SUBMITTED
        if (request.getDeadline() != null) {
            LocalDateTime oldDeadline = assignment.getDeadline();
            LocalDateTime newDeadline = request.getDeadline();
            if (!newDeadline.isAfter(LocalDateTime.now())) {
                throw new InvalidRequestException("Hạn nộp mới phải ở tương lai");
            }
            assignment.setDeadline(newDeadline);

            if (newDeadline.isAfter(oldDeadline)) {
                List<AssignmentSubmission> lateSubmissions = assignmentSubmissionRepository.findByAssignmentIdAndStatus(
                        assignment.getId(), SubmissionStatus.LATE_SUBMITTED);
                for (AssignmentSubmission sub : lateSubmissions) {
                    if (sub.getSubmittedAt() != null && !sub.getSubmittedAt().isAfter(newDeadline)) {
                        sub.setStatus(SubmissionStatus.SUBMITTED);
                        assignmentSubmissionRepository.save(sub);
                    }
                }
            }
        }

        Assignment updated = assignmentRepository.save(assignment);
        return mapToTeacherAssignmentResponse(updated, updated.getClassroom().getClassName());
    }

    @Override
    @Transactional
    public void cancelAssignment(UUID assignmentId, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        UUID teacherUid = UUID.fromString(userPrincipal.getUid());

        Assignment assignment = assignmentRepository.findByIdAndDeletedAtIsNull(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài tập không tồn tại hoặc đã bị xóa"));

        validateAndGetClassroomForTeacher(assignment.getClassroom().getId(), schoolId, teacherUid);

        // Soft delete / Cancel
        assignment.setStatus(AssignmentStatus.CANCELLED);
        assignment.setDeletedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
        log.info("Teacher {} cancelled assignment {}", teacherUid, assignmentId);
    }

    // Helper methods
    private Classroom validateAndGetClassroomForTeacher(UUID classroomId, UUID schoolId, UUID teacherUid) {
        Classroom classroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Lớp học không tồn tại hoặc đã bị xóa"));

        if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(teacherUid)) {
            throw new AccessDeniedException("Bạn không được phân công phụ trách lớp học này!");
        }
        return classroom;
    }

    private String validateAndGetTargetTitle(AssignmentType type, UUID targetId, UUID schoolId) {
        switch (type) {
            case CONTEXT_READING -> {
                HistoricalContext context = historicalContextRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Bối cảnh lịch sử không tồn tại với ID: " + targetId));
                if (context.getSchool() != null) {
                    if (!context.getSchool().getId().equals(schoolId)) {
                        throw new AccessDeniedException("Bối cảnh địa phương này không thuộc trường của bạn");
                    }
                    if (!Boolean.TRUE.equals(context.getIsPublished())) {
                        throw new InvalidRequestException("Bối cảnh địa phương chưa được School Admin phê duyệt");
                    }
                }
                return context.getName();
            }
            case AI_CHAT_ROLEPLAY -> {
                Character character = characterRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Nhân vật lịch sử không tồn tại với ID: " + targetId));
                if (character.getSchool() != null) {
                    if (!character.getSchool().getId().equals(schoolId)) {
                        throw new AccessDeniedException("Nhân vật địa phương này không thuộc trường của bạn");
                    }
                    if (!Boolean.TRUE.equals(character.getIsPublished())) {
                        throw new InvalidRequestException("Nhân vật địa phương chưa được School Admin phê duyệt");
                    }
                }
                return character.getName();
            }
            case QUIZ_TEST -> {
                Quiz quiz = quizRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Bộ câu hỏi Quiz không tồn tại với ID: " + targetId));
                return quiz.getTitle();
            }
            default -> throw new InvalidRequestException("Loại bài tập không hợp lệ: " + type);
        }
    }

    private String getTargetTitleSafely(AssignmentType type, UUID targetId) {
        try {
            switch (type) {
                case CONTEXT_READING -> {
                    return historicalContextRepository.findById(targetId).map(HistoricalContext::getName).orElse("Unknown Context");
                }
                case AI_CHAT_ROLEPLAY -> {
                    return characterRepository.findById(targetId).map(Character::getName).orElse("Unknown Character");
                }
                case QUIZ_TEST -> {
                    return quizRepository.findById(targetId).map(Quiz::getTitle).orElse("Unknown Quiz");
                }
            }
        } catch (Exception e) {
            log.warn("Could not resolve target title for type {} id {}", type, targetId, e);
        }
        return "Nội dung bài tập";
    }

    private TeacherAssignmentResponse mapToTeacherAssignmentResponse(Assignment a, String className) {
        long total = assignmentSubmissionRepository.countByAssignmentId(a.getId());
        long submitted = assignmentSubmissionRepository.countByAssignmentIdAndStatus(a.getId(), SubmissionStatus.SUBMITTED);
        long late = assignmentSubmissionRepository.countByAssignmentIdAndStatus(a.getId(), SubmissionStatus.LATE_SUBMITTED);
        long inProgress = assignmentSubmissionRepository.countByAssignmentIdAndStatus(a.getId(), SubmissionStatus.IN_PROGRESS);
        long notStarted = assignmentSubmissionRepository.countByAssignmentIdAndStatus(a.getId(), SubmissionStatus.NOT_STARTED);

        return TeacherAssignmentResponse.builder()
                .assignmentId(a.getId())
                .classroomId(a.getClassroom().getId())
                .className(className)
                .title(a.getTitle())
                .description(a.getDescription())
                .assignmentType(a.getAssignmentType())
                .targetId(a.getTargetId())
                .targetTitle(getTargetTitleSafely(a.getAssignmentType(), a.getTargetId()))
                .deadline(a.getDeadline())
                .minRequirement(a.getMinRequirement())
                .status(a.getStatus())
                .totalStudents(total)
                .submittedCount(submitted)
                .lateSubmittedCount(late)
                .inProgressCount(inProgress)
                .notStartedCount(notStarted)
                .createdAt(a.getCreatedAt())
                .build();
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null && principal.getSchoolId() != null) {
            return principal;
        }
        String userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new AccessDeniedException("Chưa xác thực hoặc token không hợp lệ");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy thông tin tài khoản người dùng"));
        return new UserPrincipal(user);
    }
}
