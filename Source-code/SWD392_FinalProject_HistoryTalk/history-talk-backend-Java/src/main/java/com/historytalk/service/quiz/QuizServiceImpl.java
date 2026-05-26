package com.historytalk.service.quiz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.quiz.*;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.enums.EventEra;
import com.historytalk.entity.enums.QuizLevel;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.quiz.Question;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.quiz.QuizAnswerDetail;
import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuestionRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final UserRepository userRepository;
    private final HistoricalContextRepository historicalContextRepository;
    private final ObjectMapper objectMapper;

    // ==================== Customer ====================

    @Override
    @Transactional(readOnly = true)
    public List<QuizCustomerResponse> getAllQuizzesForCustomer(String search, UUID userId) {
        log.info("getAllQuizzesForCustomer: search={}", search);
        List<Quiz> quizzes = quizRepository.findAllActiveForCustomer(normalize(search));
        return quizzes.stream()
                .map(q -> {
                    long playCount = userId != null
                            ? quizSessionRepository.countCompletedByQuizAndUser(q.getQuizId(), userId)
                            : 0L;
                    return mapToCustomerResponse(q, playCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public QuizCustomerResponse getQuizByIdForCustomer(String quizId, UUID userId) {
        log.info("getQuizByIdForCustomer: quizId={}", quizId);
        UUID uuid = parseUuid(quizId, "quizId");
        Quiz quiz = quizRepository.findActiveById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        long playCount = userId != null
                ? quizSessionRepository.countCompletedByQuizAndUser(uuid, userId)
                : 0L;
        return mapToCustomerResponse(quiz, playCount);
    }

    @Override
    @Transactional
    public QuizStartResponse startQuiz(String quizId, UUID userId, Integer limitedTime) {
        log.info("startQuiz: quizId={}, userId={}, limitedTime={}", quizId, userId, limitedTime);
        UUID uuid = parseUuid(quizId, "quizId");
        Quiz quiz = quizRepository.findActiveById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        QuizSession session = QuizSession.builder()
                .quiz(quiz)
                .user(user)
                .startTime(LocalDateTime.now())
                .limitedTime(limitedTime)
                .build();
        session = quizSessionRepository.save(session);

        List<Question> questions = questionRepository.findActiveByQuizId(uuid);

        return QuizStartResponse.builder()
                .sessionId(session.getSessionId().toString())
                .quizId(quiz.getQuizId().toString())
                .title(quiz.getTitle())
                .questions(questions.stream().map(this::mapToQuestionResponse).collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional
    public QuizSubmitResponse submitQuiz(QuizSubmitRequest request, UUID userId) {
        log.info("submitQuiz: sessionId={}, userId={}", request.getSessionId(), userId);
        UUID sessionUuid = parseUuid(request.getSessionId(), "sessionId");
        QuizSession session = quizSessionRepository.findBySessionId(sessionUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz session not found: " + request.getSessionId()));

        // Ownership check
        if (!session.getUser().getUid().equals(userId)) {
            throw new InvalidRequestException("You are not authorized to submit this quiz session");
        }

        // Already submitted check
        if (session.getEndTime() != null) {
            throw new InvalidRequestException("Quiz already submitted");
        }

        // Time limit check
        if (session.getLimitedTime() != null && session.getStartTime() != null) {
            LocalDateTime deadline = session.getStartTime().plusSeconds(session.getLimitedTime());
            if (LocalDateTime.now().isAfter(deadline)) {
                throw new InvalidRequestException("Time limit expired");
            }
        }

        // Load ordered questions
        List<Question> questions = questionRepository.findActiveByQuizId(session.getQuiz().getQuizId());

        // Score calculation — track by 0-based index
        int score = 0;
        List<Integer> correctAnswers = new ArrayList<>();
        List<Integer> wrongAnswers = new ArrayList<>();
        List<QuizAnswerDetail> answerDetails = new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            Optional<AnswerDetailRequest> answerOpt = request.getAnswers().stream()
                    .filter(a -> a.getQuestionId().equals(q.getQuestionId().toString()))
                    .findFirst();

            if (answerOpt.isPresent()) {
                AnswerDetailRequest ans = answerOpt.get();
                boolean isCorrect = q.getCorrectAnswer() != null && q.getCorrectAnswer().equals(ans.getSelectedAnswer());
                if (isCorrect) {
                    score++;
                    correctAnswers.add(i);
                } else {
                    wrongAnswers.add(i);
                }

                answerDetails.add(QuizAnswerDetail.builder()
                        .question(q)
                        .quizSession(session)
                        .selectedOption(ans.getSelectedAnswer())
                        .isCorrect(isCorrect)
                        .build());
            } else {
                // No answer submitted for this question — mark as wrong
                wrongAnswers.add(i);
            }
        }

        // Persist answer details
        session.getAnswerDetails().addAll(answerDetails);

        // Terminate session
        session.setEndTime(LocalDateTime.now());
        session.setScore((float) score);
        quizSessionRepository.save(session);

        int total = questions.size();
        double percentage = total > 0 ? (double) score / total * 100.0 : 0.0;

        return QuizSubmitResponse.builder()
                .resultId(session.getSessionId().toString())
                .score(score)
                .totalQuestions(total)
                .percentage(percentage)
                .correctAnswers(correctAnswers)
                .wrongAnswers(wrongAnswers)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<QuizHistoryResponse> getQuizHistory(UUID userId, Pageable pageable) {
        log.info("getQuizHistory: userId={}", userId);
        Page<QuizSession> page = quizSessionRepository.findCompletedByUserUid(userId, pageable);
        return toPaginatedResponse(page.map(this::mapToHistoryResponse));
    }

    // ==================== Staff ====================

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<QuizStaffResponse> getAllQuizzesForStaff(String search, String era, Pageable pageable, String role) {
        log.info("getAllQuizzesForStaff: search={}, era={}", search, era);
        EventEra eraEnum = parseEra(era);
        Page<Quiz> page = quizRepository.findAllForStaff(normalize(search), eraEnum, isContentManager(role), pageable);
        return toPaginatedResponse(page.map(this::mapToStaffResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public QuizStaffResponse getQuizByIdForStaff(String quizId) {
        log.info("getQuizByIdForStaff: quizId={}", quizId);
        Quiz quiz = quizRepository.findById(parseUuid(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        return mapToStaffResponse(quiz);
    }

    @Override
    @Transactional
    public QuizStaffResponse createQuiz(CreateQuizRequest request, UUID userId) {
        log.info("createQuiz: title={}, userId={}", request.getTitle(), userId);

        if (quizRepository.existsByTitleIgnoreCase(request.getTitle())) {
            throw new DataConflictException("Quiz with this title already exists: " + request.getTitle());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        HistoricalContext context = historicalContextRepository.findById(parseUuid(request.getContextId(), "contextId"))
                .orElseThrow(() -> new ResourceNotFoundException("Historical context not found: " + request.getContextId()));

        QuizLevel level = parseLevel(request.getLevel());

        Quiz quiz = Quiz.builder()
                .title(request.getTitle())
                .level(level)
                .historicalContext(context)
                .createdBy(user)
                .isPublished(Boolean.TRUE.equals(request.getIsPublished()))
                .build();
        quiz = quizRepository.save(quiz);

        // Save each question
        if (request.getQuestions() != null) {
            for (QuestionRequest qr : request.getQuestions()) {
                questionRepository.save(buildQuestion(qr, quiz));
            }
        }

        // Reload to get questions populated
        quiz = quizRepository.findById(quiz.getQuizId()).orElseThrow();
        return mapToStaffResponse(quiz);
    }

    @Override
    @Transactional
    public QuizStaffResponse updateQuiz(String quizId, UpdateQuizRequest request) {
        log.info("updateQuiz: quizId={}", quizId);
        Quiz quiz = quizRepository.findById(parseUuid(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            String newTitle = request.getTitle().trim();
            if (!newTitle.equalsIgnoreCase(quiz.getTitle()) &&
                    quizRepository.existsByTitleIgnoreCaseAndQuizIdNot(newTitle, quiz.getQuizId())) {
                throw new DataConflictException("Quiz with this title already exists: " + newTitle);
            }
            quiz.setTitle(newTitle);
        }

        if (request.getContextId() != null && !request.getContextId().isBlank()) {
            HistoricalContext context = historicalContextRepository
                    .findById(parseUuid(request.getContextId(), "contextId"))
                    .orElseThrow(() -> new ResourceNotFoundException("Historical context not found: " + request.getContextId()));
            quiz.setHistoricalContext(context);
        }

        if (request.getLevel() != null && !request.getLevel().isBlank()) {
            quiz.setLevel(parseLevel(request.getLevel()));
        }

        if (request.getIsPublished() != null) {
            quiz.setIsPublished(request.getIsPublished());
        }

        quiz = quizRepository.save(quiz);
        return mapToStaffResponse(quiz);
    }

    @Override
    @Transactional
    public void deleteQuiz(String quizId) {
        log.info("deleteQuiz: quizId={}", quizId);
        Quiz quiz = quizRepository.findById(parseUuid(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        quizRepository.delete(quiz);
    }

    @Override
    @Transactional
    public void softDeleteQuiz(String quizId) {
        log.info("softDeleteQuiz: quizId={}", quizId);
        Quiz quiz = quizRepository.findById(parseUuid(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        quiz.setDeletedAt(LocalDateTime.now());
        quizRepository.save(quiz);
    }

    @Override
    @Transactional
    public QuestionResponse addQuestion(String quizId, QuestionRequest request) {
        log.info("addQuestion: quizId={}", quizId);
        Quiz quiz = quizRepository.findById(parseUuid(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        Question question = questionRepository.save(buildQuestion(request, quiz));
        return mapToQuestionResponse(question);
    }

    @Override
    @Transactional
    public void updateQuestion(String quizId, String questionId, QuestionRequest request) {
        log.info("updateQuestion: quizId={}, questionId={}", quizId, questionId);
        // Validate quiz exists
        UUID quizUuid = parseUuid(quizId, "quizId");
        quizRepository.findById(quizUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

        Question question = questionRepository.findById(parseUuid(questionId, "questionId"))
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));

        if (!question.getQuiz().getQuizId().equals(quizUuid)) {
            throw new InvalidRequestException("Question does not belong to quiz: " + quizId);
        }

        if (request.getContent() != null && !request.getContent().isBlank()) {
            question.setContent(request.getContent());
        }
        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            question.setOptions(serializeOptions(request.getOptions()));
        }
        if (request.getCorrectAnswer() != null) {
            question.setCorrectAnswer(request.getCorrectAnswer());
        }
        if (request.getExplanation() != null) {
            question.setExplanation(request.getExplanation());
        }

        questionRepository.save(question);
    }

    @Override
    @Transactional
    public void deleteQuestion(String quizId, String questionId) {
        log.info("deleteQuestion: quizId={}, questionId={}", quizId, questionId);
        UUID quizUuid = parseUuid(quizId, "quizId");
        quizRepository.findById(quizUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

        Question question = questionRepository.findById(parseUuid(questionId, "questionId"))
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));

        if (!question.getQuiz().getQuizId().equals(quizUuid)) {
            throw new InvalidRequestException("Question does not belong to quiz: " + quizId);
        }

        questionRepository.delete(question);
    }

    // ==================== Private Helpers ====================

    private Question buildQuestion(QuestionRequest request, Quiz quiz) {
        return Question.builder()
                .quiz(quiz)
                .content(request.getContent())
                .options(serializeOptions(request.getOptions()))
                .correctAnswer(request.getCorrectAnswer())
                .explanation(request.getExplanation())
                .build();
    }

    private QuizCustomerResponse mapToCustomerResponse(Quiz quiz, long playCount) {
        HistoricalContext ctx = quiz.getHistoricalContext();
        return QuizCustomerResponse.builder()
                .quizId(quiz.getQuizId().toString())
                .title(quiz.getTitle())
                .level(quiz.getLevel() != null ? quiz.getLevel().name() : null)
                .era(ctx != null && ctx.getEra() != null ? ctx.getEra().name() : null)
                .playCount((int) playCount)
                .contextTitle(ctx != null ? ctx.getName() : null)
                .build();
    }

    private QuizStaffResponse mapToStaffResponse(Quiz quiz) {
        HistoricalContext ctx = quiz.getHistoricalContext();
        long playCount = quizSessionRepository.countCompletedByQuiz(quiz.getQuizId());
        List<Question> questions = questionRepository.findActiveByQuizId(quiz.getQuizId());

        return QuizStaffResponse.builder()
                .quizId(quiz.getQuizId().toString())
                .title(quiz.getTitle())
                .era(ctx != null && ctx.getEra() != null ? ctx.getEra().name() : null)
                .level(quiz.getLevel() != null ? quiz.getLevel().name() : null)
                .playCount((int) playCount)
                .contextId(ctx != null ? ctx.getContextId().toString() : null)
                .contextTitle(ctx != null ? ctx.getName() : null)
                .createdBy(quiz.getCreatedBy() != null ? quiz.getCreatedBy().getUserName() : null)
                .createdDate(quiz.getCreatedAt())
                .updatedDate(quiz.getUpdatedAt())
                .isPublished(quiz.getIsPublished())
                .status(buildStatus(quiz.getIsPublished(), quiz.getDeletedAt()))
                .deletedAt(quiz.getDeletedAt())
                .questions(questions.stream().map(this::mapToQuestionResponse).collect(Collectors.toList()))
                .build();
    }

    private ContentStatus buildStatus(Boolean isPublished, LocalDateTime deletedAt) {
        if (deletedAt != null) {
            return ContentStatus.INACTIVE;
        }
        if (!Boolean.TRUE.equals(isPublished)) {
            return ContentStatus.DRAFT;
        }
        return ContentStatus.ACTIVE;
    }

    private boolean isContentManager(String role) {
        return role != null && (
                "CONTENT_ADMIN".equalsIgnoreCase(role)
                        || "SYSTEM_ADMIN".equalsIgnoreCase(role)
                        || "STAFF".equalsIgnoreCase(role)
                        || "ADMIN".equalsIgnoreCase(role)
        );
    }

    private QuestionResponse mapToQuestionResponse(Question q) {
        return QuestionResponse.builder()
                .questionId(q.getQuestionId().toString())
                .content(q.getContent())
                .options(deserializeOptions(q.getOptions()))
                .correctAnswer(q.getCorrectAnswer())
                .explanation(q.getExplanation())
                .build();
    }

    private QuizHistoryResponse mapToHistoryResponse(QuizSession session) {
        Quiz quiz = session.getQuiz();
        int totalQuestions = quiz != null
                ? questionRepository.findActiveByQuizId(quiz.getQuizId()).size()
                : 0;
        int score = session.getScore() != null ? session.getScore().intValue() : 0;
        double percentage = totalQuestions > 0 ? (double) score / totalQuestions * 100.0 : 0.0;

        return QuizHistoryResponse.builder()
                .sessionId(session.getSessionId().toString())
                .quizId(quiz != null ? quiz.getQuizId().toString() : null)
                .quizTitle(quiz != null ? quiz.getTitle() : null)
                .score(score)
                .totalQuestions(totalQuestions)
                .percentage(percentage)
                .completedAt(session.getEndTime() != null ? session.getEndTime().toString() : null)
                .build();
    }

    private <T> PaginatedResponse<T> toPaginatedResponse(Page<T> page) {
        return PaginatedResponse.<T>builder()
                .content(page.getContent())
                .currentPage(page.getNumber())          // 0-indexed per contract
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    private String serializeOptions(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            log.error("Failed to serialize options", e);
            throw new InvalidRequestException("Invalid options format");
        }
    }

    private List<String> deserializeOptions(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize options: {}", json);
            return new ArrayList<>();
        }
    }

    private UUID parseUuid(String id, String fieldName) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid " + fieldName + " format: " + id);
        }
    }

    private String normalize(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }

    private EventEra parseEra(String era) {
        if (era == null || era.isBlank()) return null;
        try {
            return EventEra.valueOf(era.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid era value: " + era + ". Valid values: ANCIENT, MEDIEVAL, MODERN, CONTEMPORARY");
        }
    }

    private QuizLevel parseLevel(String level) {
        if (level == null || level.isBlank()) {
            throw new InvalidRequestException("Level is required");
        }
        try {
            return QuizLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid level value: " + level + ". Valid values: EASY, MEDIUM, HARD");
        }
    }
}
