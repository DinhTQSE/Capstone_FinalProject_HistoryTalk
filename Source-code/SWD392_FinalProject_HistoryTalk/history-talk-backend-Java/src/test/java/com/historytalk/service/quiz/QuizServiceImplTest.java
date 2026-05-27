package com.historytalk.service.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.quiz.QuizStaffResponse;
import com.historytalk.entity.enums.QuizLevel;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.user.User;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuestionRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private QuizServiceImpl quizService;

    @Test
    void getAllQuizzesForStaffExcludesDeletedRecords() {
        Pageable pageable = PageRequest.of(0, 10);
        Quiz quiz = activeQuiz();

        when(quizRepository.findAllForStaff(eq("history"), any(), eq(false), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(quiz), pageable, 1));
        when(questionRepository.findActiveByQuizId(quiz.getQuizId())).thenReturn(List.of());

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
                .level(QuizLevel.EASY)
                .historicalContext(context)
                .createdBy(creator)
                .isPublished(true)
                .build();
    }
}
