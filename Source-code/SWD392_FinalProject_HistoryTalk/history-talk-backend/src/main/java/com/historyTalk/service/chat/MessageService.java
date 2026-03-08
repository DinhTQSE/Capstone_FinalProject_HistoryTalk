package com.historyTalk.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historyTalk.dto.chat.GetMessagesResponse;
import com.historyTalk.dto.chat.MessageResponse;
import com.historyTalk.dto.chat.SendMessageRequest;
import com.historyTalk.dto.chat.SendMessageResponse;
import com.historyTalk.entity.chat.ChatSession;
import com.historyTalk.entity.chat.Message;
import com.historyTalk.entity.enums.MessageRole;
import com.historyTalk.exception.ResourceNotFoundException;
import com.historyTalk.exception.SystemException;
import com.historyTalk.repository.ChatSessionRepository;
import com.historyTalk.repository.MessageRepository;
import com.historyTalk.service.chat.AiServiceClient.AiChatResult;
import com.historyTalk.service.chat.AiServiceClient.CharacterPayload;
import com.historyTalk.service.chat.AiServiceClient.ContextPayload;
import com.historyTalk.service.chat.AiServiceClient.MessageHistoryItem;
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
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    // ── GET /chat/sessions/{id}/messages ──────────────────────────────────

    @Transactional(readOnly = true)
    public GetMessagesResponse getMessages(String sessionId, String userId) {
        log.info("Getting messages for session={} user={}", sessionId, userId);

        chatSessionRepository.findBySessionIdAndUserUid(
                UUID.fromString(sessionId), UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));

        List<Message> messages = messageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(UUID.fromString(sessionId));

        List<MessageResponse> messageResponses = messages.stream()
                .map(this::mapToMessageResponse)
                .toList();

        // suggestedQuestions from last ASSISTANT message
        List<String> suggestedQuestions = messages.stream()
                .filter(m -> m.getRole() == MessageRole.ASSISTANT)
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

        ChatSession session = chatSessionRepository.findBySessionIdAndUserUid(
                UUID.fromString(request.getSessionId()), UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chat session not found with ID: " + request.getSessionId()));

        // Load existing history BEFORE saving new user message
        List<Message> existingMessages = messageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId());

        long userMessageCount = existingMessages.stream()
                .filter(m -> m.getRole() == MessageRole.USER)
                .count();
        boolean isFirstUserMessage = (userMessageCount == 0);

        // Save user message
        Message userMsg = Message.builder()
                .content(request.getContent())
                .role(MessageRole.USER)
                .isFromAi(false)
                .chatSession(session)
                .build();
        Message savedUserMsg = messageRepository.save(userMsg);

        // Build history for AI (all existing messages, not including current user message)
        List<MessageHistoryItem> history = existingMessages.stream()
                .map(m -> new MessageHistoryItem(
                        m.getRole() == MessageRole.ASSISTANT ? "assistant" : "user",
                        m.getContent()))
                .toList();

        // Build payloads to pre-fill into AI request (avoids Python→Java callback)
        CharacterPayload characterData = AiServiceClient.buildCharacterPayload(session.getCharacter());
        ContextPayload contextData = AiServiceClient.buildContextPayload(
                session.getCharacter().getHistoricalContext());

        // Call BE-Python
        AiChatResult aiResult = aiServiceClient.chat(
                session.getCharacter().getCharacterId().toString(),
                session.getCharacter().getHistoricalContext().getContextId().toString(),
                request.getContent(),
                history,
                characterData,
                contextData);

        // Serialize suggested questions to JSON string
        String suggestedQuestionsJson = serializeSuggestedQuestions(aiResult.suggestedQuestions());

        // Save assistant message
        Message assistantMsg = Message.builder()
                .content(aiResult.message())
                .role(MessageRole.ASSISTANT)
                .isFromAi(true)
                .suggestedQuestions(suggestedQuestionsJson)
                .chatSession(session)
                .build();
        Message savedAssistantMsg = messageRepository.save(assistantMsg);

        // Update session lastMessageAt
        session.setLastMessageAt(LocalDateTime.now());
        chatSessionRepository.save(session);

        // Async title generation on first user message
        if (isFirstUserMessage) {
            aiServiceClient.generateTitleAsync(
                    session.getSessionId().toString(),
                    session.getCharacter().getCharacterId().toString(),
                    session.getCharacter().getHistoricalContext().getContextId().toString(),
                    request.getContent(),
                    aiResult.message(),
                    characterData,
                    contextData);
        }

        return SendMessageResponse.builder()
                .userMessage(mapToMessageResponse(savedUserMsg))
                .assistantMessage(mapToMessageResponse(savedAssistantMsg))
                .suggestedQuestions(aiResult.suggestedQuestions())
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private MessageResponse mapToMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getMessageId().toString())
                .sessionId(message.getChatSession().getSessionId().toString())
                .role(message.getRole().name())
                .content(message.getContent())
                .createdAt(message.getTimestamp())
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
