package com.historytalk.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.GetMessagesResponse;
import com.historytalk.dto.chat.MessageResponse;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.dto.chat.SendMessageResponse;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;
import com.historytalk.entity.enums.ChatSessionMode;

import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.service.chat.AiServiceClient;
import com.historytalk.service.chat.AiServiceClient.AiChatResult;
import com.historytalk.service.chat.AiServiceClient.CharacterPayload;
import com.historytalk.service.chat.AiServiceClient.ContextPayload;
import com.historytalk.service.chat.AiServiceClient.MessageHistoryItem;
import com.historytalk.service.chat.ExamSessionService.ExamTurnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

    private static final int MAX_HISTORY_MESSAGES = 4;

    private final MessageRepository messageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final com.historytalk.repository.UserRepository userRepository;
    private final com.historytalk.service.gamification.GamificationService gamificationService;
    private final com.historytalk.service.school.token.DailyTokenAllowanceService dailyTokenAllowanceService;
    private final ExamSessionService examSessionService;
    private final ChatTokenCharger chatTokenCharger;
    private final TransactionTemplate transactionTemplate;

    // ── GET /chat/sessions/{id}/messages ──────────────────────────────────

    @Transactional(readOnly = true)
    public GetMessagesResponse getMessages(String sessionId, String userId) {
        log.info("Getting messages for session={} user={}", sessionId, userId);

        ChatSession session = chatSessionRepository.findActiveBySessionIdAndUserUid(
                UUID.fromString(sessionId), UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));

        List<Message> messages = messageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(UUID.fromString(sessionId), false);

        List<MessageResponse> messageResponses = messages.stream()
                .map(this::mapToMessageResponse)
                .toList();

        // suggestedQuestions from last ASSISTANT message
        List<String> suggestedQuestions = messages.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsFromAi()))
                .reduce((first, second) -> second) // last ASSISTANT message
                .map(m -> parseSuggestedQuestions(m.getSuggestedQuestions()))
                .orElse(Collections.emptyList());

        return GetMessagesResponse.builder()
                .messages(messageResponses)
                .suggestedQuestions(suggestedQuestions)
                .exam(session.getMode() == ChatSessionMode.EXAM ? examSessionService.getProgress(session, messages) : null)
                .build();
    }

    // ── POST /chat/messages/check ──────────────────────────────────────────

    @Override
    public void checkMessageContent(String content) {
        ChatContentPolicy.requireAppropriate(content);
    }

    // ── POST /chat/messages ────────────────────────────────────────────────
    // LLM calls take seconds and the connection pool is small, so no transaction stays open across them:
    // each turn is prepared in one short transaction, calls the LLM, then is saved in another.

    private record PreparedTurn(
            UUID sessionId,
            boolean exam,
            String characterId,
            String contextId,
            CharacterPayload characterData,
            ContextPayload contextData,
            List<MessageHistoryItem> history,
            Message userMessage,
            boolean firstUserMessage,
            String messageType) {
    }

    private record FinishedTurn(Message assistantMessage, int remainingTokens) {
    }

    public SendMessageResponse sendMessage(String userId, SendMessageRequest request) {
        log.info("Sending message in session={} user={}", request.getSessionId(), userId);

        PreparedTurn turn = transactionTemplate.execute(status -> prepareTurn(userId, request));

        if (turn.exam()) {
            ExamTurnResult result = examSessionService.submitAnswer(turn.sessionId(), request);
            return SendMessageResponse.builder()
                    .userMessage(mapToMessageResponse(result.userMessage()))
                    .assistantMessage(mapToMessageResponse(result.assistantMessage()))
                    .suggestedQuestions(Collections.emptyList())
                    .remainingTokens(result.remainingTokens())
                    .promptTokens(result.promptTokens())
                    .completionTokens(result.completionTokens())
                    .exam(result.progress())
                    .build();
        }

        // Determine whether to skip suggestions
        boolean skipSuggestions = "VOICE".equalsIgnoreCase(request.getMessageType());

        // Call BE-Python
        AiChatResult aiResult;
        try {
            aiResult = aiServiceClient.chat(
                    turn.characterId(),
                    turn.contextId(),
                    request.getContent(),
                    turn.history(),
                    turn.characterData(),
                    turn.contextData(),
                    skipSuggestions);
        } catch (RuntimeException e) {
            discardUserMessage(turn.userMessage());
            throw e;
        }

        int promptToken = 0;
        int completionToken = 0;
        if (aiResult.tokenUsage() != null) {
            promptToken = aiResult.tokenUsage().promptTokens();
            completionToken = aiResult.tokenUsage().completionTokens();
        }

        FinishedTurn finished = finishTurn(
                turn,
                aiResult.message(),
                serializeSuggestedQuestions(aiResult.suggestedQuestions()),
                serializeSuggestedQuestions(aiResult.quotesUsed()),
                promptToken,
                completionToken);

        afterTurn(userId, turn, request.getContent(), aiResult.message());

        return SendMessageResponse.builder()
                .userMessage(mapToMessageResponse(turn.userMessage()))
                .assistantMessage(mapToMessageResponse(finished.assistantMessage()))
                .suggestedQuestions(aiResult.suggestedQuestions())
                .remainingTokens(finished.remainingTokens())
                .promptTokens(promptToken)
                .completionTokens(completionToken)
                .build();
    }

    // ── POST /chat/messages/stream ─────────────────────────────────────────

    public SseEmitter sendMessageStream(String userId, SendMessageRequest request) {
        log.info("Streaming message in session={} user={}", request.getSessionId(), userId);

        PreparedTurn turn = transactionTemplate.execute(status -> prepareTurn(userId, request));

        if (turn.exam()) {
            return streamExamTurn(turn.sessionId(), request);
        }

        SseEmitter emitter = new SseEmitter(180000L); // 3 minutes timeout

        boolean skipSuggestions = "VOICE".equalsIgnoreCase(request.getMessageType());

        CompletableFuture.runAsync(() -> {
            StringBuilder fullMessage = new StringBuilder();
            AtomicInteger promptToken = new AtomicInteger(0);
            AtomicInteger completionToken = new AtomicInteger(0);
            AtomicBoolean streamFailed = new AtomicBoolean(false);
            String[] suggestedQuestions = new String[1];
            String[] quotesUsed = new String[1];

            aiServiceClient.streamChat(
                turn.characterId(),
                turn.contextId(),
                request.getContent(),
                turn.history(),
                turn.characterData(),
                turn.contextData(),
                skipSuggestions,
                // onData
                line -> {
                    try {
                        if (line.startsWith("data: ")) {
                            String jsonStr = line.substring(6);
                            JsonNode node = objectMapper.readTree(jsonStr);
                            String type = node.path("type").asText();
                            if ("text".equals(type)) {
                                String chunk = node.path("data").asText();
                                fullMessage.append(chunk);
                                emitter.send(SseEmitter.event().data(jsonStr));
                            } else if ("metadata".equals(type)) {
                                JsonNode data = node.path("data");
                                promptToken.set(data.path("promptTokens").asInt());
                                completionToken.set(data.path("completionTokens").asInt());

                                JsonNode sqNode = data.path("suggestedQuestions");
                                if (sqNode.isArray()) {
                                    List<String> sqList = objectMapper.convertValue(sqNode, new TypeReference<List<String>>(){});
                                    suggestedQuestions[0] = serializeSuggestedQuestions(sqList);
                                }

                                JsonNode quNode = data.path("quotes_used");
                                if (quNode.isArray()) {
                                    List<String> quList = objectMapper.convertValue(quNode, new TypeReference<List<String>>(){});
                                    quotesUsed[0] = serializeSuggestedQuestions(quList);
                                }

                                emitter.send(SseEmitter.event().data(jsonStr));
                            } else if ("error".equals(type)) {
                                streamFailed.set(true);
                                emitter.send(SseEmitter.event().data(jsonStr));
                            }
                        }
                    } catch (Exception ex) {
                        log.error("Error processing SSE chunk: {}", ex.getMessage());
                    }
                },
                // onComplete
                () -> {
                    try {
                        // A failed or empty reply is not saved, so it cannot pollute the conversation history.
                        if (streamFailed.get() || fullMessage.toString().isBlank()) {
                            discardUserMessage(turn.userMessage());
                            if (!streamFailed.get()) {
                                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(
                                        Map.of("type", "error", "message", "Dịch vụ AI không trả lời. Vui lòng thử lại."))));
                            }
                            emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
                            emitter.complete();
                            return;
                        }

                        aiServiceClient.recordStreamTokens(promptToken.get(), completionToken.get());

                        FinishedTurn finished = finishTurn(
                                turn,
                                fullMessage.toString(),
                                suggestedQuestions[0],
                                quotesUsed[0],
                                promptToken.get(),
                                completionToken.get());

                        afterTurn(userId, turn, request.getContent(), fullMessage.toString());

                        // Send final event to let client know remaining tokens
                        emitter.send(SseEmitter.event().data("{\"type\":\"done\",\"remainingTokens\":" + finished.remainingTokens() + "}"));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Error completing stream: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                },
                // onError
                error -> {
                    log.error("AI stream error", error);
                    discardUserMessage(turn.userMessage());
                    emitter.completeWithError(error);
                }
            );
        });

        return emitter;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private PreparedTurn prepareTurn(String userId, SendMessageRequest request) {
        ChatContentPolicy.requireAppropriate(request.getContent());

        ChatSession session = chatSessionRepository.findActiveBySessionIdAndUserUid(
                UUID.fromString(request.getSessionId()), UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chat session not found with ID: " + request.getSessionId()));

        verifyUserTokenAndSubscriptionBeforeChat(session.getUser());

        if (session.getMode() == ChatSessionMode.EXAM) {
            return new PreparedTurn(session.getSessionId(), true, null, null, null, null, null, null, false, null);
        }

        // Load existing history BEFORE saving new user message
        List<Message> existingMessages = messageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId(), false);

        boolean isFirstUserMessage = existingMessages.stream()
                .noneMatch(m -> Boolean.FALSE.equals(m.getIsFromAi()));

        String messageType = request.getMessageType() != null ? request.getMessageType() : "TEXT";

        Message userMessage = messageRepository.save(Message.builder()
                .content(request.getContent())
                .isFromAi(false)
                .chatSession(session)
                .messageType(messageType)
                .build());

        // Build history for AI (only send the last 4 messages to save tokens)
        int startIndex = Math.max(0, existingMessages.size() - MAX_HISTORY_MESSAGES);
        List<MessageHistoryItem> history = existingMessages.subList(startIndex, existingMessages.size()).stream()
                .map(m -> new MessageHistoryItem(
                        Boolean.TRUE.equals(m.getIsFromAi()) ? "assistant" : "user",
                        m.getContent()))
                .toList();

        // Build payloads to pre-fill into AI request (avoids Python→Java callback)
        return new PreparedTurn(
                session.getSessionId(),
                false,
                session.getCharacter().getCharacterId().toString(),
                session.getHistoricalContext() != null
                        ? session.getHistoricalContext().getContextId().toString()
                        : "00000000-0000-0000-0000-000000000000",
                AiServiceClient.buildCharacterPayload(session.getCharacter()),
                session.getHistoricalContext() != null
                        ? AiServiceClient.buildContextPayload(session.getHistoricalContext())
                        : null,
                history,
                userMessage,
                isFirstUserMessage,
                messageType);
    }

    private FinishedTurn finishTurn(PreparedTurn turn, String content, String suggestedQuestionsJson,
                                    String quotesJson, int promptTokens, int completionTokens) {
        return transactionTemplate.execute(status -> {
            ChatSession session = chatSessionRepository.findById(turn.sessionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Chat session not found with ID: " + turn.sessionId()));

            // Save prompt tokens to the User's message
            turn.userMessage().setToken(promptTokens);
            messageRepository.save(turn.userMessage());

            Message assistantMessage = messageRepository.save(Message.builder()
                    .content(content)
                    .isFromAi(true)
                    .suggestedQuestions(suggestedQuestionsJson)
                    .quotes(quotesJson)
                    .chatSession(session)
                    .token(completionTokens)
                    .messageType(turn.messageType())
                    .build());

            session.setLastMessageAt(LocalDateTime.now());

            return new FinishedTurn(assistantMessage, chatTokenCharger.charge(session.getUser(), promptTokens + completionTokens));
        });
    }

    private void afterTurn(String userId, PreparedTurn turn, String userContent, String assistantContent) {
        // The reply is already saved, so a failed quest update must not turn it into an error.
        try {
            gamificationService.recordProgress(userId, com.historytalk.entity.enums.QuestType.CHAT);
        } catch (RuntimeException e) {
            log.warn("Failed to record chat quest for user {}: {}", userId, e.getMessage());
        }

        // Async title generation on first user message
        if (turn.firstUserMessage()) {
            aiServiceClient.generateTitleAsync(
                    turn.sessionId().toString(),
                    turn.characterId(),
                    turn.contextId(),
                    userContent,
                    assistantContent,
                    turn.characterData(),
                    turn.contextData());
        }
    }

    private void discardUserMessage(Message userMessage) {
        try {
            transactionTemplate.executeWithoutResult(status -> messageRepository.delete(userMessage));
        } catch (RuntimeException e) {
            log.warn("Failed to remove message {} of a failed turn: {}", userMessage.getMessageId(), e.getMessage());
        }
    }

    /**
     * Streams the feedback and the follow-up question as they are written. The answer key of the next
     * question only travels between the AI service and Java, so it never reaches the client.
     */
    private SseEmitter streamExamTurn(UUID sessionId, SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(180000L);

        examSessionService.streamAnswer(sessionId, request, new ExamSessionService.ExamStreamListener() {
            @Override
            public void onText(String text) {
                send(emitter, Map.of("type", "text", "data", text));
            }

            @Override
            public void onComplete(ExamTurnResult turn) {
                MessageResponse assistant = mapToMessageResponse(turn.assistantMessage());

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("suggestedQuestions", Collections.emptyList());
                metadata.put("promptTokens", turn.promptTokens());
                metadata.put("completionTokens", turn.completionTokens());
                metadata.put("quotes_used", assistant.getQuotes());
                metadata.put("examVerdict", assistant.getExamVerdict());
                metadata.put("exam", turn.progress());

                send(emitter, Map.of("type", "metadata", "data", metadata));
                send(emitter, Map.of("type", "done", "remainingTokens", turn.remainingTokens()));
                emitter.complete();
            }

            @Override
            public void onError(RuntimeException error) {
                send(emitter, Map.of("type", "error", "message", error.getMessage() != null
                        ? error.getMessage()
                        : "Dịch vụ AI không trả lời. Vui lòng thử lại."));
                send(emitter, Map.of("type", "done"));
                emitter.complete();
            }
        });

        return emitter;
    }

    /** The client may have left mid-stream; the turn is still saved, so a failed send is only logged. */
    private void send(SseEmitter emitter, Map<String, ?> event) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
        } catch (IOException | IllegalStateException e) {
            log.debug("Exam stream event not delivered: {}", e.getMessage());
        }
    }

    private void verifyUserTokenAndSubscriptionBeforeChat(com.historytalk.entity.user.User user) {
        if (user == null) {
            return;
        }
        if (user.getRole() == com.historytalk.entity.enums.UserRole.CUSTOMER) {
            if (user.getToken() == null || user.getToken() <= 0) {
                throw new IllegalArgumentException("Bạn đã hết token. Vui lòng nạp thêm để tiếp tục chat.");
            }
        } else if (user.getRole() == com.historytalk.entity.enums.UserRole.SCHOOL_STUDENT
                || user.getRole() == com.historytalk.entity.enums.UserRole.TEACHER) {
            if (dailyTokenAllowanceService != null) {
                dailyTokenAllowanceService.verifyAndPrepareDailyAllowance(user);
            }
        } else if (user.getRole() == com.historytalk.entity.enums.UserRole.SCHOOL_ADMIN) {
            if (dailyTokenAllowanceService != null) {
                dailyTokenAllowanceService.verifySchoolSubscriptionForStaff(user);
            }
        }
    }

    private MessageResponse mapToMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getMessageId().toString())
                .sessionId(message.getChatSession().getSessionId().toString())
                .role(Boolean.TRUE.equals(message.getIsFromAi()) ? "ASSISTANT" : "USER")
                .content(message.getContent())
                .messageType(message.getMessageType() != null ? message.getMessageType() : "TEXT")
                .quotes(parseSuggestedQuestions(message.getQuotes()))
                .examVerdict(message.getExamVerdict() != null ? message.getExamVerdict().name() : null)
                .createdAt(message.getCreatedAt())
                .build();
    }

    private String serializeSuggestedQuestions(List<String> questions) {
        if (questions == null || questions.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (Exception e) {
            log.warn("Failed to serialize suggestedQuestions: {}", e.getMessage());
            return null;
        }
    }

    private List<String> parseSuggestedQuestions(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse suggestedQuestions JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
