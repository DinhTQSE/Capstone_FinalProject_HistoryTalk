package com.historyTalk.service.chat;

import com.historyTalk.dto.chat.ChatSessionResponse;
import com.historyTalk.dto.chat.CreateChatSessionRequest;
import com.historyTalk.entity.character.Character;
import com.historyTalk.entity.chat.ChatSession;
import com.historyTalk.entity.chat.Message;
import com.historyTalk.entity.enums.MessageRole;
import com.historyTalk.entity.user.User;
import com.historyTalk.exception.InvalidRequestException;
import com.historyTalk.exception.ResourceNotFoundException;
import com.historyTalk.repository.CharacterRepository;
import com.historyTalk.repository.ChatSessionRepository;
import com.historyTalk.repository.MessageRepository;
import com.historyTalk.repository.UserRepository;
import com.historyTalk.service.chat.AiServiceClient.AiChatResult;
import com.historyTalk.service.chat.AiServiceClient.CharacterPayload;
import com.historyTalk.service.chat.AiServiceClient.ContextPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final MessageRepository messageRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getSessions(String userId, String contextId, String characterId) {
        log.info("Getting chat sessions for user={} context={} character={}", userId, contextId, characterId);

        List<ChatSession> sessions = chatSessionRepository.findByUserAndCharacterAndContext(
                UUID.fromString(userId),
                UUID.fromString(characterId),
                UUID.fromString(contextId));

        return sessions.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public ChatSessionResponse createSession(String userId, CreateChatSessionRequest request) {
        log.info("Creating chat session for user={} context={} character={}", userId, request.getContextId(), request.getCharacterId());

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Character character = characterRepository.findById(UUID.fromString(request.getCharacterId()))
                .orElseThrow(() -> new ResourceNotFoundException("Character not found with ID: " + request.getCharacterId()));

        UUID contextIdUUID = UUID.fromString(request.getContextId());
        var selectedContext = character.getHistoricalContexts().stream()
            .filter(ctx -> ctx.getContextId().equals(contextIdUUID))
            .findFirst()
            .orElseThrow(() -> new InvalidRequestException("Character does not belong to this context"));

        ChatSession session = ChatSession.builder()
                .user(user)
                .character(character)
            .historicalContext(selectedContext)
                .title("")
                .build();

        ChatSession saved = chatSessionRepository.save(session);
        log.info("Chat session created with ID={}", saved.getSessionId());

        // Send greeting message via AI
        try {
            CharacterPayload characterData = AiServiceClient.buildCharacterPayload(character);
            ContextPayload contextData = AiServiceClient.buildContextPayload(selectedContext);

            AiChatResult greeting = aiServiceClient.chat(
                    character.getCharacterId().toString(),
                    selectedContext.getContextId().toString(),
                    "Hãy chào và giới thiệu ngắn gọn về bản thân.",
                    Collections.emptyList(),
                    characterData,
                    contextData);

            String suggestedQuestionsJson = null;
            if (greeting.suggestedQuestions() != null && !greeting.suggestedQuestions().isEmpty()) {
                try {
                    suggestedQuestionsJson = objectMapper.writeValueAsString(greeting.suggestedQuestions());
                } catch (Exception ex) {
                    log.warn("Failed to serialize greeting suggested questions: {}", ex.getMessage());
                }
            }

            Message greetingMsg = Message.builder()
                    .content(greeting.message())
                    .role(MessageRole.ASSISTANT)
                    .isFromAi(true)
                    .suggestedQuestions(suggestedQuestionsJson)
                    .chatSession(saved)
                    .build();
            messageRepository.save(greetingMsg);

            saved.setLastMessageAt(LocalDateTime.now());
            chatSessionRepository.save(saved);
        } catch (Exception e) {
            log.warn("Failed to generate greeting for session {}: {}", saved.getSessionId(), e.getMessage());
        }

        return mapToResponse(saved);
    }

    @Transactional
    public void deleteSession(String sessionId, String userId) {
        log.info("Deleting chat session={} for user={}", sessionId, userId);

        ChatSession session = chatSessionRepository
                .findBySessionIdAndUserUid(UUID.fromString(sessionId), UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));

        chatSessionRepository.delete(session);
        log.info("Chat session deleted: {}", sessionId);
    }

    @Transactional
    public void softDeleteSession(String sessionId, String userId) {
        log.info("Soft deleting chat session={} for user={}", sessionId, userId);

        ChatSession session = chatSessionRepository
                .findBySessionIdAndUserUid(UUID.fromString(sessionId), UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        session.setDeletedAt(now);
        chatSessionRepository.save(session);

        if (session.getMessages() != null) {
            session.getMessages().forEach(msg -> msg.setDeletedAt(now));
        }

        log.info("Chat session soft deleted: {}", sessionId);
    }

    private ChatSessionResponse mapToResponse(ChatSession session) {
        List<Message> messages = session.getMessages();
        String lastMessage = null;
        if (!messages.isEmpty()) {
            lastMessage = messages.stream()
                    .max(Comparator.comparing(Message::getTimestamp))
                    .map(Message::getContent)
                    .orElse(null);
        }

        return ChatSessionResponse.builder()
                .id(session.getSessionId().toString())
                .characterId(session.getCharacter().getCharacterId().toString())
            .contextId(session.getHistoricalContext().getContextId().toString())
                .title(session.getTitle())
                .lastMessage(lastMessage)
                .lastMessageAt(session.getLastMessageAt())
                .messageCount(messages.size())
                .build();
    }
}
