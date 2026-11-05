package com.historytalk.service.chat;

import com.historytalk.dto.chat.ExamProgressResponse;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;

import java.util.List;

public interface ExamSessionService {

    /** Saves the opening message of a new exam session, which asks the first question. */
    Message startExam(ChatSession session);

    /** Grades the learner's answer to the current question, then asks the next one or closes the exam. */
    ExamTurnResult submitAnswer(ChatSession session, SendMessageRequest request);

    ExamProgressResponse getProgress(ChatSession session, List<Message> messages);

    record ExamTurnResult(
            Message userMessage,
            Message assistantMessage,
            ExamProgressResponse progress,
            int remainingTokens,
            int promptTokens,
            int completionTokens) {
    }
}
