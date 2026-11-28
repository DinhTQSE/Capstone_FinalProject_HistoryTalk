package com.historytalk.service.historicalContext;

import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.service.map.MapPinService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextCascadeRestorerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 28, 10, 0, 0, 123_456_000);

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private MapPinService mapPinService;

    @InjectMocks
    private ContextCascadeRestorer restorer;

    @Test
    void restoreChildren_clearsQuizzesSessionsAndPins() {
        UUID ctxId = UUID.randomUUID();
        Quiz quiz = Quiz.builder().quizId(UUID.randomUUID()).deletedAt(T).build();
        ChatSession session = ChatSession.builder().sessionId(UUID.randomUUID()).deletedAt(T).build();
        when(quizRepository.findByHistoricalContext_ContextIdAndDeletedAt(ctxId, T)).thenReturn(List.of(quiz));
        when(chatSessionRepository.findByHistoricalContext_ContextIdAndDeletedAt(ctxId, T)).thenReturn(List.of(session));

        restorer.restoreChildren(ctxId, T);

        assertThat(quiz.getDeletedAt()).isNull();
        assertThat(session.getDeletedAt()).isNull();
        verify(quizRepository).saveAll(List.of(quiz));
        verify(chatSessionRepository).saveAll(List.of(session));
        verify(mapPinService).restorePinsForContext(ctxId.toString(), T);
    }

    @Test
    void restoreChildren_onlyClearsMessagesWithContextTimestamp() {
        UUID ctxId = UUID.randomUUID();
        LocalDateTime earlier = T.minusDays(1);
        Message cascaded = Message.builder().messageId(UUID.randomUUID()).deletedAt(T).build();
        Message deletedBefore = Message.builder().messageId(UUID.randomUUID()).deletedAt(earlier).build();
        ChatSession session = ChatSession.builder()
                .sessionId(UUID.randomUUID())
                .deletedAt(T)
                .messages(new ArrayList<>(List.of(cascaded, deletedBefore)))
                .build();
        when(quizRepository.findByHistoricalContext_ContextIdAndDeletedAt(ctxId, T)).thenReturn(List.of());
        when(chatSessionRepository.findByHistoricalContext_ContextIdAndDeletedAt(ctxId, T)).thenReturn(List.of(session));

        restorer.restoreChildren(ctxId, T);

        assertThat(cascaded.getDeletedAt()).isNull();
        assertThat(deletedBefore.getDeletedAt()).isEqualTo(earlier);
    }

    @Test
    void restoreChildren_nullTimestamp_doesNothing() {
        restorer.restoreChildren(UUID.randomUUID(), null);

        verifyNoInteractions(quizRepository, chatSessionRepository, mapPinService);
    }
}
