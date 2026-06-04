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
import com.historytalk.exception.SystemException;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuestionRepository;
import com.historytalk.repository.QuizAnswerDetailRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.UserRepository;
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
        // if (session.getLimitedTime() != null && session.getStartTime() != null) {
        //     LocalDateTime deadline = session.getStartTime().plusSeconds(session.getLimitedTime());
        //     if (LocalDateTime.now().isAfter(deadline)) {
        //         throw new InvalidRequestException("Time limit expired");
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
            throw new InvalidRequestException("You are not authorized to view this quiz session");
        }

        // Must be a completed session
        if (session.getEndTime() == null) {
            throw new InvalidRequestException("Session not completed yet");
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
                    return QuizSessionDetailResponse.QuestionResultItem.builder()
                            .questionId(q.getQuestionId().toString())
                            .content(q.getContent())
                            .options(deserializeOptions(q.getOptions()))
                            .correctAnswer(q.getCorrectAnswer() != null ? q.getCorrectAnswer() : -1)
                            .selectedAnswer(detail != null ? detail.getSelectedOption() : null)
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
            throw new InvalidRequestException("CSV file must not be empty");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".csv")) {
            throw new InvalidRequestException("Only .csv files are accepted");
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
                            "CSV is missing required header column: '" + header + "'");
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
            throw new SystemException("Failed to read CSV file: " + e.getMessage());
        }

        if (grouped.isEmpty()) {
            throw new InvalidRequestException("CSV file contains no valid data rows");
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
            throw new InvalidRequestException("contextId is not a valid UUID: '" + contextIdStr + "'");
        }

        HistoricalContext context = historicalContextRepository.findById(contextUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "HistoricalContext not found for contextId: " + contextIdStr));

        QuizLevel level = parseLevel(first.get("level").trim());

        // Duplicate-title check
        if (quizRepository.existsByTitleIgnoreCase(title)) {
            throw new DataConflictException("Quiz with title '" + title + "' already exists");
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
                        "Row " + rowNum + ": questionContent must not be empty");
            }

            List<String> options = Arrays.asList(
                    row.get("option1").trim(),
                    row.get("option2").trim(),
                    row.get("option3").trim(),
                    row.get("option4").trim()
            );
            for (int o = 0; o < options.size(); o++) {
                if (options.get(o).isBlank()) {
                    throw new InvalidRequestException(
                            "Row " + rowNum + ": option" + (o + 1) + " must not be empty");
                }
            }

            int correctAnswer;
            try {
                correctAnswer = Integer.parseInt(row.get("correctAnswer").trim());
            } catch (NumberFormatException e) {
                throw new InvalidRequestException(
                        "Row " + rowNum + ": correctAnswer must be an integer (0-3)");
            }
            if (correctAnswer < 0 || correctAnswer > 3) {
                throw new InvalidRequestException(
                        "Row " + rowNum + ": correctAnswer must be between 0 and 3, got: " + correctAnswer);
            }

            String explanation = row.get("explanation").trim();

            QuestionRequest qr = QuestionRequest.builder()
                    .content(questionContent)
                    .options(options)
                    .correctAnswer(correctAnswer)
                    .explanation(explanation.isBlank() ? null : explanation)
                    .build();

            questionRepository.save(buildQuestion(qr, quiz));
        }

        // Reload to populate questions list
        quiz = quizRepository.findById(quiz.getQuizId()).orElseThrow();
        return mapToStaffResponse(quiz);
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
