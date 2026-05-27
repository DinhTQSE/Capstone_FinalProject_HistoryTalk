package com.historytalk.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.chat.GetMessagesResponse;
import com.historytalk.dto.chat.MessageResponse;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.dto.chat.SendMessageResponse;
import com.historytalk.entity.chat.ChatSession;
import com.historytalk.entity.chat.Message;

import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.service.chat.AiServiceClient;
import com.historytalk.service.chat.AiServiceClient.AiChatResult;
import com.historytalk.service.chat.AiServiceClient.CharacterPayload;
import com.historytalk.service.chat.AiServiceClient.ContextPayload;
import com.historytalk.service.chat.AiServiceClient.MessageHistoryItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final com.historytalk.repository.UserRepository userRepository;

    // ── GET /chat/sessions/{id}/messages ──────────────────────────────────

    @Transactional(readOnly = true)
    public GetMessagesResponse getMessages(String sessionId, String userId) {
        log.info("Getting messages for session={} user={}", sessionId, userId);

        chatSessionRepository.findActiveBySessionIdAndUserUid(
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
                .build();
    }

    // ── POST /chat/messages ────────────────────────────────────────────────

    @Transactional
    public SendMessageResponse sendMessage(String userId, SendMessageRequest request) {
        log.info("Sending message in session={} user={}", request.getSessionId(), userId);

        ChatSession session = chatSessionRepository.findActiveBySessionIdAndUserUid(
                UUID.fromString(request.getSessionId()), UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chat session not found with ID: " + request.getSessionId()));

        if (session.getUser().getToken() <= 0) {
            throw new IllegalArgumentException("Bạn đã hết token. Vui lòng nạp thêm để tiếp tục chat.");
        }

        // Load existing history BEFORE saving new user message
        List<Message> existingMessages = messageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId(), false);

        long userMessageCount = existingMessages.stream()
                .filter(m -> Boolean.FALSE.equals(m.getIsFromAi()))
                .count();
        boolean isFirstUserMessage = (userMessageCount == 0);

        // Save user message
        Message userMsg = Message.builder()
                .content(request.getContent())
                .isFromAi(false)
                .chatSession(session)
                .build();
        Message savedUserMsg = messageRepository.save(userMsg);

        // Build history for AI (all existing messages, not including current user message)
        List<MessageHistoryItem> history = existingMessages.stream()
                .map(m -> new MessageHistoryItem(
                        Boolean.TRUE.equals(m.getIsFromAi()) ? "assistant" : "user",
                        m.getContent()))
                .toList();

        // Build payloads to pre-fill into AI request (avoids Python→Java callback)
        CharacterPayload characterData = AiServiceClient.buildCharacterPayload(session.getCharacter());
        ContextPayload contextData = AiServiceClient.buildContextPayload(
                session.getHistoricalContext());

        // Call BE-Python
        AiChatResult aiResult = aiServiceClient.chat(
                session.getCharacter().getCharacterId().toString(),
                session.getHistoricalContext().getContextId().toString(),
                request.getContent(),
                history,
                characterData,
                contextData);

        // Serialize suggested questions to JSON string
        String suggestedQuestionsJson = serializeSuggestedQuestions(aiResult.suggestedQuestions());

        // Save assistant message
        Integer promptToken = 0;
        Integer completionToken = 0;
        Integer totalToken = 0;
        if (aiResult.tokenUsage() != null) {
            promptToken = aiResult.tokenUsage().promptTokens();
            completionToken = aiResult.tokenUsage().completionTokens();
            totalToken = aiResult.tokenUsage().totalTokens();
        }

        // Save prompt tokens to the User's message
        savedUserMsg.setToken(promptToken);
        messageRepository.save(savedUserMsg);

        Message assistantMsg = Message.builder()
                .content(aiResult.message())
                .isFromAi(true)
                .suggestedQuestions(suggestedQuestionsJson)
                .chatSession(session)
                .token(completionToken)
                .build();
        Message savedAssistantMsg = messageRepository.save(assistantMsg);

        int remainingTokens = session.getUser().getToken() != null ? session.getUser().getToken() : 0;
        if (totalToken != null && totalToken > 0) {
            userRepository.deductTokens(session.getUser().getUid(), totalToken);
            remainingTokens = Math.max(0, remainingTokens - totalToken);
            session.getUser().setToken(remainingTokens);
        }

        // Update session lastMessageAt
        session.setLastMessageAt(LocalDateTime.now());
        chatSessionRepository.save(session);

        // Async title generation on first user message
        if (isFirstUserMessage) {
            aiServiceClient.generateTitleAsync(
                    session.getSessionId().toString(),
                    session.getCharacter().getCharacterId().toString(),
                    session.getHistoricalContext().getContextId().toString(),
                    request.getContent(),
                    aiResult.message(),
                    characterData,
                    contextData);
        }

        return SendMessageResponse.builder()
                .userMessage(mapToMessageResponse(savedUserMsg))
                .assistantMessage(mapToMessageResponse(savedAssistantMsg))
                .suggestedQuestions(aiResult.suggestedQuestions())
                .remainingTokens(remainingTokens)
                .promptTokens(promptToken)
                .completionTokens(completionToken)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private MessageResponse mapToMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getMessageId().toString())
                .sessionId(message.getChatSession().getSessionId().toString())
                .role(Boolean.TRUE.equals(message.getIsFromAi()) ? "ASSISTANT" : "USER")
                .content(message.getContent())
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
