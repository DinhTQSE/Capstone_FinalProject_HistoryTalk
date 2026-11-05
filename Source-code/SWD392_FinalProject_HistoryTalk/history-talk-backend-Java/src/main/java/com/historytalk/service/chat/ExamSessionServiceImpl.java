package com.historytalk.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.ExamProgressResponse;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;
import com.historytalk.entity.enums.ExamVerdict;
import com.historytalk.entity.enums.QuestType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.service.chat.AiServiceClient.CharacterPayload;
import com.historytalk.service.chat.AiServiceClient.ContextPayload;
import com.historytalk.service.chat.AiServiceClient.ExamGradeResult;
import com.historytalk.service.chat.AiServiceClient.ExamQuestionResult;
import com.historytalk.service.gamification.GamificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * LLM calls take seconds and the connection pool is small, so no transaction stays open across them:
 * each turn reads in one short transaction, calls the LLM, then saves in another.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamSessionServiceImpl implements ExamSessionService {

    /** Both web and mobile render the parts around this separator as separate chat bubbles. */
    private static final String BUBBLE_SEPARATOR = "\n---\n";
    private static final String NO_CONTEXT_ID = "00000000-0000-0000-0000-000000000000";

    private final MessageRepository messageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatTokenCharger chatTokenCharger;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final GamificationService gamificationService;
    private final TransactionTemplate transactionTemplate;

    private record ExamContext(
            String characterId,
            String characterName,
            String contextId,
            CharacterPayload characterData,
            ContextPayload contextData,
            int totalQuestions) {
    }

    private record PreparedAnswer(
            ExamContext context,
            List<Message> existingMessages,
            Message currentQuestion,
            Message userMessage,
            String messageType) {
    }

    @Override
    public Message startExam(UUID sessionId) {
        ExamContext context = transactionTemplate.execute(status -> buildContext(loadSession(sessionId)));

        ExamQuestionResult first = askQuestion(context, List.of());

        String opening = "Ta là " + context.characterName() + ". Hôm nay ta sẽ hỏi ngươi "
                + context.totalQuestions() + " câu. Hãy trả lời theo hiểu biết của ngươi.";

        return transactionTemplate.execute(status -> {
            ChatSession session = loadSession(sessionId);
            Message saved = messageRepository.save(Message.builder()
                    .content(opening + BUBBLE_SEPARATOR + first.question())
                    .isFromAi(true)
                    .chatSession(session)
                    .examQuestion(first.question())
                    .examExpectedAnswer(first.expectedAnswer())
                    .examSource(first.sourceExcerpt())
                    .build());
            session.setLastMessageAt(LocalDateTime.now());
            return saved;
        });
    }

    @Override
    public ExamTurnResult submitAnswer(UUID sessionId, SendMessageRequest request) {
        PreparedAnswer prepared = transactionTemplate.execute(status -> prepareAnswer(sessionId, request));

        ExamGradeResult grade;
        ExamQuestionResult next = null;
        ExamProgressResponse progress;
        ExamVerdict verdict;
        try {
            Message current = prepared.currentQuestion();
            grade = aiServiceClient.gradeExamAnswer(
                    prepared.context().characterId(),
                    prepared.context().contextId(),
                    prepared.context().characterData(),
                    prepared.context().contextData(),
                    current.getExamQuestion(),
                    current.getExamExpectedAnswer(),
                    current.getExamSource(),
                    request.getContent());
            verdict = parseVerdict(grade.verdict());

            List<Message> graded = new ArrayList<>(prepared.existingMessages());
            graded.add(Message.builder().examVerdict(verdict).build());
            progress = computeProgress(prepared.context().totalQuestions(), graded);

            if (!progress.isFinished()) {
                next = askQuestion(prepared.context(), prepared.existingMessages());
            }
        } catch (RuntimeException e) {
            // The learner can answer again, so a failed turn leaves nothing behind.
            discard(prepared.userMessage());
            throw e;
        }

        int promptTokens = promptTokensOf(grade.tokenUsage()) + (next != null ? promptTokensOf(next.tokenUsage()) : 0);
        int completionTokens = completionTokensOf(grade.tokenUsage())
                + (next != null ? completionTokensOf(next.tokenUsage()) : 0);

        // The excerpt held the answer, so it only becomes a visible citation once the question is graded.
        Message assistantMessage = Message.builder()
                .content(grade.feedback() + BUBBLE_SEPARATOR + (next != null ? next.question() : closingText(progress)))
                .isFromAi(true)
                .messageType(prepared.messageType())
                .examVerdict(verdict)
                .quotes(serializeQuotes(prepared.currentQuestion().getExamSource()))
                .examQuestion(next != null ? next.question() : null)
                .examExpectedAnswer(next != null ? next.expectedAnswer() : null)
                .examSource(next != null ? next.sourceExcerpt() : null)
                .token(completionTokens)
                .build();

        int totalTokens = promptTokens + completionTokens;
        int finalPromptTokens = promptTokens;
        String[] userId = new String[1];
        int remainingTokens = transactionTemplate.execute(status -> {
            ChatSession session = loadSession(sessionId);
            userId[0] = session.getUser().getUid().toString();

            prepared.userMessage().setToken(finalPromptTokens);
            messageRepository.save(prepared.userMessage());

            assistantMessage.setChatSession(session);
            messageRepository.save(assistantMessage);

            session.setLastMessageAt(LocalDateTime.now());
            return chatTokenCharger.charge(session.getUser(), totalTokens);
        });

        recordQuest(userId[0]);

        return new ExamTurnResult(prepared.userMessage(), assistantMessage, progress, remainingTokens,
                promptTokens, completionTokens);
    }

    @Override
    public ExamProgressResponse getProgress(ChatSession session, List<Message> messages) {
        int total = session.getExamQuestionCount() != null ? session.getExamQuestionCount() : 0;
        return computeProgress(total, messages);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private PreparedAnswer prepareAnswer(UUID sessionId, SendMessageRequest request) {
        ChatSession session = loadSession(sessionId);

        List<Message> existingMessages = messageRepository.findByChatSessionSessionIdOrderByTimestampAsc(sessionId, false);

        Message current = existingMessages.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsFromAi()))
                .reduce((first, second) -> second)
                .filter(m -> m.getExamQuestion() != null)
                .orElseThrow(() -> new InvalidRequestException("Buổi vấn đáp này đã kết thúc."));

        String messageType = request.getMessageType() != null ? request.getMessageType() : "TEXT";

        Message userMessage = messageRepository.save(Message.builder()
                .content(request.getContent())
                .isFromAi(false)
                .chatSession(session)
                .messageType(messageType)
                .build());

        return new PreparedAnswer(buildContext(session), existingMessages, current, userMessage, messageType);
    }

    private ExamContext buildContext(ChatSession session) {
        return new ExamContext(
                session.getCharacter().getCharacterId().toString(),
                session.getCharacter().getName(),
                session.getHistoricalContext() != null
                        ? session.getHistoricalContext().getContextId().toString()
                        : NO_CONTEXT_ID,
                AiServiceClient.buildCharacterPayload(session.getCharacter()),
                session.getHistoricalContext() != null
                        ? AiServiceClient.buildContextPayload(session.getHistoricalContext())
                        : null,
                session.getExamQuestionCount() != null ? session.getExamQuestionCount() : 0);
    }

    private ChatSession loadSession(UUID sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));
    }

    private ExamQuestionResult askQuestion(ExamContext context, List<Message> existingMessages) {
        List<Message> questionMessages = existingMessages.stream()
                .filter(m -> m.getExamQuestion() != null)
                .toList();

        return aiServiceClient.generateExamQuestion(
                context.characterId(),
                context.contextId(),
                context.characterData(),
                context.contextData(),
                questionMessages.stream().map(Message::getExamQuestion).toList(),
                questionMessages.stream().map(Message::getExamSource).filter(Objects::nonNull).toList());
    }

    private ExamProgressResponse computeProgress(int total, List<Message> messages) {
        int correct = 0;
        int partial = 0;
        int incorrect = 0;
        for (Message message : messages) {
            if (message.getExamVerdict() == null) {
                continue;
            }
            switch (message.getExamVerdict()) {
                case CORRECT -> correct++;
                case PARTIAL -> partial++;
                case INCORRECT -> incorrect++;
            }
        }

        int answered = correct + partial + incorrect;
        double score = total > 0 ? Math.round((correct + partial * 0.5) * 100.0 / total) / 10.0 : 0.0;

        return ExamProgressResponse.builder()
                .totalQuestions(total)
                .answeredQuestions(answered)
                .correctCount(correct)
                .partialCount(partial)
                .incorrectCount(incorrect)
                .score(score)
                .finished(answered >= total)
                .build();
    }

    private void discard(Message message) {
        try {
            transactionTemplate.executeWithoutResult(status -> messageRepository.delete(message));
        } catch (RuntimeException e) {
            log.warn("Failed to remove exam answer {}: {}", message.getMessageId(), e.getMessage());
        }
    }

    private void recordQuest(String userId) {
        try {
            gamificationService.recordProgress(userId, QuestType.CHAT);
        } catch (RuntimeException e) {
            log.warn("Failed to record chat quest for user {}: {}", userId, e.getMessage());
        }
    }

    private ExamVerdict parseVerdict(String verdict) {
        try {
            return ExamVerdict.valueOf(verdict);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SystemException("Dịch vụ AI trả về kết quả chấm không hợp lệ: " + verdict);
        }
    }

    private int promptTokensOf(AiMetricsService.TokenUsage usage) {
        return usage != null && usage.promptTokens() != null ? usage.promptTokens() : 0;
    }

    private int completionTokensOf(AiMetricsService.TokenUsage usage) {
        return usage != null && usage.completionTokens() != null ? usage.completionTokens() : 0;
    }

    private String serializeQuotes(String source) {
        try {
            return objectMapper.writeValueAsString(List.of(source));
        } catch (JsonProcessingException | NullPointerException e) {
            log.warn("Failed to serialize exam source: {}", e.getMessage());
            return null;
        }
    }

    private String closingText(ExamProgressResponse progress) {
        return "Buổi vấn đáp đã kết thúc. Ngươi trả lời đúng " + progress.getCorrectCount()
                + " câu, đúng một phần " + progress.getPartialCount()
                + " câu, sai " + progress.getIncorrectCount()
                + " câu trên tổng " + progress.getTotalQuestions()
                + " câu. Điểm của ngươi: " + progress.getScore() + "/10.";
    }
}
