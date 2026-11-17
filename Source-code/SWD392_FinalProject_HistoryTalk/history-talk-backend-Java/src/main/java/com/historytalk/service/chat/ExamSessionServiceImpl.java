package com.historytalk.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.historytalk.service.character.CharacterPronouns;
import com.historytalk.service.chat.AiServiceClient.CharacterPayload;
import com.historytalk.service.chat.AiServiceClient.ContextPayload;
import com.historytalk.service.chat.AiServiceClient.ExamQuestionResult;
import com.historytalk.service.gamification.GamificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final long TURN_TIMEOUT_SECONDS = 180;
    private static final int MAX_CONVERSATION_SOURCES = 30;

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
            int totalQuestions,
            String selfPronoun,
            String addressPronoun,
            List<String> sourcePool) {
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

        ExamQuestionResult first = aiServiceClient.generateExamQuestion(
                context.characterId(),
                context.contextId(),
                context.characterData(),
                context.contextData(),
                List.of(),
                List.of(),
                context.sourcePool());

        String topic = context.sourcePool().isEmpty() ? "" : " về những điều chúng ta vừa trò chuyện";
        String opening = CharacterPronouns.capitalize(context.selfPronoun()) + " là " + context.characterName()
                + ". Hôm nay " + context.selfPronoun() + " sẽ hỏi " + context.addressPronoun() + " "
                + context.totalQuestions() + " câu" + topic + ". Hãy trả lời theo hiểu biết của "
                + context.addressPronoun() + ".";

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
        CompletableFuture<ExamTurnResult> result = new CompletableFuture<>();
        streamAnswer(sessionId, request, new ExamStreamListener() {
            @Override
            public void onText(String text) {
            }

            @Override
            public void onComplete(ExamTurnResult turn) {
                result.complete(turn);
            }

            @Override
            public void onError(RuntimeException error) {
                result.completeExceptionally(error);
            }
        });

        try {
            return result.get(TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new SystemException(e.getMessage());
        } catch (TimeoutException e) {
            throw new SystemException("Dịch vụ AI không trả lời. Vui lòng thử lại.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SystemException("Lượt vấn đáp bị gián đoạn.");
        }
    }

    @Override
    public void streamAnswer(UUID sessionId, SendMessageRequest request, ExamStreamListener listener) {
        PreparedAnswer prepared = transactionTemplate.execute(status -> prepareAnswer(sessionId, request));
        ExamContext context = prepared.context();
        Message current = prepared.currentQuestion();

        int answeredBefore = computeProgress(context.totalQuestions(), prepared.existingMessages()).getAnsweredQuestions();
        boolean askNext = answeredBefore + 1 < context.totalQuestions();

        List<Message> questionMessages = prepared.existingMessages().stream()
                .filter(m -> m.getExamQuestion() != null)
                .toList();

        AiServiceClient.ExamTurnRequest turnRequest = new AiServiceClient.ExamTurnRequest(
                context.characterId(),
                context.contextId(),
                context.characterData(),
                context.contextData(),
                current.getExamQuestion(),
                current.getExamExpectedAnswer(),
                current.getExamSource(),
                request.getContent(),
                questionMessages.stream().map(Message::getExamQuestion).toList(),
                questionMessages.stream().map(Message::getExamSource).filter(Objects::nonNull).toList(),
                askNext,
                context.sourcePool());

        AtomicReference<JsonNode> metadata = new AtomicReference<>();
        AtomicReference<String> aiError = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean(false);

        aiServiceClient.streamExamTurn(
                turnRequest,
                line -> {
                    if (!line.startsWith("data: ")) {
                        return;
                    }
                    try {
                        JsonNode node = objectMapper.readTree(line.substring(6));
                        switch (node.path("type").asText()) {
                            case "text" -> listener.onText(node.path("data").asText());
                            case "metadata" -> metadata.set(node.path("data"));
                            case "error" -> aiError.set(node.path("message").asText());
                            default -> {
                            }
                        }
                    } catch (JsonProcessingException e) {
                        log.warn("Skipping malformed exam stream line: {}", e.getMessage());
                    }
                },
                () -> {
                    if (!finished.compareAndSet(false, true)) {
                        return;
                    }
                    ExamTurnResult turn;
                    try {
                        if (aiError.get() != null || metadata.get() == null) {
                            throw new SystemException(aiError.get() != null
                                    ? aiError.get()
                                    : "Dịch vụ AI không trả lời. Vui lòng thử lại.");
                        }
                        turn = finishAnswer(sessionId, prepared, metadata.get(), askNext);
                    } catch (RuntimeException e) {
                        // The learner can answer again, so a failed turn leaves nothing behind.
                        discard(prepared.userMessage());
                        listener.onError(e);
                        return;
                    }
                    // Only the closing text is not streamed by the AI service, since Java computes the score.
                    if (!askNext) {
                        listener.onText(BUBBLE_SEPARATOR + closingText(turn.progress(), context.addressPronoun()));
                    }
                    listener.onComplete(turn);
                },
                error -> {
                    if (!finished.compareAndSet(false, true)) {
                        return;
                    }
                    log.error("AI exam stream error", error);
                    discard(prepared.userMessage());
                    listener.onError(new SystemException("Dịch vụ AI không khả dụng. Vui lòng thử lại."));
                });
    }

    private ExamTurnResult finishAnswer(UUID sessionId, PreparedAnswer prepared, JsonNode metadata, boolean askNext) {
        ExamVerdict verdict = parseVerdict(metadata.path("verdict").asText(null));
        String feedback = requiredText(metadata, "feedback");
        String nextQuestion = askNext ? requiredText(metadata, "question") : null;

        List<Message> graded = new ArrayList<>(prepared.existingMessages());
        graded.add(Message.builder().examVerdict(verdict).build());
        ExamProgressResponse progress = computeProgress(prepared.context().totalQuestions(), graded);

        // Prompt tokens already come divided by 10 from the AI service.
        int promptTokens = metadata.path("promptTokens").asInt(0);
        int completionTokens = metadata.path("completionTokens").asInt(0);

        // The excerpt held the answer, so it only becomes a visible citation once the question is graded.
        Message assistantMessage = Message.builder()
                .content(feedback + BUBBLE_SEPARATOR
                        + (nextQuestion != null ? nextQuestion : closingText(progress, prepared.context().addressPronoun())))
                .isFromAi(true)
                .messageType(prepared.messageType())
                .examVerdict(verdict)
                .quotes(serializeQuotes(prepared.currentQuestion().getExamSource()))
                .examQuestion(nextQuestion)
                .examExpectedAnswer(nextQuestion != null ? requiredText(metadata, "expectedAnswer") : null)
                .examSource(nextQuestion != null ? requiredText(metadata, "sourceExcerpt") : null)
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
                session.getExamQuestionCount() != null ? session.getExamQuestionCount() : 0,
                CharacterPronouns.selfOf(session.getCharacter()),
                CharacterPronouns.addressOf(session.getCharacter()),
                conversationSources(session.getSourceSessionId()));
    }

    /**
     * The document excerpts the character answered with in the chat an exam was started from, so the questions
     * are about what the learner just heard while still coming from the documents.
     */
    private List<String> conversationSources(UUID sourceSessionId) {
        if (sourceSessionId == null) {
            return List.of();
        }
        Set<String> sources = new LinkedHashSet<>();
        for (Message message : messageRepository.findByChatSessionSessionIdOrderByTimestampAsc(sourceSessionId, false)) {
            if (!Boolean.TRUE.equals(message.getIsFromAi()) || message.getQuotes() == null) {
                continue;
            }
            try {
                for (String quote : objectMapper.readValue(message.getQuotes(), new TypeReference<List<String>>() {})) {
                    if (quote != null && !quote.isBlank()) {
                        sources.add(quote.trim());
                    }
                }
            } catch (JsonProcessingException e) {
                log.warn("Skipping unreadable quotes of message {}: {}", message.getMessageId(), e.getMessage());
            }
        }
        return sources.stream().limit(MAX_CONVERSATION_SOURCES).toList();
    }

    private ChatSession loadSession(UUID sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));
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

    private String requiredText(JsonNode metadata, String field) {
        String value = metadata.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new SystemException("Dịch vụ AI trả về lượt vấn đáp thiếu " + field);
        }
        return value;
    }

    private String serializeQuotes(String source) {
        try {
            return objectMapper.writeValueAsString(List.of(source));
        } catch (JsonProcessingException | NullPointerException e) {
            log.warn("Failed to serialize exam source: {}", e.getMessage());
            return null;
        }
    }

    private String closingText(ExamProgressResponse progress, String addressPronoun) {
        return "Buổi vấn đáp đã kết thúc. " + CharacterPronouns.capitalize(addressPronoun)
                + " trả lời đúng " + progress.getCorrectCount()
                + " câu, đúng một phần " + progress.getPartialCount()
                + " câu, sai " + progress.getIncorrectCount()
                + " câu trên tổng " + progress.getTotalQuestions()
                + " câu. Điểm của " + addressPronoun + ": " + progress.getScore() + "/10.";
    }
}
