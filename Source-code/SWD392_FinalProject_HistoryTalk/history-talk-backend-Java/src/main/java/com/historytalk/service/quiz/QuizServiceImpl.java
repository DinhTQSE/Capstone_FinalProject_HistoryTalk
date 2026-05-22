package com.historytalk.service.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.*;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.entity.enums.EventEra;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.quiz.*;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuestionRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.utils.SecurityUtils;
import com.historytalk.utils.UuidUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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

    private static final long SESSION_EXPIRATION_MINUTES = 30;

    // ==================== Staff Operations ====================

    @Transactional(readOnly = true)
    @Override
    public PaginatedResponse<QuizStaffResponse> getAllQuizzesForStaff(
            String search, Integer grade, EventEra era, Pageable pageable) {
        
        log.info("Fetching quizzes for staff with search: {}, grade: {}, era: {}", search, grade, era);
        
        Page<Quiz> page = quizRepository.findAllWithSearch(normalize(search), grade, era, true, pageable);
        
        return mapPageToPaginatedResponse(
            page.map(this::mapToStaffResponse)
        );
    }

    @Transactional(readOnly = true)
    @Override
    public PaginatedResponse<QuizStaffResponse> getQuizzesByContextForStaff(
            String contextId, String search, Integer grade, EventEra era, Pageable pageable) {
        log.info("Fetching quizzes for staff by context: {}", contextId);

        UUID ctx = UuidUtils.fromString(contextId, "contextId");
        historicalContextRepository.findById(ctx)
                .orElseThrow(() -> new ResourceNotFoundException("Historical context not found: " + contextId));

        Page<Quiz> page = quizRepository.findAllByContextWithSearch(ctx, normalize(search), grade, era, true, pageable);
        return mapPageToPaginatedResponse(page.map(this::mapToStaffResponse));
    }

    @Transactional(readOnly = true)
    @Override
    public List<QuizCustomerResponse> getAllQuizzesForCustomer(String search) {
        log.info("Fetching quizzes for customer with search: {}", search);
        
        return quizRepository.findAllSimple(normalize(search), false)
                .stream()
                .map(this::mapToCustomerResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public QuizStaffResponse getQuizByIdForStaff(String quizId) {
        log.info("Fetching quiz by ID for staff: {}", quizId);
        
        try {
            UUID quizUuid = UUID.fromString(quizId);
            log.debug("✓ Converted string ID to UUID: {}", quizUuid);
            
            Quiz quiz = quizRepository.findById(quizUuid)
                    .orElseThrow(() -> {
                        log.warn("❌ Quiz not found in database (or soft deleted) for ID: {}", quizId);
                        return new ResourceNotFoundException("Quiz not found with ID: " + quizId);
                    });
            
            log.info("✓ Quiz found successfully: {} | Title: {}", quizId, quiz.getTitle());
            return mapToStaffResponse(quiz);
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid UUID format: {}", quizId, e);
            throw new InvalidRequestException("Invalid quiz ID format: " + quizId);
        } catch (ResourceNotFoundException e) {
            log.error("❌ ResourceNotFoundException thrown: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ Unexpected error in getQuizByIdForStaff: {}", quizId, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public QuizCustomerResponse getQuizByIdForCustomer(String quizId) {
        log.info("Fetching quiz by ID for customer: {}", quizId);
        
        Quiz quiz = quizRepository.findActiveById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));
        
        return mapToCustomerResponse(quiz);
    }

    @Transactional
    @Override
    public QuizStaffResponse createQuiz(CreateQuizRequest request, String userId) {
        log.info("Creating quiz: {} by user: {}", request.getTitle(), userId);
        
        // Check for duplicate title
        if (quizRepository.findByTitleIgnoreCase(request.getTitle()).isPresent()) {
            throw new DataConflictException("Quiz with title already exists: " + request.getTitle());
        }

        User user = userRepository.findById(UuidUtils.fromString(userId, "userId"))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        HistoricalContext context = historicalContextRepository.findById(UuidUtils.fromString(request.getContextId(), "contextId"))
                .orElseThrow(() -> new ResourceNotFoundException("Historical context not found: " + request.getContextId()));

        EventEra era = null;
        if (request.getEra() != null && !request.getEra().isEmpty()) {
            try {
                era = EventEra.valueOf(request.getEra());
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("Invalid era value: " + request.getEra());
            }
        }

        Quiz quiz = Quiz.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .grade(request.getGrade())
                .chapterNumber(request.getChapterNumber())
                .chapterTitle(request.getChapterTitle())
                .era(era)
                .durationSeconds(request.getDurationSeconds())
                .playCount(0)
                .rating(0.0)
                .historicalContext(context)
                .createdBy(user)
                .build();

        quiz = quizRepository.save(quiz);

        // Add questions
        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            List<Question> questions = new ArrayList<>();
            int orderIndex = 0;
            for (QuestionRequest qReq : request.getQuestions()) {
                Question question = createQuestionEntity(qReq, quiz, orderIndex++);
                questions.add(question);
            }
            quiz.setQuestions(questions);
            quiz = quizRepository.save(quiz);
        }

        return mapToStaffResponse(quiz);
    }

    @Transactional
    @Override
    public QuizStaffResponse updateQuiz(String quizId, UpdateQuizRequest request, String userId, String userRole) {
        log.info("Updating quiz: {} by user: {}", quizId, userId);
        
        Quiz quiz = quizRepository.findById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));

        // Check ownership
        checkOwnershipOrAdmin(quiz.getCreatedBy().getUid().toString(), userId, userRole);

        // Check for duplicate title if changed
        if (request.getTitle() != null && !request.getTitle().equalsIgnoreCase(quiz.getTitle())) {
            if (quizRepository.findByTitleIgnoreCase(request.getTitle()).isPresent()) {
                throw new DataConflictException("Quiz with title already exists: " + request.getTitle());
            }
            quiz.setTitle(request.getTitle());
        }

        if (request.getDescription() != null) {
            quiz.setDescription(request.getDescription());
        }
        if (request.getGrade() != null) {
            quiz.setGrade(request.getGrade());
        }
        if (request.getChapterNumber() != null) {
            quiz.setChapterNumber(request.getChapterNumber());
        }
        if (request.getChapterTitle() != null) {
            quiz.setChapterTitle(request.getChapterTitle());
        }
        if (request.getEra() != null && !request.getEra().isEmpty()) {
            try {
                quiz.setEra(EventEra.valueOf(request.getEra()));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("Invalid era value: " + request.getEra());
            }
        }
        if (request.getDurationSeconds() != null) {
            quiz.setDurationSeconds(request.getDurationSeconds());
        }

        quiz = quizRepository.save(quiz);
        return mapToStaffResponse(quiz);
    }

    @Transactional
    @Override
    public void deleteQuiz(String quizId, String userId, String userRole) {
        log.info("Deleting quiz: {} by user: {}", quizId, userId);
        
        Quiz quiz = quizRepository.findById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));

        // Check ownership
        checkOwnershipOrAdmin(quiz.getCreatedBy().getUid().toString(), userId, userRole);

        quizRepository.delete(quiz);
    }

    @Transactional
    @Override
    public void softDeleteQuiz(String quizId, String userId, String userRole) {
        log.info("Soft deleting quiz: {} by user: {}", quizId, userId);
        
        Quiz quiz = quizRepository.findById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));

        // Check ownership or staff/admin
        checkOwnershipOrStaffOrAdmin(quiz.getCreatedBy().getUid().toString(), userId, userRole);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        quiz.setDeletedAt(now);

        // Cascade to Questions
        if (quiz.getQuestions() != null) {
            quiz.getQuestions().forEach(question -> {
                question.setDeletedAt(now);
                if (question.getAnswerDetails() != null) {
                    question.getAnswerDetails().forEach(detail -> detail.setDeletedAt(now));
                }
            });
        }

        quizRepository.save(quiz);
        log.info("Quiz soft-deleted successfully with ID: {}", quizId);
    }

    @Transactional
    @Override
    public void addQuestion(String quizId, QuestionRequest request, String userId, String userRole) {
        log.info("Adding question to quiz: {} by user: {}", quizId, userId);
        
        Quiz quiz = quizRepository.findById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));

        // Check ownership
        checkOwnershipOrAdmin(quiz.getCreatedBy().getUid().toString(), userId, userRole);

        int newOrderIndex = quiz.getQuestions().size();
        Question question = createQuestionEntity(request, quiz, newOrderIndex);
        quiz.getQuestions().add(question);
        quizRepository.save(quiz);
    }

    @Transactional
    @Override
    public void updateQuestion(String quizId, String questionId, QuestionRequest request, String userId, String userRole) {
        log.info("Updating question: {} in quiz: {} by user: {}", questionId, quizId, userId);
        
        Quiz quiz = quizRepository.findById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));

        // Check ownership
        checkOwnershipOrAdmin(quiz.getCreatedBy().getUid().toString(), userId, userRole);

        Question question = questionRepository.findById(UuidUtils.fromString(questionId, "questionId"))
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + questionId));

        if (request.getContent() != null) {
            question.setContent(request.getContent());
        }
        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            question.setOptions(serializeOptions(request.getOptions()));
        }
        if (request.getCorrectAnswer() != null) {
            question.setCorrectAnswer(request.getCorrectAnswer());
        }
        if (request.getOrderIndex() != null) {
            question.setOrderIndex(request.getOrderIndex());
        }
        if (request.getExplanation() != null) {
            question.setExplanation(request.getExplanation());
        }

        questionRepository.save(question);
    }

    @Transactional
    @Override
    public void deleteQuestion(String quizId, String questionId, String userId, String userRole) {
        log.info("Deleting question: {} from quiz: {} by user: {}", questionId, quizId, userId);
        
        Quiz quiz = quizRepository.findById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));

        // Check ownership or staff/admin
        checkOwnershipOrStaffOrAdmin(quiz.getCreatedBy().getUid().toString(), userId, userRole);

        Question question = questionRepository.findById(UuidUtils.fromString(questionId, "questionId"))
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + questionId));

        questionRepository.delete(question);
    }

    @Transactional
    @Override
    public void softDeleteQuestion(String quizId, String questionId, String userId, String userRole) {
        log.info("Soft deleting question: {} from quiz: {} by user: {}", questionId, quizId, userId);
        
        Quiz quiz = quizRepository.findById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));

        // Check ownership
        checkOwnershipOrAdmin(quiz.getCreatedBy().getUid().toString(), userId, userRole);

        Question question = questionRepository.findById(UuidUtils.fromString(questionId, "questionId"))
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + questionId));

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        question.setDeletedAt(now);

        // Cascade to QuizAnswerDetails
        if (question.getAnswerDetails() != null) {
            question.getAnswerDetails().forEach(detail -> detail.setDeletedAt(now));
        }

        questionRepository.save(question);
        log.info("Question soft-deleted successfully with ID: {}", questionId);
    }

    @Transactional
    @Override
    public void reorderQuestions(String quizId, List<String> questionIds, String userId, String userRole) {
        log.info("Reordering questions in quiz: {} by user: {}", quizId, userId);
        
        Quiz quiz = quizRepository.findById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));

        // Check ownership
        checkOwnershipOrAdmin(quiz.getCreatedBy().getUid().toString(), userId, userRole);

        for (int i = 0; i < questionIds.size(); i++) {
            final int index = i;
            Question question = questionRepository.findById(UuidUtils.fromString(questionIds.get(index), "questionId"))
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionIds.get(index)));
            question.setOrderIndex(index);
            questionRepository.save(question);
        }
    }

    // ==================== Customer Operations ====================

    @Transactional
    @Override
    public QuizStartResponse startQuiz(String quizId, String userId) {
        log.info("Starting quiz: {} for user: {}", quizId, userId);
        
        Quiz quiz = quizRepository.findActiveById(UuidUtils.fromString(quizId, "quizId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));

        User user = userRepository.findById(UuidUtils.fromString(userId, "userId"))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Increment play count
        quiz.setPlayCount(quiz.getPlayCount() + 1);
        quizRepository.save(quiz);

        // Create session
        LocalDateTime startTime = LocalDateTime.now();
        QuizSession session = QuizSession.builder()
            .quiz(quiz)
            .user(user)
            .startTime(startTime)
            .limitedTime(quiz.getDurationSeconds())
            .isSubmitted(false)
            .build();

        session = quizSessionRepository.save(session);

        // Get questions
        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(quiz.getQuizId(), false);

        return QuizStartResponse.builder()
                .sessionId(session.getSessionId().toString())
                .quizId(quiz.getQuizId().toString())
                .title(quiz.getTitle())
            .limitedTime(quiz.getDurationSeconds())
                .questions(questions.stream()
                        .map(this::mapQuestionToResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional
    @Override
    public QuizSubmitResponse submitQuiz(QuizSubmitRequest request, String userId) {
        log.info("Submitting quiz for user: {}", userId);
        
        // Validate session
        QuizSession session = quizSessionRepository.findBySessionId(UuidUtils.fromString(request.getSessionId(), "sessionId"))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz session not found: " + request.getSessionId()));

        if (session.getIsSubmitted()) {
            throw new InvalidRequestException("Quiz already submitted");
        }

        if (session.getLimitedTime() != null && session.getStartTime() != null) {
            LocalDateTime deadline = session.getStartTime().plusSeconds(session.getLimitedTime());
            if (LocalDateTime.now().isAfter(deadline)) {
                throw new InvalidRequestException("Quiz session has expired");
            }
        }

        if (!session.getUser().getUid().toString().equals(userId)) {
            throw new InvalidRequestException("Unauthorized to submit this quiz");
        }

        // Get quiz and questions
        Quiz quiz = session.getQuiz();
        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(quiz.getQuizId(), false);

        // Check if user exceeded duration limit
        if (quiz.getDurationSeconds() != null && request.getDurationSeconds() > quiz.getDurationSeconds()) {
            log.warn("User {} exceeded time limit. Duration: {} seconds, Limit: {} seconds",
                userId, request.getDurationSeconds(), quiz.getDurationSeconds());
            throw new InvalidRequestException("Time limit exceeded. You took " + request.getDurationSeconds() +
                " seconds but the limit is " + quiz.getDurationSeconds() + " seconds");
        }

        // Calculate score
        int score = 0;
        List<Integer> correctAnswers = new ArrayList<>();
        List<Integer> wrongAnswers = new ArrayList<>();

        for (AnswerDetailRequest answerReq : request.getAnswers()) {
            Question question = questions.stream()
                    .filter(q -> q.getQuestionId().toString().equals(answerReq.getQuestionId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + answerReq.getQuestionId()));

            boolean isCorrect = question.getCorrectAnswer().equals(answerReq.getSelectedAnswer());
            if (isCorrect) {
                score++;
                correctAnswers.add(answerReq.getSelectedAnswer());
            } else {
                wrongAnswers.add(answerReq.getSelectedAnswer());
            }
        }

        // Save answer details
        List<QuizAnswerDetail> answerDetails = new ArrayList<>();
        for (AnswerDetailRequest answerReq : request.getAnswers()) {
            Question question = questions.stream()
                    .filter(q -> q.getQuestionId().toString().equals(answerReq.getQuestionId()))
                    .findFirst()
                    .orElseThrow();

            boolean isCorrect = question.getCorrectAnswer().equals(answerReq.getSelectedAnswer());
            QuizAnswerDetail detail = QuizAnswerDetail.builder()
                    .selectedOption(answerReq.getSelectedAnswer())
                    .isCorrect(isCorrect)
                    .quizSession(session)
                    .question(question)
                    .build();
            answerDetails.add(detail);
        }
        session.setAnswerDetails(answerDetails);

        // Mark session as submitted + score/timing
        session.setIsSubmitted(true);
        session.setScore((double) score);
        session.setEndTime(LocalDateTime.now());
        quizSessionRepository.save(session);

        double percentage = (double) score / questions.size() * 100;

        return QuizSubmitResponse.builder()
            .sessionId(session.getSessionId().toString())
                .score(score)
                .totalQuestions(questions.size())
                .percentage(percentage)
                .correctAnswers(correctAnswers)
                .wrongAnswers(wrongAnswers)
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public PaginatedResponse<QuizHistoryResponse> getQuizHistory(String userId, Pageable pageable) {
        log.info("Fetching quiz history for user: {}", userId);

        Page<QuizSession> page = quizSessionRepository.findByUserUid(
                UuidUtils.fromString(userId, "userId"), false, pageable);

        return mapPageToPaginatedResponse(page.map(this::mapToHistoryResponse));
    }

    // ==================== Helper Methods ====================

    private Question createQuestionEntity(QuestionRequest request, Quiz quiz, int orderIndex) {
        return Question.builder()
                .content(request.getContent())
                .options(serializeOptions(request.getOptions()))
                .correctAnswer(request.getCorrectAnswer())
                .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : orderIndex)
                .explanation(request.getExplanation())
                .quiz(quiz)
                .build();
    }

    private String serializeOptions(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            log.error("Failed to serialize options", e);
            throw new InvalidRequestException("Failed to serialize options");
        }
    }

    private List<String> deserializeOptions(String options) {
        try {
            return objectMapper.readValue(options, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize options", e);
            return new ArrayList<>();
        }
    }

    private QuizStaffResponse mapToStaffResponse(Quiz quiz) {
        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(quiz.getQuizId(), true);
        
        return QuizStaffResponse.builder()
                .quizId(quiz.getQuizId().toString())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .grade(quiz.getGrade())
                .chapterNumber(quiz.getChapterNumber())
                .chapterTitle(quiz.getChapterTitle())
                .era(quiz.getEra() != null ? quiz.getEra().toString() : null)
                .durationSeconds(quiz.getDurationSeconds())
                .playCount(quiz.getPlayCount())
                .rating(quiz.getRating())
                .contextId(safeGetContextId(quiz))
                .contextTitle(safeGetContextName(quiz))
                .createdBy(quiz.getCreatedBy().getUserName())
                .createdDate(LocalDateTime.now()) // You may need to add createdDate to Quiz entity
                .updatedDate(LocalDateTime.now()) // You may need to add updatedDate to Quiz entity
                .deletedAt(quiz.getDeletedAt())
                .questions(questions.stream()
                        .map(this::mapQuestionToResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private QuizCustomerResponse mapToCustomerResponse(Quiz quiz) {
        return QuizCustomerResponse.builder()
                .quizId(quiz.getQuizId().toString())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .grade(quiz.getGrade())
                .chapterNumber(quiz.getChapterNumber())
                .chapterTitle(quiz.getChapterTitle())
                .era(quiz.getEra() != null ? quiz.getEra().toString() : null)
                .durationSeconds(quiz.getDurationSeconds())
                .playCount(quiz.getPlayCount())
                .rating(quiz.getRating())
                .contextTitle(safeGetContextName(quiz))
                .deletedAt(quiz.getDeletedAt())
                .build();
    }

    private QuestionResponse mapQuestionToResponse(Question question) {
        return QuestionResponse.builder()
                .questionId(question.getQuestionId().toString())
                .content(question.getContent())
                .options(deserializeOptions(question.getOptions()))
                .correctAnswer(question.getCorrectAnswer())
                .orderIndex(question.getOrderIndex())
                .explanation(question.getExplanation())
                .deletedAt(question.getDeletedAt())
                .build();
    }

    private QuizHistoryResponse mapToHistoryResponse(QuizSession session) {
        String quizId = safeGetQuizId(session);
        String quizTitle = safeGetQuizTitle(session);
        int totalQuestions = safeGetQuizQuestionCount(session);
        double score = session.getScore() != null ? session.getScore() : 0.0;
        double percentage = totalQuestions > 0 ? score / totalQuestions * 100 : 0;

        Integer timeSpentSeconds = null;
        if (session.getStartTime() != null && session.getEndTime() != null) {
            timeSpentSeconds = Math.toIntExact(java.time.Duration.between(session.getStartTime(), session.getEndTime()).getSeconds());
        }

        return QuizHistoryResponse.builder()
                .sessionId(session.getSessionId().toString())
                .quizId(quizId)
                .quizTitle(quizTitle)
                .score(session.getScore())
                .totalQuestions(totalQuestions)
                .percentage(percentage)
                .timeSpentSeconds(timeSpentSeconds)
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .build();
    }

    private <T> PaginatedResponse<T> mapPageToPaginatedResponse(Page<T> page) {
        return PaginatedResponse.<T>builder()
                .content(page.getContent())
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional
    @Override
    public void softDeleteQuizSession(String sessionId, String userId) {
        log.info("Soft deleting quiz session: {} for user: {}", sessionId, userId);
        
        QuizSession session = quizSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz session not found with ID: " + sessionId));
                
        if (!session.getUser().getUid().equals(UUID.fromString(userId))) {
            throw new InvalidRequestException("You do not have permission to delete this quiz session");
        }
        
        LocalDateTime now = LocalDateTime.now();
        session.setDeletedAt(now);
        if (session.getAnswerDetails() != null) {
            session.getAnswerDetails().forEach(detail -> detail.setDeletedAt(now));
        }
        quizSessionRepository.save(session);
        
        log.info("Quiz session soft deleted successfully: {}", sessionId);
    }

    @Transactional
    @Override
    public void softDeleteQuizSessionStaff(String sessionId, String userId, String userRole) {
        log.info("Soft deleting quiz session: {} for user: {}", sessionId, userId);

        QuizSession session = quizSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("Quiz session not found with ID: " + sessionId));

        if (!session.getUser().getUid().equals(UUID.fromString(userId)) && !"STAFF".equalsIgnoreCase(userRole)) {
            throw new InvalidRequestException("You do not have permission to delete this quiz session");
        }

        LocalDateTime now = LocalDateTime.now();
        session.setDeletedAt(now);
        if (session.getAnswerDetails() != null) {
            session.getAnswerDetails().forEach(detail -> detail.setDeletedAt(now));
        }
        quizSessionRepository.save(session);

        log.info("Quiz session soft deleted successfully: {}", sessionId);
    }

    private void checkOwnershipOrAdmin(String createdByUid, String userId, String userRole) {
        if (!createdByUid.equals(userId) && !userRole.equalsIgnoreCase("ADMIN")) {
            throw new InvalidRequestException("You don't have permission to modify this quiz");
        }
    }

    private void checkOwnershipOrStaffOrAdmin(String createdByUid, String userId, String userRole) {
        if (!createdByUid.equals(userId) && !"ADMIN".equalsIgnoreCase(userRole) && !"STAFF".equalsIgnoreCase(userRole)) {
            throw new InvalidRequestException("You don't have permission to modify or delete this quiz");
        }
    }

    private String normalize(String search) {
        return (search == null || search.isBlank()) ? null : search.toLowerCase();
    }

    // ==================== Null-safe Helper Methods ====================

    private String safeGetContextId(Quiz quiz) {
        try {
            HistoricalContext ctx = quiz.getHistoricalContext();
            return ctx != null ? ctx.getContextId().toString() : null;
        } catch (Exception e) {
            log.warn("Failed to load context for quiz {}", quiz.getQuizId());
            return null;
        }
    }

    private String safeGetContextName(Quiz quiz) {
        try {
            HistoricalContext ctx = quiz.getHistoricalContext();
            return ctx != null ? ctx.getName() : "[Deleted Context]";
        } catch (Exception e) {
            return "[Deleted Context]";
        }
    }

    private String safeGetQuizTitle(QuizSession session) {
        try {
            Quiz quiz = session.getQuiz();
            return quiz != null ? quiz.getTitle() : "[Deleted Quiz]";
        } catch (Exception e) {
            return "[Deleted Quiz]";
        }
    }

    private String safeGetQuizId(QuizSession session) {
        try {
            Quiz quiz = session.getQuiz();
            return quiz != null ? quiz.getQuizId().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int safeGetQuizQuestionCount(QuizSession session) {
        try {
            Quiz quiz = session.getQuiz();
            return quiz != null && quiz.getQuestions() != null ? quiz.getQuestions().size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

}
