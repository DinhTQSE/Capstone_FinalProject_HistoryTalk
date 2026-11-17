package com.historytalk.service.chat;

import com.historytalk.dto.chat.ExamProgressResponse;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;

import java.util.List;
import java.util.UUID;

public interface ExamSessionService {

    /** Saves the opening message of a new exam session, which asks the first question. */
    Message startExam(UUID sessionId);

    /** Grades the learner's answer to the current question, then asks the next one or closes the exam. */
    ExamTurnResult submitAnswer(UUID sessionId, SendMessageRequest request);

    /**
     * Same as {@link #submitAnswer} but streams the reply. The next question is written from the learner's
     * answer, so it cannot be prepared in advance. A finished exam is rejected before anything is streamed.
     */
    void streamAnswer(UUID sessionId, SendMessageRequest request, ExamStreamListener listener);

    ExamProgressResponse getProgress(ChatSession session, List<Message> messages);

    /** Called from the AI stream's thread. Exactly one of onComplete and onError is called. */
    interface ExamStreamListener {
        void onText(String text);

        void onComplete(ExamTurnResult result);

        void onError(RuntimeException error);
    }

    record ExamTurnResult(
            Message userMessage,
            Message assistantMessage,
            ExamProgressResponse progress,
            int remainingTokens,
            int promptTokens,
            int completionTokens) {
    }
}
