package com.historytalk.service.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.AnswerDetailRequest;
import com.historytalk.dto.quiz.AttachQuestionRequest;
import com.historytalk.dto.quiz.QuestionBankResponse;
import com.historytalk.dto.quiz.QuestionRequest;
import com.historytalk.dto.quiz.QuizSubmitRequest;
import com.historytalk.dto.quiz.QuizSubmitResponse;
import com.historytalk.dto.quiz.quizdata.FillInBlankAnswerData;
import com.historytalk.dto.quiz.quizdata.FillInBlankQuestionData;
import com.historytalk.dto.quiz.quizdata.MatchingAnswerData;
import com.historytalk.dto.quiz.quizdata.MatchingPair;
import com.historytalk.dto.quiz.quizdata.MatchingQuestionData;
import com.historytalk.dto.quiz.quizdata.MatchingItem;
import com.historytalk.dto.quiz.quizdata.MultipleChoiceAnswerData;
import com.historytalk.dto.quiz.quizdata.MultipleChoiceQuestionData;
import com.historytalk.dto.quiz.quizdata.QuestionOption;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.entity.quiz.Question;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.quiz.QuizQuestion;
import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.repository.*;
import com.historytalk.service.gamification.GamificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceImplTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizSessionRepository quizSessionRepository;
    @Mock private QuizAnswerDetailRepository quizAnswerDetailRepository;
    @Mock private UserRepository userRepository;
    @Mock private HistoricalContextRepository historicalContextRepository;
    @Mock private GamificationService gamificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QuizServiceImpl quizService;

    @BeforeEach
    void setUp() {
        quizService = new QuizServiceImpl(
                quizRepository, questionRepository, quizSessionRepository,
                quizAnswerDetailRepository, userRepository, historicalContextRepository,
                objectMapper, gamificationService,
                quizQuestionRepository, new QuestionDataValidator(objectMapper)
        );
    }

    // ==================== FillInBlank grading ====================

    @Test
    void submitQuiz_FillInBlank_caseInsensitiveMatch_scoreOne() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        FillInBlankQuestionData qData = new FillInBlankQuestionData(
                List.of("938", "năm 938"), false, true);
        String questionDataJson = objectMapper.writeValueAsString(qData);

        Question question = Question.builder()
                .questionId(questionId)
                .content("Năm nào diễn ra Trận Bạch Đằng?")
                .questionType(QuestionType.FILL_IN_BLANK)
                .questionData(questionDataJson)
                .build();

        User user = new User();
        user.setUid(userId);
        Quiz quiz = Quiz.builder().quizId(quizId).title("T").build();
        QuizSession session = QuizSession.builder()
                .sessionId(sessionId).quiz(quiz).user(user)
                .startTime(LocalDateTime.now()).build();

        when(quizSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findActiveByQuizId(quizId)).thenReturn(List.of(question));
        when(quizSessionRepository.save(any())).thenReturn(session);

        FillInBlankAnswerData ansData = new FillInBlankAnswerData(List.of("938"));
        AnswerDetailRequest answer = AnswerDetailRequest.builder()
                .questionId(questionId.toString())
                .answerData(objectMapper.valueToTree(ansData))
                .build();

        QuizSubmitResponse response = quizService.submitQuiz(
                QuizSubmitRequest.builder().sessionId(sessionId.toString()).answers(List.of(answer)).build(),
                userId);

        assertThat(response.getScore()).isEqualTo(1);
    }

    @Test
    void submitQuiz_FillInBlank_wrongAnswer_scoreZero() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        FillInBlankQuestionData qData = new FillInBlankQuestionData(
                List.of("938"), false, true);
        String questionDataJson = objectMapper.writeValueAsString(qData);

        Question question = Question.builder()
                .questionId(questionId).content("Q?")
                .questionType(QuestionType.FILL_IN_BLANK)
                .questionData(questionDataJson).build();

        User user = new User();
        user.setUid(userId);
        Quiz quiz = Quiz.builder().quizId(quizId).title("T").build();
        QuizSession session = QuizSession.builder()
                .sessionId(sessionId).quiz(quiz).user(user)
                .startTime(LocalDateTime.now()).build();

        when(quizSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findActiveByQuizId(quizId)).thenReturn(List.of(question));
        when(quizSessionRepository.save(any())).thenReturn(session);

        FillInBlankAnswerData ansData = new FillInBlankAnswerData(List.of("939"));
        AnswerDetailRequest answer = AnswerDetailRequest.builder()
                .questionId(questionId.toString())
                .answerData(objectMapper.valueToTree(ansData)).build();

        QuizSubmitResponse response = quizService.submitQuiz(
                QuizSubmitRequest.builder().sessionId(sessionId.toString()).answers(List.of(answer)).build(),
                userId);

        assertThat(response.getScore()).isEqualTo(0);
    }

    // ==================== Matching grading (all-or-nothing) ====================

    @Test
    void submitQuiz_Matching_allCorrect_scoreOne() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        MatchingQuestionData qData = new MatchingQuestionData(
                List.of(new MatchingItem("L1", "Ngô Quyền")),
                List.of(new MatchingItem("R1", "Bạch Đằng 938")),
                List.of(new MatchingPair("L1", "R1")));
        String questionDataJson = objectMapper.writeValueAsString(qData);

        Question question = Question.builder()
                .questionId(questionId).content("Nối đôi?")
                .questionType(QuestionType.MATCHING)
                .questionData(questionDataJson).build();

        User user = new User();
        user.setUid(userId);
        Quiz quiz = Quiz.builder().quizId(quizId).title("T").build();
        QuizSession session = QuizSession.builder()
                .sessionId(sessionId).quiz(quiz).user(user)
                .startTime(LocalDateTime.now()).build();

        when(quizSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findActiveByQuizId(quizId)).thenReturn(List.of(question));
        when(quizSessionRepository.save(any())).thenReturn(session);

        MatchingAnswerData ansData = new MatchingAnswerData(List.of(new MatchingPair("L1", "R1")));
        AnswerDetailRequest answer = AnswerDetailRequest.builder()
                .questionId(questionId.toString())
                .answerData(objectMapper.valueToTree(ansData)).build();

        QuizSubmitResponse response = quizService.submitQuiz(
                QuizSubmitRequest.builder().sessionId(sessionId.toString()).answers(List.of(answer)).build(),
                userId);

        assertThat(response.getScore()).isEqualTo(1);
    }

    @Test
    void submitQuiz_Matching_wrongPair_scoreZero() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        MatchingQuestionData qData = new MatchingQuestionData(
                List.of(new MatchingItem("L1", "A"), new MatchingItem("L2", "B")),
                List.of(new MatchingItem("R1", "X"), new MatchingItem("R2", "Y")),
                List.of(new MatchingPair("L1", "R1"), new MatchingPair("L2", "R2")));
        String questionDataJson = objectMapper.writeValueAsString(qData);

        Question question = Question.builder()
                .questionId(questionId).content("Nối đôi?")
                .questionType(QuestionType.MATCHING)
                .questionData(questionDataJson).build();

        User user = new User();
        user.setUid(userId);
        Quiz quiz = Quiz.builder().quizId(quizId).title("T").build();
        QuizSession session = QuizSession.builder()
                .sessionId(sessionId).quiz(quiz).user(user)
                .startTime(LocalDateTime.now()).build();

        when(quizSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findActiveByQuizId(quizId)).thenReturn(List.of(question));
        when(quizSessionRepository.save(any())).thenReturn(session);

        // One pair wrong (L1→R2 instead of L1→R1)
        MatchingAnswerData ansData = new MatchingAnswerData(
                List.of(new MatchingPair("L1", "R2"), new MatchingPair("L2", "R1")));
        AnswerDetailRequest answer = AnswerDetailRequest.builder()
                .questionId(questionId.toString())
                .answerData(objectMapper.valueToTree(ansData)).build();

        QuizSubmitResponse response = quizService.submitQuiz(
                QuizSubmitRequest.builder().sessionId(sessionId.toString()).answers(List.of(answer)).build(),
                userId);

        assertThat(response.getScore()).isEqualTo(0);
    }

    // ==================== Multiple Choice grading ====================

    @Test
    void submitQuiz_MC_correctAnswer_scoreOne() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        MultipleChoiceQuestionData qData = MultipleChoiceQuestionData.builder()
                .options(List.of(
                        QuestionOption.builder().id("0").text("938").build(),
                        QuestionOption.builder().id("1").text("939").build()))
                .correctOptionIds(List.of("0"))
                .build();
        String questionDataJson = objectMapper.writeValueAsString(qData);

        Question question = Question.builder()
                .questionId(questionId)
                .content("Năm Bạch Đằng?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionData(questionDataJson)
                .build();

        User user = new User();
        user.setUid(userId);
        Quiz quiz = Quiz.builder().quizId(quizId).title("T").build();
        QuizSession session = QuizSession.builder()
                .sessionId(sessionId).quiz(quiz).user(user)
                .startTime(LocalDateTime.now()).build();

        when(quizSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findActiveByQuizId(quizId)).thenReturn(List.of(question));
        when(quizSessionRepository.save(any())).thenReturn(session);

        MultipleChoiceAnswerData ansData = MultipleChoiceAnswerData.builder()
                .selectedOptionIds(List.of("0"))
                .build();
        AnswerDetailRequest answer = AnswerDetailRequest.builder()
                .questionId(questionId.toString())
                .answerData(objectMapper.valueToTree(ansData))
                .build();

        QuizSubmitResponse response = quizService.submitQuiz(
                QuizSubmitRequest.builder().sessionId(sessionId.toString()).answers(List.of(answer)).build(),
                userId);

        assertThat(response.getScore()).isEqualTo(1);
    }

    @Test
    void submitQuiz_MC_wrongAnswer_scoreZero() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        MultipleChoiceQuestionData qData = MultipleChoiceQuestionData.builder()
                .options(List.of(
                        QuestionOption.builder().id("0").text("938").build(),
                        QuestionOption.builder().id("1").text("939").build()))
                .correctOptionIds(List.of("0"))
                .build();
        String questionDataJson = objectMapper.writeValueAsString(qData);

        Question question = Question.builder()
                .questionId(questionId)
                .content("Năm Bạch Đằng?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionData(questionDataJson)
                .build();

        User user = new User();
        user.setUid(userId);
        Quiz quiz = Quiz.builder().quizId(quizId).title("T").build();
        QuizSession session = QuizSession.builder()
                .sessionId(sessionId).quiz(quiz).user(user)
                .startTime(LocalDateTime.now()).build();

        when(quizSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findActiveByQuizId(quizId)).thenReturn(List.of(question));
        when(quizSessionRepository.save(any())).thenReturn(session);

        MultipleChoiceAnswerData ansData = MultipleChoiceAnswerData.builder()
                .selectedOptionIds(List.of("1"))
                .build();
        AnswerDetailRequest answer = AnswerDetailRequest.builder()
                .questionId(questionId.toString())
                .answerData(objectMapper.valueToTree(ansData))
                .build();

        QuizSubmitResponse response = quizService.submitQuiz(
                QuizSubmitRequest.builder().sessionId(sessionId.toString()).answers(List.of(answer)).build(),
                userId);

        assertThat(response.getScore()).isEqualTo(0);
    }

    // ==================== Question Bank ====================

    @Test
    void createBankQuestion_FillInBlank_savesWithNullQuiz() throws Exception {
        FillInBlankQuestionData qData = new FillInBlankQuestionData(
                List.of("Ngô Quyền"), false, true);

        QuestionRequest request = QuestionRequest.builder()
                .content("Ai chiến thắng Bạch Đằng 938?")
                .questionType(QuestionType.FILL_IN_BLANK)
                .questionData(objectMapper.valueToTree(qData))
                .build();

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        // Simulate @UuidGenerator: return same question but with a UUID (as DB would assign)
        when(questionRepository.save(captor.capture())).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            return Question.builder()
                    .questionId(UUID.randomUUID())
                    .content(q.getContent())
                    .questionType(q.getQuestionType())
                    .questionData(q.getQuestionData())
                    .quiz(q.getQuiz())
                    .explanation(q.getExplanation())
                    .build();
        });

        quizService.createBankQuestion(request);

        Question saved = captor.getValue();
        assertThat(saved.getQuiz()).isNull();
        assertThat(saved.getQuestionType()).isEqualTo(QuestionType.FILL_IN_BLANK);
        assertThat(saved.getQuestionData()).isNotNull();
    }

    @Test
    void attachQuestionToQuiz_validQuestion_savesLink() {
        UUID quizId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID contextId = UUID.randomUUID();

        var context = com.historytalk.entity.historicalContext.HistoricalContext.builder()
                .contextId(contextId).build();
        Quiz quiz = Quiz.builder().quizId(quizId).historicalContext(context).build();
        Question question = Question.builder().questionId(questionId).quiz(null).build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(quizQuestionRepository.existsByQuiz_QuizIdAndQuestion_QuestionId(quizId, questionId))
                .thenReturn(false);
        when(quizQuestionRepository.findMaxOrderIndex(quizId)).thenReturn(-1);
        when(quizQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        quizService.attachQuestionToQuiz(quizId.toString(), questionId.toString());

        ArgumentCaptor<QuizQuestion> captor = ArgumentCaptor.forClass(QuizQuestion.class);
        verify(quizQuestionRepository).save(captor.capture());
        assertThat(captor.getValue().getQuiz().getQuizId()).isEqualTo(quizId);
        assertThat(captor.getValue().getQuestion().getQuestionId()).isEqualTo(questionId);
    }

    @Test
    void attachQuestionToQuiz_duplicateLink_throwsDataConflict() {
        UUID quizId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        UUID contextId = UUID.randomUUID();
        var context = com.historytalk.entity.historicalContext.HistoricalContext.builder()
                .contextId(contextId).build();
        Quiz quiz = Quiz.builder().quizId(quizId).historicalContext(context).build();
        Question question = Question.builder().questionId(questionId).build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(quizQuestionRepository.existsByQuiz_QuizIdAndQuestion_QuestionId(quizId, questionId))
                .thenReturn(true);

        assertThatThrownBy(() -> quizService.attachQuestionToQuiz(quizId.toString(), questionId.toString()))
                .isInstanceOf(DataConflictException.class);
    }

    @Test
    void unlinkQuestionFromQuiz_existingLink_deletesLink() {
        UUID quizId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        UUID contextId = UUID.randomUUID();
        var context = com.historytalk.entity.historicalContext.HistoricalContext.builder()
                .contextId(contextId).build();
        Quiz quiz = Quiz.builder().quizId(quizId).historicalContext(context).build();
        Question question = Question.builder().questionId(questionId).build();
        QuizQuestion link = QuizQuestion.builder().quiz(quiz).question(question).orderIndex(0).build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(quizQuestionRepository.findById(any())).thenReturn(Optional.of(link));

        quizService.unlinkQuestionFromQuiz(quizId.toString(), questionId.toString());

        verify(quizQuestionRepository).delete(link);
    }
}
