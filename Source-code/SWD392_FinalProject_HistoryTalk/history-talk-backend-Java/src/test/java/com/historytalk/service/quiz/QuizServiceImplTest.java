package com.historytalk.service.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.quiz.CreateQuizRequest;
import com.historytalk.dto.quiz.QuestionRequest;
import com.historytalk.dto.quiz.QuestionResponse;
import com.historytalk.dto.quiz.QuizStaffResponse;
import com.historytalk.dto.quiz.QuizStartResponse;
import com.historytalk.entity.enums.QuestionDifficulty;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.quiz.Question;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.quiz.QuizQuestion;
import com.historytalk.entity.quiz.QuizQuestionId;
import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuestionRepository;
import com.historytalk.repository.QuizQuestionRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceImplTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuizSessionRepository quizSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HistoricalContextRepository historicalContextRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private QuestionDataValidator questionDataValidator;

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @InjectMocks
    private QuizServiceImpl quizService;

    @Test
    void getAllQuizzesForStaffExcludesDeletedRecords() {
        Pageable pageable = PageRequest.of(0, 10);
        Quiz quiz = activeQuiz();

        when(quizRepository.findAllForStaff(eq("history"), any(), eq(false), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(quiz), pageable, 1));
        when(questionRepository.findActiveByQuizIds(List.of(quiz.getQuizId()))).thenReturn(List.of());

        PaginatedResponse<QuizStaffResponse> response =
                quizService.getAllQuizzesForStaff("history", null, pageable, "CONTENT_ADMIN");

        assertThat(response.getContent()).hasSize(1);
        ArgumentCaptor<Boolean> includeDeletedCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(quizRepository).findAllForStaff(eq("history"), any(), includeDeletedCaptor.capture(), eq(pageable));
        assertThat(includeDeletedCaptor.getValue()).isFalse();
    }

    @Test
    void quizStaffResponseJsonDoesNotExposeDeletedAt() throws Exception {
        QuizStaffResponse response = QuizStaffResponse.builder()
                .quizId(UUID.randomUUID().toString())
                .title("Tran Dynasty Quiz")
                .build();

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain("deletedAt");
    }

    @Test
    void updateQuestionTypeOnlyRevalidatesExistingData() throws Exception {
        Quiz quiz = activeQuiz();
        Question question = Question.builder()
                .questionId(UUID.randomUUID())
                .content("Ai chỉ huy?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionData("{\"options\":[],\"correctOptionIds\":[]}")
                .build();
        QuestionRequest request = QuestionRequest.builder()
                .questionType(QuestionType.MATCHING)
                .build();

        when(quizRepository.findById(quiz.getQuizId())).thenReturn(Optional.of(quiz));
        when(questionRepository.findById(question.getQuestionId())).thenReturn(Optional.of(question));
        when(quizQuestionRepository.existsByQuiz_QuizIdAndQuestion_QuestionId(quiz.getQuizId(), question.getQuestionId()))
                .thenReturn(true);
        when(objectMapper.readTree(question.getQuestionData())).thenReturn(new ObjectMapper().createObjectNode());
        doThrow(new InvalidRequestException("invalid")).when(questionDataValidator)
                .validate(eq(QuestionType.MATCHING), any());

        assertThatThrownBy(() -> quizService.updateQuestion(
                quiz.getQuizId().toString(), question.getQuestionId().toString(), request))
                .isInstanceOf(InvalidRequestException.class);

        verify(questionDataValidator).validate(eq(QuestionType.MATCHING), any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void updateBankQuestionTypeOnlyRevalidatesExistingData() throws Exception {
        Question question = Question.builder()
                .questionId(UUID.randomUUID())
                .content("Ai chỉ huy?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionData("{\"options\":[],\"correctOptionIds\":[]}")
                .build();
        QuestionRequest request = QuestionRequest.builder()
                .questionType(QuestionType.FILL_IN_BLANK)
                .build();

        when(questionRepository.findById(question.getQuestionId())).thenReturn(Optional.of(question));
        when(objectMapper.readTree(question.getQuestionData())).thenReturn(new ObjectMapper().createObjectNode());
        doThrow(new InvalidRequestException("invalid")).when(questionDataValidator)
                .validate(eq(QuestionType.FILL_IN_BLANK), any());

        assertThatThrownBy(() -> quizService.updateBankQuestion(question.getQuestionId().toString(), request))
                .isInstanceOf(InvalidRequestException.class);

        verify(questionRepository, never()).save(any());
    }

    @Test
    void createQuiz_linksEachQuestionInOrder() {
        Quiz template = activeQuiz();
        UUID quizId = template.getQuizId();
        CreateQuizRequest request = CreateQuizRequest.builder()
                .title("Bạch Đằng")
                .contextId(template.getHistoricalContext().getContextId().toString())
                .questions(List.of(questionRequest("Câu 1"), questionRequest("Câu 2")))
                .build();

        when(quizRepository.existsByTitleIgnoreCase("Bạch Đằng")).thenReturn(false);
        when(userRepository.findById(template.getCreatedBy().getUid())).thenReturn(Optional.of(template.getCreatedBy()));
        when(historicalContextRepository.findById(template.getHistoricalContext().getContextId()))
                .thenReturn(Optional.of(template.getHistoricalContext()));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> {
            Quiz q = inv.getArgument(0);
            q.setQuizId(quizId);
            return q;
        });
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setQuestionId(UUID.randomUUID());
            return q;
        });

        quizService.createQuiz(request, template.getCreatedBy().getUid());

        ArgumentCaptor<QuizQuestion> links = ArgumentCaptor.forClass(QuizQuestion.class);
        verify(quizQuestionRepository, times(2)).save(links.capture());
        assertThat(links.getAllValues()).extracting(QuizQuestion::getOrderIndex).containsExactly(0, 1);
        assertThat(links.getAllValues()).allSatisfy(l -> assertThat(l.getQuiz().getQuizId()).isEqualTo(quizId));
        assertThat(links.getAllValues()).extracting(l -> l.getQuestion().getContent())
                .containsExactly("Câu 1", "Câu 2");
    }

    @Test
    void addQuestion_linksAfterCurrentMaxOrder() {
        Quiz quiz = activeQuiz();
        when(quizRepository.findById(quiz.getQuizId())).thenReturn(Optional.of(quiz));
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setQuestionId(UUID.randomUUID());
            return q;
        });
        when(quizQuestionRepository.findMaxOrderIndex(quiz.getQuizId())).thenReturn(4);

        quizService.addQuestion(quiz.getQuizId().toString(), questionRequest("Câu mới"));

        ArgumentCaptor<QuizQuestion> link = ArgumentCaptor.forClass(QuizQuestion.class);
        verify(quizQuestionRepository).save(link.capture());
        assertThat(link.getValue().getOrderIndex()).isEqualTo(5);
        assertThat(link.getValue().getQuiz()).isSameAs(quiz);
    }

    @Test
    void updateQuestion_notLinkedToQuiz_rejected() {
        Quiz quiz = activeQuiz();
        Question question = Question.builder().questionId(UUID.randomUUID()).content("Câu khác").build();
        when(quizRepository.findById(quiz.getQuizId())).thenReturn(Optional.of(quiz));
        when(questionRepository.findById(question.getQuestionId())).thenReturn(Optional.of(question));
        when(quizQuestionRepository.existsByQuiz_QuizIdAndQuestion_QuestionId(quiz.getQuizId(), question.getQuestionId()))
                .thenReturn(false);

        assertThatThrownBy(() -> quizService.updateQuestion(
                quiz.getQuizId().toString(), question.getQuestionId().toString(), questionRequest("Sửa")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Câu hỏi không thuộc về quiz");
        verify(questionRepository, never()).save(any());
    }

    @Test
    void deleteQuestion_unlinksWithoutDeletingQuestion() {
        Quiz quiz = activeQuiz();
        Question question = Question.builder().questionId(UUID.randomUUID()).build();
        QuizQuestion link = QuizQuestion.builder().quiz(quiz).question(question).orderIndex(0).build();
        when(quizRepository.findById(quiz.getQuizId())).thenReturn(Optional.of(quiz));
        when(questionRepository.findById(question.getQuestionId())).thenReturn(Optional.of(question));
        when(quizQuestionRepository.findById(new QuizQuestionId(quiz.getQuizId(), question.getQuestionId())))
                .thenReturn(Optional.of(link));

        quizService.deleteQuestion(quiz.getQuizId().toString(), question.getQuestionId().toString());

        verify(quizQuestionRepository).delete(link);
        verify(questionRepository, never()).delete(any());
    }

    @Test
    void deleteQuiz_keepsQuestionsInBank() {
        Quiz quiz = activeQuiz();
        quiz.setQuestions(new java.util.ArrayList<>(List.of(Question.builder().questionId(UUID.randomUUID()).build())));
        when(quizRepository.findById(quiz.getQuizId())).thenReturn(Optional.of(quiz));

        quizService.deleteQuiz(quiz.getQuizId().toString());

        verify(quizRepository).delete(quiz);
        verify(questionRepository, never()).delete(any());
        verify(questionRepository, never()).deleteAll(any());
    }

    @Test
    void updateQuestion_nullDifficultyKeepsExisting() {
        Quiz quiz = activeQuiz();
        Question question = Question.builder().questionId(UUID.randomUUID()).content("Cũ")
                .difficulty(QuestionDifficulty.HARD).build();
        when(quizRepository.findById(quiz.getQuizId())).thenReturn(Optional.of(quiz));
        when(questionRepository.findById(question.getQuestionId())).thenReturn(Optional.of(question));
        when(quizQuestionRepository.existsByQuiz_QuizIdAndQuestion_QuestionId(quiz.getQuizId(), question.getQuestionId()))
                .thenReturn(true);

        quizService.updateQuestion(quiz.getQuizId().toString(), question.getQuestionId().toString(),
                QuestionRequest.builder().content("Mới").questionType(null).build());

        ArgumentCaptor<Question> saved = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("Mới");
        assertThat(saved.getValue().getDifficulty()).isEqualTo(QuestionDifficulty.HARD);
    }

    @Test
    void quizStaffResponseJsonHasNoLevel() throws Exception {
        QuizStaffResponse response = QuizStaffResponse.builder()
                .quizId(UUID.randomUUID().toString())
                .title("Tran Dynasty Quiz")
                .build();

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain("\"level\"");
    }

    @Test
    void startQuiz_questionsCarryDifficulty() {
        Quiz quiz = activeQuiz();
        User learner = User.builder().uid(UUID.randomUUID()).userName("learner").build();
        when(quizRepository.findActiveById(quiz.getQuizId())).thenReturn(Optional.of(quiz));
        when(userRepository.findById(learner.getUid())).thenReturn(Optional.of(learner));
        when(quizSessionRepository.save(any(QuizSession.class))).thenAnswer(inv -> {
            QuizSession s = inv.getArgument(0);
            s.setSessionId(UUID.randomUUID());
            return s;
        });
        when(questionRepository.findActiveByQuizId(quiz.getQuizId())).thenReturn(List.of(
                questionWithDifficulty(QuestionDifficulty.EASY), questionWithDifficulty(QuestionDifficulty.HARD)));

        QuizStartResponse response = quizService.startQuiz(quiz.getQuizId().toString(), learner.getUid(), null);

        assertThat(response.getQuestions()).extracting(QuestionResponse::getDifficulty)
                .containsExactly(QuestionDifficulty.EASY, QuestionDifficulty.HARD);
    }

    @Test
    void staffQuizDetail_questionsCarryDifficulty() {
        Quiz quiz = activeQuiz();
        when(quizRepository.findById(quiz.getQuizId())).thenReturn(Optional.of(quiz));
        when(questionRepository.findActiveByQuizId(quiz.getQuizId())).thenReturn(List.of(
                questionWithDifficulty(QuestionDifficulty.MEDIUM), questionWithDifficulty(QuestionDifficulty.EASY)));

        QuizStaffResponse response = quizService.getQuizByIdForStaff(quiz.getQuizId().toString());

        assertThat(response.getQuestions()).extracting(QuestionResponse::getDifficulty)
                .containsExactly(QuestionDifficulty.MEDIUM, QuestionDifficulty.EASY);
    }

    private Question questionWithDifficulty(QuestionDifficulty difficulty) {
        return Question.builder()
                .questionId(UUID.randomUUID())
                .content("Câu " + difficulty)
                .questionType(QuestionType.FILL_IN_BLANK)
                .difficulty(difficulty)
                .build();
    }

    private QuestionRequest questionRequest(String content) {
        return QuestionRequest.builder()
                .content(content)
                .questionType(QuestionType.FILL_IN_BLANK)
                .difficulty(QuestionDifficulty.EASY)
                .build();
    }

    private Quiz activeQuiz() {
        User creator = User.builder()
                .uid(UUID.randomUUID())
                .userName("staff")
                .build();
        HistoricalContext context = HistoricalContext.builder()
                .contextId(UUID.randomUUID())
                .name("Tran Dynasty")
                .description("Historical context description")
                .createdBy(creator)
                .build();
        return Quiz.builder()
                .quizId(UUID.randomUUID())
                .title("Tran Dynasty Quiz")
                .historicalContext(context)
                .createdBy(creator)
                .isPublished(true)
                .build();
    }
}
