package com.historytalk.service.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.QuizStaffResponse;
import com.historytalk.dto.quiz.quizdata.FillInBlankQuestionData;
import com.historytalk.dto.quiz.quizdata.MatchingQuestionData;
import com.historytalk.dto.quiz.quizdata.MultipleChoiceQuestionData;
import com.historytalk.entity.enums.QuestionDifficulty;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.quiz.Question;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.quiz.QuizQuestion;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuizServiceImplImportTest {

    private static final String TITLE = "Điện Biên Phủ";
    private static final String HEADER = "level,questionType,questionContent,"
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

        context = HistoricalContext.builder().contextId(contextId).name("Điện Biên Phủ 1954").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().uid(userId).userName("staff").build()));
        when(historicalContextRepository.findById(contextId)).thenReturn(Optional.of(context));
        when(quizRepository.existsByTitleIgnoreCase(any())).thenReturn(false);
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> {
            Quiz quiz = inv.getArgument(0);
            quiz.setQuizId(UUID.randomUUID());
            return quiz;
        });
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.findActiveByQuizId(any())).thenReturn(List.of());
    }

    @Test
    void importsAllThreeTypesIntoOneQuiz() throws Exception {
        QuizStaffResponse response = importCsv(HEADER
                + row("EASY", "MULTIPLE_CHOICE", "Ai chỉ huy?", "Võ Nguyên Giáp,Văn Tiến Dũng,Hoàng Văn Thái,Nguyễn Chí Thanh", "0")
                + row("EASY", "FILL_IN_BLANK", "Năm nào?", ",,,", "1954|năm 1954")
                + row("EASY", "MATCHING", "Nối", "13/3 => Him Lam,7/5 => De Castries,,", ""));

        assertThat(response.getTitle()).isEqualTo(TITLE);
        assertThat(response.getContextId()).isEqualTo(contextId.toString());
        assertThat(response.getIsPublished()).isFalse();
        verify(quizRepository, times(1)).save(any(Quiz.class));

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(3)).save(captor.capture());
        List<Question> saved = captor.getAllValues();
        assertThat(saved).extracting(Question::getQuestionType).containsExactly(
                QuestionType.MULTIPLE_CHOICE, QuestionType.FILL_IN_BLANK, QuestionType.MATCHING);
        assertThat(objectMapper.readValue(saved.get(0).getQuestionData(), MultipleChoiceQuestionData.class)
                .getCorrectOptionIds()).containsExactly("0");
        assertThat(objectMapper.readValue(saved.get(1).getQuestionData(), FillInBlankQuestionData.class)
                .getAcceptedAnswers()).containsExactly("1954", "năm 1954");
        assertThat(objectMapper.readValue(saved.get(2).getQuestionData(), MatchingQuestionData.class)
                .getCorrectPairs()).hasSize(2);

        ArgumentCaptor<QuizQuestion> links = ArgumentCaptor.forClass(QuizQuestion.class);
        verify(quizQuestionRepository, times(3)).save(links.capture());
        assertThat(links.getAllValues()).extracting(QuizQuestion::getOrderIndex).containsExactly(0, 1, 2);
    }

    @Test
    void guidanceCommentLinesAreIgnored() {
        importCsv("# Hướng dẫn, đọc kỹ,,,\n#\n" + HEADER
                + row("EASY", "", "Câu 1", "A,B,C,D", "0")
                + "# ghi chú giữa các dòng\n"
                + row("MEDIUM", "", "Câu 2", "A,B,C,D", "1"));

        verify(questionRepository, times(2)).save(any(Question.class));
    }

    @Test
    void legacyTitleAndContextIdColumnsAreIgnored() {
        importCsv("title,contextId,level,questionContent,option1,option2,option3,option4,correctAnswer,explanation\n"
                + "Khác," + UUID.randomUUID() + ",MEDIUM,Ai?,A,B,C,D,0,\n");

        ArgumentCaptor<Quiz> quizCaptor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).save(quizCaptor.capture());
        assertThat(quizCaptor.getValue().getTitle()).isEqualTo(TITLE);
        assertThat(quizCaptor.getValue().getHistoricalContext()).isSameAs(context);
        verify(historicalContextRepository, times(1)).findById(any());
        verify(historicalContextRepository).findById(contextId);
    }

    @Test
    void reportsEveryInvalidRowAndSavesNothing() {
        assertThatThrownBy(() -> importCsv(HEADER
                + row("EASY", "", "Câu 1", "A,B,C,D", "0")
                + row("EASY", "", "Câu 2", "A,B,C,D", "9")
                + row("EASY", "", "Câu 3", "A,B,C,D", "1")
                + row("EASY", "", "Câu 4", "A,B,C,D", "9")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("2 câu hỏi lỗi")
                .hasMessageContaining("Câu hỏi 2:")
                .hasMessageContaining("Câu hỏi 4:");

        verify(quizRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void blankTitleIsRejectedBeforeReadingFile() {
        assertThatThrownBy(() -> importCsv(HEADER + row("EASY", "", "Câu 1", "A,B,C,D", "0"), contextId.toString(), "   "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Tên quiz không được để trống");

        verify(historicalContextRepository, never()).findById(any());
        verify(quizRepository, never()).save(any());
    }

    @Test
    void titleLongerThan255IsRejected() {
        assertThatThrownBy(() -> importCsv(HEADER + row("EASY", "", "Câu 1", "A,B,C,D", "0"),
                contextId.toString(), "Q".repeat(256)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("255");

        verify(quizRepository, never()).save(any());
    }

    @Test
    void invalidContextIdFormatIsRejected() {
        assertThatThrownBy(() -> importCsv(HEADER + row("EASY", "", "Câu 1", "A,B,C,D", "0"), "abc", TITLE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("contextId");

        verify(quizRepository, never()).save(any());
    }

    @Test
    void softDeletedContextIsRejected() {
        context.setDeletedAt(LocalDateTime.now());

        assertThatThrownBy(() -> importCsv(HEADER + row("EASY", "", "Câu 1", "A,B,C,D", "0")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(quizRepository, never()).save(any());
    }

    @Test
    void duplicateTitleIsRejected() {
        when(quizRepository.existsByTitleIgnoreCase(TITLE)).thenReturn(true);

        assertThatThrownBy(() -> importCsv(HEADER + row("EASY", "", "Câu 1", "A,B,C,D", "0")))
                .isInstanceOf(DataConflictException.class);

        verify(quizRepository, never()).save(any());
    }

    @Test
    void mixedLevelsKeepPerQuestionDifficulty() {
        importCsv(HEADER
                + row("EASY", "", "Câu 1", "A,B,C,D", "0")
                + row("HARD", "", "Câu 2", "A,B,C,D", "1"));

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Question::getDifficulty)
                .containsExactly(QuestionDifficulty.EASY, QuestionDifficulty.HARD);
    }

    private static String row(String level, String type, String content, String fourOptions, String correct) {
        return String.join(",", level, type, content, fourOptions, correct, "") + "\n";
    }

    private QuizStaffResponse importCsv(String csv) {
        return importCsv(csv, contextId.toString(), TITLE);
    }

    private QuizStaffResponse importCsv(String csv, String formContextId, String formTitle) {
        MockMultipartFile file = new MockMultipartFile("file", "quiz.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return quizService.importQuizFromCsv(file, formContextId, formTitle, userId);
    }
}
