package com.historytalk.service.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.QuizImportResponse;
import com.historytalk.dto.quiz.quizdata.FillInBlankQuestionData;
import com.historytalk.dto.quiz.quizdata.MatchingQuestionData;
import com.historytalk.dto.quiz.quizdata.MultipleChoiceQuestionData;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.entity.enums.QuestionDifficulty;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.quiz.Question;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.quiz.QuizQuestion;
import com.historytalk.entity.user.User;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuestionRepository;
import com.historytalk.repository.QuizAnswerDetailRepository;
import com.historytalk.repository.QuizQuestionRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.gamification.GamificationService;
import com.historytalk.service.quiz.csv.FillInBlankCsvParser;
import com.historytalk.service.quiz.csv.MatchingCsvParser;
import com.historytalk.service.quiz.csv.MultipleChoiceCsvParser;
import com.historytalk.service.quiz.csv.QuizCsvFileReader;
import com.historytalk.service.quiz.csv.QuizCsvRowParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuizServiceImplImportTest {

    private static final String HEADER = "title,contextId,level,questionType,questionContent,"
            + "option1,option2,option3,option4,correctAnswer,explanation\n";

    @Mock private QuizRepository quizRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizSessionRepository quizSessionRepository;
    @Mock private QuizAnswerDetailRepository quizAnswerDetailRepository;
    @Mock private UserRepository userRepository;
    @Mock private HistoricalContextRepository historicalContextRepository;
    @Mock private GamificationService gamificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();
    private final UUID contextId = UUID.randomUUID();
    private HistoricalContext context;
    private QuizServiceImpl quizService;

    @BeforeEach
    void setUp() {
        quizService = new QuizServiceImpl(
                quizRepository, questionRepository, quizSessionRepository,
                quizAnswerDetailRepository, userRepository, historicalContextRepository,
                objectMapper, gamificationService, quizQuestionRepository,
                new QuestionDataValidator(objectMapper),
                new QuizCsvFileReader(),
                new QuizCsvRowParser(List.of(
                        new MultipleChoiceCsvParser(objectMapper),
                        new FillInBlankCsvParser(objectMapper),
                        new MatchingCsvParser(objectMapper))));

        context = HistoricalContext.builder().contextId(contextId).name("Bạch Đằng").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().uid(userId).userName("staff").build()));
        when(historicalContextRepository.findById(contextId)).thenReturn(Optional.of(context));
        when(quizRepository.existsByTitleIgnoreCase(any())).thenReturn(false);
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> {
            Quiz quiz = inv.getArgument(0);
            quiz.setQuizId(UUID.randomUUID());
            when(quizRepository.findById(quiz.getQuizId())).thenReturn(Optional.of(quiz));
            return quiz;
        });
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.findActiveByQuizId(any())).thenReturn(List.of());
    }

    @Test
    void importsAllThreeTypesInOneQuiz() throws Exception {
        QuizImportResponse response = importCsv(HEADER
                + row("Bạch Đằng 938", "EASY", "MULTIPLE_CHOICE", "Ai chỉ huy?", "Ngô Quyền,Lê Lợi,Trần Hưng Đạo,Lý Thường Kiệt", "0", "")
                + row("Bạch Đằng 938", "EASY", "FILL_IN_BLANK", "Năm nào?", ",,,", "938|năm 938", "")
                + row("Bạch Đằng 938", "EASY", "MATCHING", "Nối", "Ngô Quyền => Bạch Đằng,Lê Lợi => Chi Lăng,,", "", ""));

        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(3)).save(captor.capture());
        List<Question> saved = captor.getAllValues();
        assertThat(saved).extracting(Question::getQuestionType).containsExactly(
                QuestionType.MULTIPLE_CHOICE, QuestionType.FILL_IN_BLANK, QuestionType.MATCHING);
        assertThat(objectMapper.readValue(saved.get(0).getQuestionData(), MultipleChoiceQuestionData.class)
                .getCorrectOptionIds()).containsExactly("0");
        assertThat(objectMapper.readValue(saved.get(1).getQuestionData(), FillInBlankQuestionData.class)
                .getAcceptedAnswers()).containsExactly("938", "năm 938");
        assertThat(objectMapper.readValue(saved.get(2).getQuestionData(), MatchingQuestionData.class)
                .getCorrectPairs()).hasSize(2);

        ArgumentCaptor<QuizQuestion> links = ArgumentCaptor.forClass(QuizQuestion.class);
        verify(quizQuestionRepository, times(3)).save(links.capture());
        assertThat(links.getAllValues()).extracting(QuizQuestion::getOrderIndex).containsExactly(0, 1, 2);
        assertThat(links.getAllValues()).extracting(QuizQuestion::getQuestion).containsExactlyElementsOf(saved);
    }

    @Test
    void legacyCsvStillImports() throws Exception {
        QuizImportResponse response = importCsv(
                "title,contextId,level,questionContent,option1,option2,option3,option4,correctAnswer,explanation\n"
                        + "Legacy," + contextId + ",MEDIUM,Ai?,A,B,C,D,0,\n");

        assertThat(response.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(captor.capture());
        assertThat(captor.getValue().getExplanation()).isNull();
        assertThat(objectMapper.readValue(captor.getValue().getQuestionData(), MultipleChoiceQuestionData.class)
                .getCorrectOptionIds()).containsExactly("0");
        assertThat(captor.getValue().getDifficulty()).isEqualTo(QuestionDifficulty.MEDIUM);
        ArgumentCaptor<Quiz> quizCaptor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).save(quizCaptor.capture());
        assertThat(quizCaptor.getValue().getIsPublished()).isFalse();
    }

    @Test
    void mixedLevelsInOneQuizImportWithPerQuestionDifficulty() {
        QuizImportResponse response = importCsv(HEADER
                + row("Q", "EASY", "", "Câu 1", "A,B,C,D", "0", "")
                + row("Q", "HARD", "", "Câu 2", "A,B,C,D", "1", ""));

        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Question::getDifficulty)
                .containsExactly(QuestionDifficulty.EASY, QuestionDifficulty.HARD);
    }

    @Test
    void invalidLevelInLaterRowIsRejected() {
        QuizImportResponse response = importCsv(HEADER
                + row("Q", "EASY", "", "Câu 1", "A,B,C,D", "0", "")
                + row("Q", "abc", "", "Câu 2", "A,B,C,D", "1", ""));

        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getErrors().get(0)).contains("Dòng 3", "EASY, MEDIUM, HARD");
        verify(quizRepository, never()).save(any());
    }

    @Test
    void invalidRowSkipsWholeQuizWithoutSaving() {
        QuizImportResponse response = importCsv(HEADER
                + row("Q", "EASY", "MULTIPLE_CHOICE", "Câu 1", "A,B,C,D", "0", "")
                + row("Q", "EASY", "FILL_IN_BLANK", "Câu 2", ",,,", "", ""));

        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getErrors().get(0)).contains("Dòng 3");
        verify(quizRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void inconsistentContextIdSkipsQuiz() {
        QuizImportResponse response = importCsv(HEADER
                + row("Q", "EASY", "", "Câu 1", "A,B,C,D", "0", "")
                + "Q," + UUID.randomUUID() + ",EASY,,Câu 2,A,B,C,D,0,\n");

        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getErrors().get(0)).contains("Dòng 3", "contextId");
        verify(quizRepository, never()).save(any());
    }

    @Test
    void softDeletedContextIsRejected() {
        context.setDeletedAt(LocalDateTime.now());

        QuizImportResponse response = importCsv(HEADER + row("Q", "EASY", "", "Câu 1", "A,B,C,D", "0", ""));

        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getErrors().get(0)).contains("HistoricalContext not found");
        verify(quizRepository, never()).save(any());
    }

    @Test
    void titleLongerThanColumnLimitIsRejectedBeforeSaving() {
        String longTitle = "Q".repeat(256);

        QuizImportResponse response = importCsv(HEADER + row(longTitle, "EASY", "", "Câu 1", "A,B,C,D", "0", ""));

        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getErrors().get(0)).contains("255");
        verify(quizRepository, never()).save(any());
    }

    @Test
    void oneBadQuizDoesNotBlockOthers() {
        QuizImportResponse response = importCsv(HEADER
                + row("A", "EASY", "", "Câu 1", "A,B,C,D", "9", "")
                + row("B", "EASY", "", "Câu 1", "A,B,C,D", "0", ""));

        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getErrors().get(0)).startsWith("Quiz 'A' skipped");
    }

    private String row(String title, String level, String type, String content, String fourOptions,
                       String correct, String explanation) {
        return String.join(",", title, contextId.toString(), level, type, content, fourOptions, correct, explanation) + "\n";
    }

    private QuizImportResponse importCsv(String csv) {
        MockMultipartFile file = new MockMultipartFile("file", "quiz.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return quizService.importQuizzesFromCsv(file, userId);
    }
}
