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
import com.historytalk.repository.assignment.AssignmentSubmissionBatchStatsProjection;
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
import java.util.function.Function;
import java.util.stream.Collectors;

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
            throw new AccessDeniedException("TÃ i khoáº£n khÃ´ng thuá»™c báº¥t ká»³ trÆ°á»ng há»c nÃ o");
        }
        UUID teacherUid = UUID.fromString(userPrincipal.getUid());

        // 1. Validate Teacher sá»Ÿ há»¯u lá»›p há»c
        Classroom classroom = validateAndGetClassroomForTeacher(request.getClassId(), schoolId, teacherUid);

        // 2. Validate Target ID theo loáº¡i bÃ i táº­p & láº¥y Title
        String targetTitle = validateAndGetTargetTitle(request.getAssignmentType(), request.getTargetId(), schoolId);

        // 3. Validate Deadline
        if (!request.getDeadline().isAfter(LocalDateTime.now())) {
            throw new InvalidRequestException("Háº¡n ná»™p bÃ i pháº£i á»Ÿ thá»i Ä‘iá»ƒm tÆ°Æ¡ng lai");
        }

        int minRequirement = request.getMinRequirement() != null && request.getMinRequirement() > 0
                ? request.getMinRequirement()
                : switch (request.getAssignmentType()) {
                    case AI_CHAT_ROLEPLAY -> 5;
                    case QUIZ_TEST -> 1;
                    case CONTEXT_READING -> 180;
                };

        User teacher = userRepository.findById(teacherUid)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin GiÃ¡o viÃªn"));

        // 4. LÆ°u Assignment
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

        // 5. Eager Initialization: Khá»Ÿi táº¡o Submission NOT_STARTED cho toÃ n bá»™ há»c sinh ACTIVE trong lá»›p
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

        List<Assignment> assignments = page.getContent();
        List<TeacherAssignmentResponse> content;

        if (assignments.isEmpty()) {
            content = Collections.emptyList();
        } else {
            List<UUID> assignmentIds = assignments.stream().map(Assignment::getId).toList();

            Map<UUID, AssignmentSubmissionBatchStatsProjection> statsMap = assignmentSubmissionRepository
                    .getSubmissionStatsByAssignmentIds(assignmentIds).stream()
                    .collect(Collectors.toMap(AssignmentSubmissionBatchStatsProjection::getAssignmentId, Function.identity(), (k1, k2) -> k1));

            Set<UUID> contextIds = assignments.stream()
                    .filter(a -> a.getAssignmentType() == AssignmentType.CONTEXT_READING && a.getTargetId() != null)
                    .map(Assignment::getTargetId)
                    .collect(Collectors.toSet());
            Map<UUID, String> contextMap = contextIds.isEmpty() ? Collections.emptyMap() :
                    historicalContextRepository.findAllById(contextIds).stream()
                            .collect(Collectors.toMap(HistoricalContext::getContextId, HistoricalContext::getName, (k1, k2) -> k1));

            Set<UUID> charIds = assignments.stream()
                    .filter(a -> a.getAssignmentType() == AssignmentType.AI_CHAT_ROLEPLAY && a.getTargetId() != null)
                    .map(Assignment::getTargetId)
                    .collect(Collectors.toSet());
            Map<UUID, String> charMap = charIds.isEmpty() ? Collections.emptyMap() :
                    characterRepository.findAllById(charIds).stream()
                            .collect(Collectors.toMap(Character::getCharacterId, Character::getName, (k1, k2) -> k1));

            Set<UUID> quizIds = assignments.stream()
                    .filter(a -> a.getAssignmentType() == AssignmentType.QUIZ_TEST && a.getTargetId() != null)
                    .map(Assignment::getTargetId)
                    .collect(Collectors.toSet());
            Map<UUID, String> quizMap = quizIds.isEmpty() ? Collections.emptyMap() :
                    quizRepository.findAllById(quizIds).stream()
                            .collect(Collectors.toMap(Quiz::getQuizId, Quiz::getTitle, (k1, k2) -> k1));

            content = assignments.stream().map(a -> {
                AssignmentSubmissionBatchStatsProjection stats = statsMap.get(a.getId());
                long total = stats != null && stats.getTotalSubmissions() != null ? stats.getTotalSubmissions() : 0L;
                long submitted = stats != null && stats.getSubmittedCount() != null ? stats.getSubmittedCount() : 0L;
                long late = stats != null && stats.getLateSubmittedCount() != null ? stats.getLateSubmittedCount() : 0L;
                long inProgress = stats != null && stats.getInProgressCount() != null ? stats.getInProgressCount() : 0L;
                long notStarted = stats != null && stats.getNotStartedCount() != null ? stats.getNotStartedCount() : 0L;

                String targetTitle = "Ná»™i dung bÃ i táº­p";
                if (a.getAssignmentType() != null && a.getTargetId() != null) {
                    switch (a.getAssignmentType()) {
                        case CONTEXT_READING -> targetTitle = contextMap.getOrDefault(a.getTargetId(), "Unknown Context");
                        case AI_CHAT_ROLEPLAY -> targetTitle = charMap.getOrDefault(a.getTargetId(), "Unknown Character");
                        case QUIZ_TEST -> targetTitle = quizMap.getOrDefault(a.getTargetId(), "Unknown Quiz");
                    }
                }

                return TeacherAssignmentResponse.builder()
                        .assignmentId(a.getId())
                        .classroomId(a.getClassroom().getId())
                        .className(classroom.getClassName())
                        .title(a.getTitle())
                        .description(a.getDescription())
                        .assignmentType(a.getAssignmentType())
                        .targetId(a.getTargetId())
                        .targetTitle(targetTitle)
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
            }).toList();
        }



        return PaginatedResponse.<TeacherAssignmentResponse>builder()
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
    public AssignmentGradebookResponse getAssignmentGradebook(UUID assignmentId, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID schoolId = userPrincipal.getSchoolId();
        UUID teacherUid = UUID.fromString(userPrincipal.getUid());

        Assignment assignment = assignmentRepository.findByIdAndDeletedAtIsNull(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("BÃ i táº­p khÃ´ng tá»“n táº¡i hoáº·c Ä‘Ã£ bá»‹ xÃ³a"));

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
                .orElseThrow(() -> new ResourceNotFoundException("BÃ i táº­p khÃ´ng tá»“n táº¡i hoáº·c Ä‘Ã£ bá»‹ xÃ³a"));

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
            if (request.getStatus() == AssignmentStatus.ACTIVE && assignment.getAssignmentType() != null) {
                validateAndGetTargetTitle(assignment.getAssignmentType(), assignment.getTargetId(), schoolId);
            }
            assignment.setStatus(request.getStatus());
        }

        // Gia háº¡n Deadline & Auto-Revert LATE_SUBMITTED sang SUBMITTED
        if (request.getDeadline() != null) {
            LocalDateTime oldDeadline = assignment.getDeadline();
            LocalDateTime newDeadline = request.getDeadline();
            if (!newDeadline.isAfter(LocalDateTime.now())) {
                throw new InvalidRequestException("Háº¡n ná»™p má»›i pháº£i á»Ÿ tÆ°Æ¡ng lai");
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
                .orElseThrow(() -> new ResourceNotFoundException("BÃ i táº­p khÃ´ng tá»“n táº¡i hoáº·c Ä‘Ã£ bá»‹ xÃ³a"));

        validateAndGetClassroomForTeacher(assignment.getClassroom().getId(), schoolId, teacherUid);

        // Soft cancel: keep deletedAt null so teacher can still view CANCELLED assignments and preserved gradebook data
        assignment.setStatus(AssignmentStatus.CANCELLED);
        assignmentRepository.save(assignment);
        log.info("Teacher {} cancelled assignment {}", teacherUid, assignmentId);
    }

    // Helper methods
    private Classroom validateAndGetClassroomForTeacher(UUID classroomId, UUID schoolId, UUID teacherUid) {
        Classroom classroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(classroomId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Lá»›p há»c khÃ´ng tá»“n táº¡i hoáº·c Ä‘Ã£ bá»‹ xÃ³a"));

        if (classroom.getTeacher() == null || !classroom.getTeacher().getUid().equals(teacherUid)) {
            throw new AccessDeniedException("Báº¡n khÃ´ng Ä‘Æ°á»£c phÃ¢n cÃ´ng phá»¥ trÃ¡ch lá»›p há»c nÃ y!");
        }
        return classroom;
    }

    private String validateAndGetTargetTitle(AssignmentType type, UUID targetId, UUID schoolId) {
        switch (type) {
            case CONTEXT_READING -> {
                HistoricalContext context = historicalContextRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Bá»‘i cáº£nh lá»‹ch sá»­ khÃ´ng tá»“n táº¡i vá»›i ID: " + targetId));
                if (context.getSchool() != null) {
                    if (!context.getSchool().getId().equals(schoolId)) {
                        throw new AccessDeniedException("Bá»‘i cáº£nh Ä‘á»‹a phÆ°Æ¡ng nÃ y khÃ´ng thuá»™c trÆ°á»ng cá»§a báº¡n");
                    }
                    if (!Boolean.TRUE.equals(context.getIsPublished())) {
                        throw new InvalidRequestException("Bá»‘i cáº£nh Ä‘á»‹a phÆ°Æ¡ng chÆ°a Ä‘Æ°á»£c School Admin phÃª duyá»‡t");
                    }
                }
                return context.getName();
            }
            case AI_CHAT_ROLEPLAY -> {
                Character character = characterRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("NhÃ¢n váº­t lá»‹ch sá»­ khÃ´ng tá»“n táº¡i vá»›i ID: " + targetId));
                if (character.getSchool() != null) {
                    if (!character.getSchool().getId().equals(schoolId)) {
                        throw new AccessDeniedException("NhÃ¢n váº­t Ä‘á»‹a phÆ°Æ¡ng nÃ y khÃ´ng thuá»™c trÆ°á»ng cá»§a báº¡n");
                    }
                    if (!Boolean.TRUE.equals(character.getIsPublished())) {
                        throw new InvalidRequestException("NhÃ¢n váº­t Ä‘á»‹a phÆ°Æ¡ng chÆ°a Ä‘Æ°á»£c School Admin phÃª duyá»‡t");
                    }
                }
                return character.getName();
            }
            case QUIZ_TEST -> {
                Quiz quiz = quizRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Bá»™ cÃ¢u há»i Quiz khÃ´ng tá»“n táº¡i vá»›i ID: " + targetId));
                if (quiz.getDeletedAt() != null) {
                    throw new ResourceNotFoundException("Bá»™ cÃ¢u há»i Quiz Ä‘Ã£ bá»‹ xÃ³a vá»›i ID: " + targetId);
                }
                if (!Boolean.TRUE.equals(quiz.getIsPublished())) {
                    throw new InvalidRequestException("Bá»™ cÃ¢u há»i Quiz chÆ°a Ä‘Æ°á»£c cÃ´ng bá»‘/phÃª duyá»‡t");
                }
                if (quiz.getHistoricalContext() != null && quiz.getHistoricalContext().getSchool() != null) {
                    if (!quiz.getHistoricalContext().getSchool().getId().equals(schoolId)) {
                        throw new AccessDeniedException("Bá»™ cÃ¢u há»i Quiz nÃ y thuá»™c bá»‘i cáº£nh Ä‘á»‹a phÆ°Æ¡ng cá»§a trÆ°á»ng khÃ¡c");
                    }
                } else if (quiz.getCreatedBy() != null && quiz.getCreatedBy().getSchool() != null) {
                    if (!quiz.getCreatedBy().getSchool().getId().equals(schoolId)) {
                        throw new AccessDeniedException("Bá»™ cÃ¢u há»i Quiz nÃ y khÃ´ng thuá»™c trÆ°á»ng cá»§a báº¡n");
                    }
                }
                return quiz.getTitle();
            }
            default -> throw new InvalidRequestException("Loáº¡i bÃ i táº­p khÃ´ng há»£p lá»‡: " + type);
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
        return "Ná»™i dung bÃ i táº­p";
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
            throw new AccessDeniedException("ChÆ°a xÃ¡c thá»±c hoáº·c token khÃ´ng há»£p lá»‡");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new AccessDeniedException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin tÃ i khoáº£n ngÆ°á»i dÃ¹ng"));
        return new UserPrincipal(user);
    }
}
