package com.historytalk.service.assignment;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.assignment.StudentAssignmentResponse;
import com.historytalk.dto.assignment.StudentSubmissionResponse;
import com.historytalk.dto.assignment.SubmitAssignmentRequest;
import com.historytalk.entity.assignment.Assignment;
import com.historytalk.entity.assignment.AssignmentSubmission;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.enums.AssignmentStatus;
import com.historytalk.entity.enums.AssignmentType;
import com.historytalk.entity.enums.SubmissionStatus;
import com.historytalk.entity.quiz.Question;
import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.QuestionRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.assignment.AssignmentRepository;
import com.historytalk.repository.assignment.AssignmentSubmissionRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentAssignmentServiceImpl implements StudentAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final UserRepository userRepository;
    private final HistoricalContextRepository historicalContextRepository;
    private final CharacterRepository characterRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final MessageRepository messageRepository;
    private final ChatSessionRepository chatSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<StudentAssignmentResponse> getMyAssignments(
            UUID classroomId, SubmissionStatus status, Pageable pageable, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID studentUid = UUID.fromString(userPrincipal.getUid());

        Page<AssignmentSubmission> page = assignmentSubmissionRepository.findStudentSubmissions(
                studentUid, classroomId, status, pageable);

        java.util.Map<UUID, String> targetTitles = resolveTargetTitlesBatch(
                page.getContent().stream().map(AssignmentSubmission::getAssignment).toList());

        List<StudentAssignmentResponse> responses = page.getContent().stream()
                .map(s -> mapToStudentAssignmentResponse(s, targetTitles.get(s.getAssignment().getTargetId())))
                .toList();

        return PaginatedResponse.<StudentAssignmentResponse>builder()
                .content(responses)
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
    public StudentAssignmentResponse getAssignmentDetail(UUID assignmentId, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID studentUid = UUID.fromString(userPrincipal.getUid());

        AssignmentSubmission submission = assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y bÃ i táº­p hoáº·c báº¡n chÆ°a Ä‘Æ°á»£c giao bÃ i táº­p nÃ y"));

        return mapToStudentAssignmentResponse(submission);
    }

    @Override
    @Transactional
    public StudentSubmissionResponse startAssignment(UUID assignmentId, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID studentUid = UUID.fromString(userPrincipal.getUid());

        AssignmentSubmission submission = assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin ná»™p bÃ i cá»§a báº¡n cho bÃ i táº­p nÃ y"));

        Assignment assignment = submission.getAssignment();
        if (assignment.getStatus() != AssignmentStatus.ACTIVE || assignment.getDeletedAt() != null) {
            throw new InvalidRequestException("BÃ i táº­p nÃ y hiá»‡n khÃ´ng cÃ²n kháº£ dá»¥ng");
        }

        if (submission.getStatus() == SubmissionStatus.NOT_STARTED) {
            submission.setStatus(SubmissionStatus.IN_PROGRESS);
            submission.setStartedAt(LocalDateTime.now());
            assignmentSubmissionRepository.save(submission);
            log.info("Student {} started assignment {}", studentUid, assignmentId);
        }

        return mapToStudentSubmissionResponse(submission);
    }

    @Override
    @Transactional
    public StudentSubmissionResponse submitAssignment(UUID assignmentId, SubmitAssignmentRequest request, UserPrincipal principal) {
        UserPrincipal userPrincipal = resolvePrincipal(principal);
        UUID studentUid = UUID.fromString(userPrincipal.getUid());

        AssignmentSubmission submission = assignmentSubmissionRepository.findByAssignmentIdAndStudentUid(assignmentId, studentUid)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin ná»™p bÃ i cá»§a báº¡n cho bÃ i táº­p nÃ y"));

        Assignment assignment = submission.getAssignment();
        if (assignment.getStatus() != AssignmentStatus.ACTIVE || assignment.getDeletedAt() != null) {
            throw new InvalidRequestException("BÃ i táº­p nÃ y hiá»‡n khÃ´ng cÃ²n kháº£ dá»¥ng Ä‘á»ƒ ná»™p");
        }

        if (submission.getStatus() == SubmissionStatus.SUBMITTED || submission.getStatus() == SubmissionStatus.LATE_SUBMITTED) {
            throw new InvalidRequestException("Báº¡n Ä‘Ã£ ná»™p bÃ i táº­p nÃ y rá»“i, khÃ´ng thá»ƒ ná»™p láº¡i");
        }

        LocalDateTime now = LocalDateTime.now();
        SubmissionStatus finalStatus = (assignment.getDeadline() != null && now.isAfter(assignment.getDeadline()))
                ? SubmissionStatus.LATE_SUBMITTED
                : SubmissionStatus.SUBMITTED;

        AssignmentType type = assignment.getAssignmentType();
        int minReq = assignment.getMinRequirement() != null ? assignment.getMinRequirement() : 5;

        switch (type) {
            case AI_CHAT_ROLEPLAY -> {
                ChatSession chatSession;
                if (request != null && request.getChatSessionId() != null) {
                    chatSession = chatSessionRepository.findActiveBySessionIdAndUserUid(request.getChatSessionId(), studentUid)
                            .orElseThrow(() -> new InvalidRequestException("PhiÃªn chat khÃ´ng tá»“n táº¡i hoáº·c khÃ´ng thuá»™c quyá»n sá»Ÿ há»¯u cá»§a báº¡n"));

                    if (chatSession.getCharacter() != null && !chatSession.getCharacter().getCharacterId().equals(assignment.getTargetId())) {
                        throw new InvalidRequestException("PhiÃªn chat khÃ´ng pháº£i vá»›i nhÃ¢n váº­t lá»‹ch sá»­ Ä‘Æ°á»£c yÃªu cáº§u trong bÃ i táº­p");
                    }
                } else {
                    List<ChatSession> sessions = chatSessionRepository.findByUserAndCharacter(studentUid, assignment.getTargetId());
                    if (sessions.isEmpty()) {
                        throw new InvalidRequestException("Báº¡n chÆ°a cÃ³ phiÃªn trÃ² chuyá»‡n nÃ o vá»›i nhÃ¢n váº­t lá»‹ch sá»­ Ä‘Æ°á»£c yÃªu cáº§u trong bÃ i táº­p");
                    }
                    chatSession = sessions.get(0);
                }

                long userMsgCount = messageRepository.countUserMessagesInSession(chatSession.getSessionId());
                int msgCount = (int) userMsgCount;
                submission.setMessageCount(msgCount);
                submission.setChatSessionId(chatSession.getSessionId());

                double rawScore = minReq <= 0 ? 10.0 : ((double) msgCount / minReq) * 10.0;
                BigDecimal score = BigDecimal.valueOf(Math.min(10.0, rawScore)).setScale(2, RoundingMode.HALF_UP);
                submission.setScore(score);
            }
            case QUIZ_TEST -> {
                QuizSession quizSession;
                if (request != null && request.getQuizSessionId() != null) {
                    quizSession = quizSessionRepository.findBySessionId(request.getQuizSessionId())
                            .orElseThrow(() -> new InvalidRequestException("PhiÃªn lÃ m bÃ i Quiz khÃ´ng tá»“n táº¡i"));

                    if (quizSession.getUser() == null || !quizSession.getUser().getUid().equals(studentUid)) {
                        throw new AccessDeniedException("PhiÃªn lÃ m bÃ i Quiz khÃ´ng thuá»™c quyá»n sá»Ÿ há»¯u cá»§a báº¡n");
                    }

                    if (quizSession.getQuiz() == null || !quizSession.getQuiz().getQuizId().equals(assignment.getTargetId())) {
                        throw new InvalidRequestException("PhiÃªn lÃ m bÃ i Quiz khÃ´ng khá»›p vá»›i bÃ i quiz Ä‘Æ°á»£c giao trong bÃ i táº­p");
                    }

                    if (quizSession.getEndTime() == null) {
                        throw new InvalidRequestException("PhiÃªn lÃ m bÃ i Quiz nÃ y chÆ°a hoÃ n thÃ nh ná»™p bÃ i");
                    }
                } else {
                    List<QuizSession> completedSessions = quizSessionRepository.findCompletedByQuizAndUser(assignment.getTargetId(), studentUid);
                    if (completedSessions.isEmpty()) {
                        throw new InvalidRequestException("Báº¡n chÆ°a hoÃ n thÃ nh bÃ i lÃ m tráº¯c nghiá»‡m nÃ o cho bá»™ cÃ¢u há»i Ä‘Æ°á»£c giao");
                    }
                    quizSession = completedSessions.get(0);
                }

                float correctAnswers = quizSession.getScore() != null ? quizSession.getScore() : 0.0f;
                List<Question> activeQuestions = questionRepository != null
                        ? questionRepository.findActiveByQuizId(quizSession.getQuiz().getQuizId())
                        : Collections.emptyList();
                long totalQuestions = (activeQuestions != null && !activeQuestions.isEmpty())
                        ? activeQuestions.size()
                        : (quizSession.getQuiz().getQuestions() != null
                                ? quizSession.getQuiz().getQuestions().stream()
                                        .filter(q -> q.getDeletedAt() == null)
                                        .count()
                                : 0);
                if (totalQuestions <= 0) {
                    totalQuestions = 1;
                }

                double rawScore = ((double) correctAnswers / totalQuestions) * 10.0;
                BigDecimal score = BigDecimal.valueOf(Math.min(10.0, rawScore)).setScale(2, RoundingMode.HALF_UP);
                submission.setScore(score);
                submission.setQuizSession(quizSession);
            }
            case CONTEXT_READING -> {
                if (submission.getStartedAt() == null) {
                    submission.setStartedAt(now.minusSeconds(1));
                }
                long seconds = Duration.between(submission.getStartedAt(), now).getSeconds();
                int durationSeconds = (int) Math.max(0, seconds);
                submission.setReadingDurationSeconds(durationSeconds);

                int readMinReq = assignment.getMinRequirement() != null && assignment.getMinRequirement() > 0
                        ? assignment.getMinRequirement()
                        : 60;
                double rawScore = ((double) durationSeconds / readMinReq) * 10.0;
                BigDecimal score = BigDecimal.valueOf(Math.min(10.0, rawScore)).setScale(2, RoundingMode.HALF_UP);
                submission.setScore(score);
            }
        }

        submission.setStatus(finalStatus);
        submission.setSubmittedAt(now);
        assignmentSubmissionRepository.save(submission);

        log.info("Student {} submitted assignment {}: status={}, score={}",
                studentUid, assignmentId, finalStatus, submission.getScore());

        return mapToStudentSubmissionResponse(submission);
    }

    private StudentAssignmentResponse mapToStudentAssignmentResponse(AssignmentSubmission s) {
        Assignment a = s.getAssignment();
        return mapToStudentAssignmentResponse(s, getTargetTitleSafely(a.getAssignmentType(), a.getTargetId()));
    }

    private StudentAssignmentResponse mapToStudentAssignmentResponse(AssignmentSubmission s, String resolvedTargetTitle) {
        Assignment a = s.getAssignment();
        boolean isExpired = a.getDeadline() != null && LocalDateTime.now().isAfter(a.getDeadline());

        return StudentAssignmentResponse.builder()
                .assignmentId(a.getId())
                .classroomId(a.getClassroom().getId())
                .className(a.getClassroom().getClassName())
                .title(a.getTitle())
                .description(a.getDescription())
                .assignmentType(a.getAssignmentType())
                .targetId(a.getTargetId())
                .targetTitle(resolvedTargetTitle)
                .deadline(a.getDeadline())
                .minRequirement(a.getMinRequirement())
                .submissionStatus(s.getStatus())
                .score(s.getScore())
                .messageCount(s.getMessageCount())
                .readingDurationSeconds(s.getReadingDurationSeconds())
                .startedAt(s.getStartedAt())
                .submittedAt(s.getSubmittedAt())
                .isExpired(isExpired)
                .build();
    }

    private java.util.Map<UUID, String> resolveTargetTitlesBatch(List<Assignment> assignments) {
        java.util.Map<UUID, String> result = new java.util.HashMap<>();
        if (assignments == null || assignments.isEmpty()) {
            return result;
        }
        try {
            List<UUID> contextIds = assignments.stream()
                    .filter(a -> a.getAssignmentType() == AssignmentType.CONTEXT_READING && a.getTargetId() != null)
                    .map(Assignment::getTargetId).distinct().toList();
            if (!contextIds.isEmpty()) {
                historicalContextRepository.findAllById(contextIds)
                        .forEach(hc -> result.put(hc.getContextId(), hc.getName()));
            }
            List<UUID> charIds = assignments.stream()
                    .filter(a -> a.getAssignmentType() == AssignmentType.AI_CHAT_ROLEPLAY && a.getTargetId() != null)
                    .map(Assignment::getTargetId).distinct().toList();
            if (!charIds.isEmpty()) {
                characterRepository.findAllById(charIds)
                        .forEach(c -> result.put(c.getCharacterId(), c.getName()));
            }
            List<UUID> quizIds = assignments.stream()
                    .filter(a -> a.getAssignmentType() == AssignmentType.QUIZ_TEST && a.getTargetId() != null)
                    .map(Assignment::getTargetId).distinct().toList();
            if (!quizIds.isEmpty()) {
                quizRepository.findAllById(quizIds)
                        .forEach(q -> result.put(q.getQuizId(), q.getTitle()));
            }
        } catch (Exception e) {
            log.warn("Could not batch load target titles: {}", e.getMessage());
        }
        return result;
    }

    private StudentSubmissionResponse mapToStudentSubmissionResponse(AssignmentSubmission s) {
        return StudentSubmissionResponse.builder()
                .submissionId(s.getId())
                .assignmentId(s.getAssignment().getId())
                .status(s.getStatus())
                .score(s.getScore())
                .messageCount(s.getMessageCount())
                .readingDurationSeconds(s.getReadingDurationSeconds())
                .startedAt(s.getStartedAt())
                .submittedAt(s.getSubmittedAt())
                .build();
    }

    private String getTargetTitleSafely(AssignmentType type, UUID targetId) {
        if (type == null || targetId == null) {
            return null;
        }
        try {
            return switch (type) {
                case CONTEXT_READING -> historicalContextRepository.findById(targetId)
                        .map(hc -> hc.getName())
                        .orElse(null);
                case AI_CHAT_ROLEPLAY -> characterRepository.findById(targetId)
                        .map(c -> c.getName())
                        .orElse(null);
                case QUIZ_TEST -> quizRepository.findById(targetId)
                        .map(q -> q.getTitle())
                        .orElse(null);
            };
        } catch (Exception e) {
            log.warn("Could not load target title for type={} id={}: {}", type, targetId, e.getMessage());
            return null;
        }
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) {
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
