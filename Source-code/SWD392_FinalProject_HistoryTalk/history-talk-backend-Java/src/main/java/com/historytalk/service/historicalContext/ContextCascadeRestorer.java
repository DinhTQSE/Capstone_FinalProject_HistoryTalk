package com.historytalk.service.historicalContext;

import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.service.map.MapPinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Undoes the children part of {@code HistoricalContextServiceImpl.softDeleteContext} when a context is
 * restored (BUS-SPEC §2.9 System Trash). The soft delete stamps every child with the context's own
 * deletedAt, so only rows carrying exactly that value are restored; children deleted on their own
 * earlier keep their older timestamp and stay deleted. Only deletedAt is touched: no token, quiz
 * session or XP data changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContextCascadeRestorer {

    private final QuizRepository quizRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final MapPinService mapPinService;

    @Transactional
    public void restoreChildren(UUID contextId, LocalDateTime contextDeletedAt) {
        if (contextDeletedAt == null) {
            return;
        }

        List<Quiz> quizzes = quizRepository.findByHistoricalContext_ContextIdAndDeletedAt(contextId, contextDeletedAt);
        quizzes.forEach(quiz -> quiz.setDeletedAt(null));
        quizRepository.saveAll(quizzes);

        List<ChatSession> sessions = chatSessionRepository.findByHistoricalContext_ContextIdAndDeletedAt(
                contextId, contextDeletedAt);
        sessions.forEach(session -> {
            session.setDeletedAt(null);
            if (session.getMessages() != null) {
                session.getMessages().stream()
                        .filter(msg -> contextDeletedAt.equals(msg.getDeletedAt()))
                        .forEach(msg -> msg.setDeletedAt(null));
            }
        });
        chatSessionRepository.saveAll(sessions);

        mapPinService.restorePinsForContext(contextId.toString(), contextDeletedAt);

        log.info("Cascade restored {} quizzes and {} chat sessions for contextId={}",
                quizzes.size(), sessions.size(), contextId);
    }
}
