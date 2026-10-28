package com.historytalk.service.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.quiz.*;
import com.historytalk.dto.quiz.quizdata.FillInBlankAnswerData;
import com.historytalk.dto.quiz.quizdata.FillInBlankQuestionData;
import com.historytalk.dto.quiz.quizdata.MatchingAnswerData;
import com.historytalk.dto.quiz.quizdata.MatchingPair;
import com.historytalk.dto.quiz.quizdata.MatchingQuestionData;
import com.historytalk.dto.quiz.quizdata.MultipleChoiceAnswerData;
import com.historytalk.dto.quiz.quizdata.MultipleChoiceQuestionData;
import com.historytalk.dto.quiz.quizdata.QuestionOption;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.enums.EventEra;
import com.historytalk.entity.enums.QuestType;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.entity.enums.QuizLevel;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.quiz.Question;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.quiz.QuizAnswerDetail;
import com.historytalk.entity.quiz.QuizQuestion;
import com.historytalk.entity.quiz.QuizQuestionId;
import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuestionRepository;
import com.historytalk.repository.QuizAnswerDetailRepository;
import com.historytalk.repository.QuizQuestionRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.gamification.GamificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final QuizAnswerDetailRepository quizAnswerDetailRepository;
    private final UserRepository userRepository;
    private final HistoricalContextRepository historicalContextRepository;
    private final ObjectMapper objectMapper;
    private final GamificationService gamificationService;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuestionDataValidator questionDataValidator;

    // ==================== Customer ====================

    @Override
    @Transactional(readOnly = true)
    public List<QuizCustomerResponse> getAllQuizzesForCustomer(String search, String contextId, UUID userId) {
        log.info("getAllQuizzesForCustomer: search={}, contextId={}", search, contextId);
        UUID contextUuid = null;
        if (contextId != null && !contextId.isBlank()) {
            try {
                contextUuid = UUID.fromString(contextId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid contextId format: {}", contextId);
            }
        }
        List<Quiz> quizzes = quizRepository.findAllActiveForCustomer(normalize(search), contextUuid);
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
            throw new InvalidRequestException("Bạn không có quyền nộp bài cho phiên làm bài này");
        }

        // Already submitted check
        if (session.getEndTime() != null) {
            throw new InvalidRequestException("Quiz đã được nộp");
        }

        // Time limit check
        // if (session.getLimitedTime() != null && session.getStartTime() != null) {
        //     LocalDateTime deadline = session.getStartTime().plusSeconds(session.getLimitedTime());
        //     if (LocalDateTime.now().isAfter(deadline)) {
        //         throw new InvalidRequestException("Đã hết thời gian làm bài");
        //     }
        // }

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
                boolean isCorrect = gradeAnswer(q, ans);
                if (isCorrect) {
                    score++;
                    correctAnswers.add(i);
                } else {
                    wrongAnswers.add(i);
                }

                String answerDataJson = null;
                if (ans.getAnswerData() != null) {
                    try {
                        answerDataJson = objectMapper.writeValueAsString(ans.getAnswerData());
                    } catch (Exception e) {
                        log.warn("Failed to serialize answerData for question {}", q.getQuestionId());
                    }
                }

                answerDetails.add(QuizAnswerDetail.builder()
                        .question(q)
                        .quizSession(session)
                        .answerData(answerDataJson)
                        .isCorrect(isCorrect)
                        .scoreEarned(isCorrect ? 1.0f : 0.0f)
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
        gamificationService.recordProgress(userId.toString(), QuestType.QUIZ);

        int total = questions.size();
        double percentage = total > 0 ? (double) score / total * 100.0 : 0.0;

        return QuizSubmitResponse.builder()
                .resultId(session.getSessionId().toString())
                .score(score)
                .totalQuestions(total)
                .percentage(percentage)
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
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

    @Override
    @Transactional(readOnly = true)
    public QuizSessionDetailResponse getSessionDetail(String sessionId, UUID userId) {
        log.info("getSessionDetail: sessionId={}, userId={}", sessionId, userId);
        UUID sessionUuid = parseUuid(sessionId, "sessionId");

        QuizSession session = quizSessionRepository.findBySessionId(sessionUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz session not found: " + sessionId));

        // Ownership check — skipped for admin (userId == null)
        if (userId != null && !session.getUser().getUid().equals(userId)) {
            throw new InvalidRequestException("Bạn không có quyền xem phiên làm bài này");
        }

        // Must be a completed session
        if (session.getEndTime() == null) {
            throw new InvalidRequestException("Phiên làm bài chưa hoàn thành");
        }

        Quiz quiz = session.getQuiz();

        // Load answer details — keyed by questionId for O(1) lookup
        List<QuizAnswerDetail> details = quizAnswerDetailRepository.findBySessionId(sessionUuid);
        Map<UUID, QuizAnswerDetail> detailByQuestion = details.stream()
                .collect(Collectors.toMap(
                        d -> d.getQuestion().getQuestionId(),
                        d -> d,
                        (a, b) -> a  // keep first if duplicate (should not happen)
                ));

        // Iterate active questions in creation order
        List<Question> questions = questionRepository.findActiveByQuizId(quiz.getQuizId());
        List<QuizSessionDetailResponse.QuestionResultItem> items = questions.stream()
                .map(q -> {
                    QuizAnswerDetail detail = detailByQuestion.get(q.getQuestionId());
                    JsonNode submittedAnswer = null;
                    if (detail != null && detail.getAnswerData() != null) {
                        submittedAnswer = deserializeJsonNode(detail.getAnswerData());
                    }
                    return QuizSessionDetailResponse.QuestionResultItem.builder()
                            .questionId(q.getQuestionId().toString())
                            .content(q.getContent())
                            .questionType(q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MULTIPLE_CHOICE)
                            .questionData(deserializeJsonNode(q.getQuestionData()))
                            .submittedAnswer(submittedAnswer)
                            .isCorrect(detail != null && Boolean.TRUE.equals(detail.getIsCorrect()))
                            .explanation(q.getExplanation())
                            .build();
                })
                .collect(Collectors.toList());

        int score = session.getScore() != null ? session.getScore().intValue() : 0;
        int total = questions.size();
        double percentage = total > 0 ? (double) score / total * 100.0 : 0.0;

        return QuizSessionDetailResponse.builder()
                .sessionId(session.getSessionId().toString())
                .quizId(quiz.getQuizId().toString())
                .quizTitle(quiz.getTitle())
                .score(score)
                .totalQuestions(total)
                .percentage(percentage)
                .limitedTime(session.getLimitedTime())
                .startedAt(session.getStartTime() != null ? session.getStartTime().toString() : null)
                .completedAt(session.getEndTime().toString())
                .questions(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<QuizHistoryResponse> getAllUsersQuizHistory(Pageable pageable) {
        log.info("getAllUsersQuizHistory");
        Page<QuizSession> page = quizSessionRepository.findAllCompleted(pageable);
        return toPaginatedResponse(page.map(this::mapToHistoryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<QuizHistoryResponse> getQuizHistoryByUserId(String userId, Pageable pageable) {
        log.info("getQuizHistoryByUserId: userId={}", userId);
        UUID uid = parseUuid(userId, "userId");
        userRepository.findById(uid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Page<QuizSession> page = quizSessionRepository.findCompletedByUserUidForAdmin(uid, pageable);
        return toPaginatedResponse(page.map(this::mapToHistoryResponse));
    }

    // ==================== Staff ====================

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<QuizStaffResponse> getAllQuizzesForStaff(String search, String era, Pageable pageable, String role) {
        log.info("getAllQuizzesForStaff: search={}, era={}", search, era);
        EventEra eraEnum = parseEra(era);
        Page<Quiz> page = quizRepository.findAllForStaff(normalize(search), eraEnum, false, pageable);
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
            throw new DataConflictException("Quiz với tiêu đề này đã tồn tại: " + request.getTitle());
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
                throw new DataConflictException("Quiz với tiêu đề này đã tồn tại: " + newTitle);
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

        if (question.getQuiz() == null || !question.getQuiz().getQuizId().equals(quizUuid)) {
            throw new InvalidRequestException("Câu hỏi không thuộc về quiz: " + quizId);
        }

        if (request.getContent() != null && !request.getContent().isBlank()) {
            question.setContent(request.getContent());
        }
        validateEffectiveQuestionData(question, request);
        if (request.getExplanation() != null) {
            question.setExplanation(request.getExplanation());
        }
        if (request.getQuestionType() != null) {
            question.setQuestionType(request.getQuestionType());
        }
        if (request.getQuestionData() != null) {
            question.setQuestionData(serializeJsonNode(request.getQuestionData()));
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

        if (question.getQuiz() == null || !question.getQuiz().getQuizId().equals(quizUuid)) {
            throw new InvalidRequestException("Câu hỏi không thuộc về quiz: " + quizId);
        }

        questionRepository.delete(question);
    }

    // ==================== Import ====================

    /**
     * Bulk-import quizzes from a CSV file.
     *
     * <p>CSV format (header required):
     * <pre>title,contextId,level,questionContent,option1,option2,option3,option4,correctAnswer,explanation</pre>
     *
     * <p>Rows sharing the same {@code title} are grouped into one quiz.
     * A single {@code @Transactional} session is used for the entire call so that
     * all repository calls share the same Hibernate session and configured DB schema.
     * Per-quiz isolation is handled by the try/catch in the processing loop.
     */
    @Override
    @Transactional
    public QuizImportResponse importQuizzesFromCsv(MultipartFile file, UUID userId) {
        log.info("importQuizzesFromCsv: filename={}, size={}", file.getOriginalFilename(), file.getSize());

        // ── 1. Basic file validation ──────────────────────────────────────────
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File CSV không được để trống");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".csv")) {
            throw new InvalidRequestException("Chỉ chấp nhận file .csv");
        }

        // ── 2. Required CSV headers ───────────────────────────────────────────
        List<String> requiredHeaders = Arrays.asList(
                "title", "contextId", "level",
                "questionContent", "option1", "option2", "option3", "option4",
                "correctAnswer", "explanation"
        );

        // ── 3. Parse and group by title ───────────────────────────────────────
        Map<String, List<CSVRecord>> grouped = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);

            // Validate that all required headers are present
            for (String header : requiredHeaders) {
                if (!parser.getHeaderMap().containsKey(header)) {
                    throw new InvalidRequestException(
                            "File CSV thiếu cột tiêu đề bắt buộc: '" + header + "'");
                }
            }

            for (CSVRecord record : parser) {
                String title = record.get("title").trim();
                if (title.isBlank()) {
                    continue; // skip rows with no title
                }
                grouped.computeIfAbsent(title, k -> new ArrayList<>()).add(record);
            }

        } catch (InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse CSV file", e);
            throw new SystemException("Không thể đọc file CSV: " + e.getMessage());
        }

        if (grouped.isEmpty()) {
            throw new InvalidRequestException("File CSV không chứa hàng dữ liệu hợp lệ nào");
        }

        // ── 4. Process each quiz group ────────────────────────────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        int totalAttempted = grouped.size();
        int successCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();
        List<QuizStaffResponse> imported = new ArrayList<>();

        for (Map.Entry<String, List<CSVRecord>> entry : grouped.entrySet()) {
            String title = entry.getKey();
            List<CSVRecord> rows = entry.getValue();

            try {
                QuizStaffResponse created = saveQuizGroup(title, rows, user);
                imported.add(created);
                successCount++;
            } catch (DataConflictException e) {
                errors.add("Quiz '" + title + "' skipped — duplicate title: " + e.getMessage());
                skippedCount++;
            } catch (InvalidRequestException e) {
                errors.add("Quiz '" + title + "' skipped — validation error: " + e.getMessage());
                skippedCount++;
            } catch (ResourceNotFoundException e) {
                errors.add("Quiz '" + title + "' skipped — " + e.getMessage());
                skippedCount++;
            } catch (Exception e) {
                log.error("Unexpected error saving quiz group '{}'", title, e);
                errors.add("Quiz '" + title + "' skipped — unexpected error: " + e.getMessage());
                skippedCount++;
            }
        }

        log.info("importQuizzesFromCsv complete: attempted={} success={} skipped={}",
                totalAttempted, successCount, skippedCount);

        return QuizImportResponse.builder()
                .totalQuizzesAttempted(totalAttempted)
                .successCount(successCount)
                .skippedCount(skippedCount)
                .errors(errors)
                .imported(imported)
                .build();
    }

    /**
     * Validates and persists one quiz group (quiz + questions).
     * Called from within the outer {@code @Transactional} session in
     * {@link #importQuizzesFromCsv}, so all DB access shares the same
     * Hibernate session and schema context.
     */
    private QuizStaffResponse saveQuizGroup(String title, List<CSVRecord> rows, User user) {
        // Validate quiz-level fields from the first row
        CSVRecord first = rows.get(0);

        String contextIdStr = first.get("contextId").trim();
        UUID contextUuid;
        try {
            contextUuid = UUID.fromString(contextIdStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("contextId không phải là UUID hợp lệ: '" + contextIdStr + "'");
        }

        HistoricalContext context = historicalContextRepository.findById(contextUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "HistoricalContext not found for contextId: " + contextIdStr));

        QuizLevel level = parseLevel(first.get("level").trim());

        // Duplicate-title check
        if (quizRepository.existsByTitleIgnoreCase(title)) {
            throw new DataConflictException("Quiz với tiêu đề '" + title + "' already exists");
        }

        // Build and save the Quiz entity (draft by default)
        Quiz quiz = Quiz.builder()
                .title(title)
                .level(level)
                .historicalContext(context)
                .createdBy(user)
                .isPublished(false)
                .build();
        quiz = quizRepository.save(quiz);

        // Build and save questions
        for (int i = 0; i < rows.size(); i++) {
            CSVRecord row = rows.get(i);
            int rowNum = i + 1;

            String questionContent = row.get("questionContent").trim();
            if (questionContent.isBlank()) {
                throw new InvalidRequestException(
                        "Hàng " + rowNum + ": questionContent must not be empty");
            }

            String[] rawOptions = {
                    row.get("option1").trim(),
                    row.get("option2").trim(),
                    row.get("option3").trim(),
                    row.get("option4").trim()
            };
            for (int o = 0; o < rawOptions.length; o++) {
                if (rawOptions[o].isBlank()) {
                    throw new InvalidRequestException(
                            "Hàng " + rowNum + ": option" + (o + 1) + " must not be empty");
                }
            }

            int correctIndex;
            try {
                correctIndex = Integer.parseInt(row.get("correctAnswer").trim());
            } catch (NumberFormatException e) {
                throw new InvalidRequestException(
                        "Hàng " + rowNum + ": correctAnswer must be an integer (0-3)");
            }
            if (correctIndex < 0 || correctIndex > 3) {
                throw new InvalidRequestException(
                        "Hàng " + rowNum + ": correctAnswer must be between 0 and 3, got: " + correctIndex);
            }

            // Build typed questionData from CSV columns
            List<QuestionOption> optionList = new ArrayList<>();
            for (int o = 0; o < rawOptions.length; o++) {
                optionList.add(QuestionOption.builder().id(String.valueOf(o)).text(rawOptions[o]).build());
            }
            MultipleChoiceQuestionData mcData = MultipleChoiceQuestionData.builder()
                    .options(optionList)
                    .correctOptionIds(List.of(String.valueOf(correctIndex)))
                    .build();

            String explanation = row.get("explanation").trim();

            QuestionRequest qr = QuestionRequest.builder()
                    .content(questionContent)
                    .questionType(QuestionType.MULTIPLE_CHOICE)
                    .questionData(objectMapper.valueToTree(mcData))
                    .explanation(explanation.isBlank() ? null : explanation)
                    .build();

            questionRepository.save(buildQuestion(qr, quiz));
        }

        // Reload to populate questions list
        quiz = quizRepository.findById(quiz.getQuizId()).orElseThrow();
        return mapToStaffResponse(quiz);
    }

    // ==================== Private Helpers ====================

    /**
     * Re-validates the (type, data) pair a partial update would leave on the question,
     * so a type-only change cannot keep data shaped for the previous type.
     */
    private void validateEffectiveQuestionData(Question question, QuestionRequest request) {
        if (request.getQuestionType() == null && request.getQuestionData() == null) {
            return;
        }
        QuestionType effectiveType = request.getQuestionType() != null
                ? request.getQuestionType()
                : (question.getQuestionType() != null ? question.getQuestionType() : QuestionType.MULTIPLE_CHOICE);
        JsonNode effectiveData = request.getQuestionData() != null
                ? request.getQuestionData()
                : deserializeJsonNode(question.getQuestionData());
        questionDataValidator.validate(effectiveType, effectiveData);
    }

    private Question buildQuestion(QuestionRequest request, Quiz quiz) {
        QuestionType type = request.getQuestionType() != null
                ? request.getQuestionType() : QuestionType.MULTIPLE_CHOICE;
        questionDataValidator.validate(type, request.getQuestionData());
        return Question.builder()
                .quiz(quiz)
                .content(request.getContent())
                .explanation(request.getExplanation())
                .questionType(type)
                .questionData(serializeJsonNode(request.getQuestionData()))
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
                .questions(questions.stream().map(this::mapToStaffQuestionDetail).collect(Collectors.toList()))
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
                .questionType(q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MULTIPLE_CHOICE)
                .questionData(sanitizeQuestionDataForLearner(q))
                .explanation(q.getExplanation())
                .build();
    }

    private QuestionResponse mapToStaffQuestionDetail(Question q) {
        return QuestionResponse.builder()
                .questionId(q.getQuestionId().toString())
                .content(q.getContent())
                .questionType(q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MULTIPLE_CHOICE)
                .questionData(deserializeJsonNode(q.getQuestionData()))
                .explanation(q.getExplanation())
                .build();
    }

    /** Strip correct-answer fields before sending questionData to learners. */
    private JsonNode sanitizeQuestionDataForLearner(Question q) {
        if (q.getQuestionData() == null) return null;
        QuestionType type = q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MULTIPLE_CHOICE;
        return switch (type) {
            case FILL_IN_BLANK ->
                // acceptedAnswers must not leak; learner needs only content + type
                null;
            case MATCHING -> {
                // Keep left/right items for display, strip correctPairs
                try {
                    MatchingQuestionData data = objectMapper.readValue(q.getQuestionData(), MatchingQuestionData.class);
                    MatchingQuestionData safe = MatchingQuestionData.builder()
                            .leftItems(data.getLeftItems())
                            .rightItems(data.getRightItems())
                            .build();
                    yield objectMapper.valueToTree(safe);
                } catch (Exception e) {
                    log.warn("Failed to sanitize matching questionData for question {}", q.getQuestionId());
                    yield null;
                }
            }
            case MULTIPLE_CHOICE -> {
                // Keep options for display, strip correctOptionIds
                try {
                    JsonNode node = objectMapper.readTree(q.getQuestionData());
                    ObjectNode safe = objectMapper.createObjectNode();
                    if (node.has("options")) safe.set("options", node.get("options"));
                    yield safe;
                } catch (Exception e) {
                    log.warn("Failed to sanitize MC questionData for question {}", q.getQuestionId());
                    yield null;
                }
            }
        };
    }

    private QuestionBankResponse mapToBankResponse(Question q) {
        return QuestionBankResponse.builder()
                .questionId(q.getQuestionId().toString())
                .content(q.getContent())
                .explanation(q.getExplanation())
                .questionType(q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MULTIPLE_CHOICE)
                .questionData(deserializeJsonNode(q.getQuestionData()))
                .assignedToQuiz(q.getQuiz() != null)
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

    private String serializeJsonNode(JsonNode node) {
        if (node == null) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Failed to serialize JsonNode", e);
            throw new InvalidRequestException("Định dạng dữ liệu câu hỏi không hợp lệ");
        }
    }

    private JsonNode deserializeJsonNode(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to deserialize JsonNode: {}", json);
            return null;
        }
    }

    // ==================== Answer Grading ====================

    private boolean gradeAnswer(Question question, AnswerDetailRequest answer) {
        QuestionType type = question.getQuestionType() != null
                ? question.getQuestionType() : QuestionType.MULTIPLE_CHOICE;
        return switch (type) {
            case FILL_IN_BLANK -> gradeFillInBlank(question, answer);
            case MATCHING -> gradeMatching(question, answer);
            case MULTIPLE_CHOICE -> gradeMultipleChoice(question, answer);
        };
    }

    private boolean gradeMultipleChoice(Question question, AnswerDetailRequest answer) {
        if (question.getQuestionData() == null || answer.getAnswerData() == null) return false;
        try {
            MultipleChoiceQuestionData qData = objectMapper.readValue(
                    question.getQuestionData(), MultipleChoiceQuestionData.class);
            MultipleChoiceAnswerData aData = objectMapper.treeToValue(
                    answer.getAnswerData(), MultipleChoiceAnswerData.class);
            if (aData.getSelectedOptionIds() == null || qData.getCorrectOptionIds() == null) return false;
            return aData.getSelectedOptionIds().size() == qData.getCorrectOptionIds().size()
                    && aData.getSelectedOptionIds().containsAll(qData.getCorrectOptionIds());
        } catch (Exception e) {
            log.warn("Failed to grade MC for question {}", question.getQuestionId(), e);
            return false;
        }
    }

    private boolean gradeFillInBlank(Question question, AnswerDetailRequest answer) {
        if (question.getQuestionData() == null || answer.getAnswerData() == null) return false;
        try {
            FillInBlankQuestionData qData = objectMapper.readValue(
                    question.getQuestionData(), FillInBlankQuestionData.class);
            FillInBlankAnswerData aData = objectMapper.treeToValue(
                    answer.getAnswerData(), FillInBlankAnswerData.class);
            if (aData.getAnswers() == null || aData.getAnswers().isEmpty()) return false;

            String submitted = aData.getAnswers().get(0);
            if (qData.isTrimWhitespace() && submitted != null) submitted = submitted.trim();

            if (submitted == null) return false;
            for (String accepted : qData.getAcceptedAnswers()) {
                String target = qData.isTrimWhitespace() ? accepted.trim() : accepted;
                boolean match = qData.isCaseSensitive()
                        ? submitted.equals(target)
                        : submitted.equalsIgnoreCase(target);
                if (match) return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to grade FillInBlank for question {}", question.getQuestionId(), e);
            return false;
        }
    }

    private boolean gradeMatching(Question question, AnswerDetailRequest answer) {
        if (question.getQuestionData() == null || answer.getAnswerData() == null) return false;
        try {
            MatchingQuestionData qData = objectMapper.readValue(
                    question.getQuestionData(), MatchingQuestionData.class);
            MatchingAnswerData aData = objectMapper.treeToValue(
                    answer.getAnswerData(), MatchingAnswerData.class);
            if (aData.getPairs() == null || qData.getCorrectPairs() == null) return false;
            if (aData.getPairs().size() != qData.getCorrectPairs().size()) return false;

            // All-or-nothing: every correct pair must appear exactly in submitted pairs
            for (MatchingPair correct : qData.getCorrectPairs()) {
                boolean found = aData.getPairs().stream().anyMatch(p ->
                        correct.getLeftId().equals(p.getLeftId())
                        && correct.getRightId().equals(p.getRightId()));
                if (!found) return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to grade Matching for question {}", question.getQuestionId(), e);
            return false;
        }
    }

    // ==================== Question Bank ====================

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<QuestionBankResponse> getQuestionBank(String questionType, String search, Pageable pageable) {
        QuestionType typeEnum = null;
        if (questionType != null && !questionType.isBlank()) {
            try {
                typeEnum = QuestionType.valueOf(questionType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("Loại câu hỏi không hợp lệ: " + questionType);
            }
        }
        Page<Question> page = questionRepository.findAllForBank(typeEnum, normalize(search), pageable);
        return toPaginatedResponse(page.map(this::mapToBankResponse));
    }

    @Override
    @Transactional
    public QuestionBankResponse createBankQuestion(QuestionRequest request) {
        log.info("createBankQuestion: type={}", request.getQuestionType());
        Question question = buildQuestion(request, null);
        question = questionRepository.save(question);
        return mapToBankResponse(question);
    }

    @Override
    @Transactional
    public QuestionBankResponse updateBankQuestion(String questionId, QuestionRequest request) {
        log.info("updateBankQuestion: questionId={}", questionId);
        Question question = questionRepository.findById(parseUuid(questionId, "questionId"))
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        if (question.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Question has been deleted: " + questionId);
        }
        if (request.getContent() != null && !request.getContent().isBlank()) {
            question.setContent(request.getContent());
        }
        validateEffectiveQuestionData(question, request);
        if (request.getExplanation() != null) {
            question.setExplanation(request.getExplanation());
        }
        if (request.getQuestionType() != null) {
            question.setQuestionType(request.getQuestionType());
        }
        if (request.getQuestionData() != null) {
            question.setQuestionData(serializeJsonNode(request.getQuestionData()));
        }
        question = questionRepository.save(question);
        return mapToBankResponse(question);
    }

    @Override
    @Transactional
    public void softDeleteBankQuestion(String questionId) {
        log.info("softDeleteBankQuestion: questionId={}", questionId);
        Question question = questionRepository.findById(parseUuid(questionId, "questionId"))
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        question.setDeletedAt(LocalDateTime.now());
        questionRepository.save(question);
    }

    @Override
    @Transactional
    public void attachQuestionToQuiz(String quizId, String questionId) {
        log.info("attachQuestionToQuiz: quizId={}, questionId={}", quizId, questionId);
        UUID quizUuid = parseUuid(quizId, "quizId");
        UUID questionUuid = parseUuid(questionId, "questionId");

        Quiz quiz = quizRepository.findById(quizUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        Question question = questionRepository.findById(questionUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));

        if (question.getDeletedAt() != null) {
            throw new InvalidRequestException("Không thể thêm câu hỏi đã bị xóa vào quiz");
        }
        if (quizQuestionRepository.existsByQuiz_QuizIdAndQuestion_QuestionId(quizUuid, questionUuid)) {
            throw new DataConflictException("Câu hỏi đã được liên kết với quiz này");
        }

        int nextOrder = quizQuestionRepository.findMaxOrderIndex(quizUuid) + 1;
        QuizQuestion link = QuizQuestion.builder()
                .quiz(quiz)
                .question(question)
                .orderIndex(nextOrder)
                .build();
        try {
            quizQuestionRepository.save(link);
        } catch (DataIntegrityViolationException e) {
            throw new DataConflictException("Câu hỏi đã được liên kết với quiz này");
        }
    }

    @Override
    @Transactional
    public void unlinkQuestionFromQuiz(String quizId, String questionId) {
        log.info("unlinkQuestionFromQuiz: quizId={}, questionId={}", quizId, questionId);
        UUID quizUuid = parseUuid(quizId, "quizId");
        UUID questionUuid = parseUuid(questionId, "questionId");

        quizRepository.findById(quizUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));
        questionRepository.findById(questionUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));

        QuizQuestionId linkId = new QuizQuestionId(quizUuid, questionUuid);
        QuizQuestion link = quizQuestionRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Câu hỏi chưa được liên kết với quiz này"));
        quizQuestionRepository.delete(link);
    }

    private UUID parseUuid(String id, String fieldName) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Không hợp lệ " + fieldName + " không hợp lệ: " + id);
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
            throw new InvalidRequestException("Giá trị thời đại (era) không hợp lệ: " + era + ". Giá trị hợp lệ: ANCIENT, MEDIEVAL, MODERN, CONTEMPORARY");
        }
    }

    private QuizLevel parseLevel(String level) {
        if (level == null || level.isBlank()) {
            throw new InvalidRequestException("Yêu cầu cấp độ (Level)");
        }
        try {
            return QuizLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Giá trị cấp độ (level) không hợp lệ: " + level + ". Giá trị hợp lệ: EASY, MEDIUM, HARD");
        }
    }
}
