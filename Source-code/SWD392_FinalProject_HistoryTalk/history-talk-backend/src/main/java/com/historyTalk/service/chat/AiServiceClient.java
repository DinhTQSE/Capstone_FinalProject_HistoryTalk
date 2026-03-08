package com.historyTalk.service.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historyTalk.exception.SystemException;
import com.historyTalk.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AiServiceClient {

    private final RestClient restClient;
    private final ChatSessionRepository chatSessionRepository;

    public AiServiceClient(
            @Value("${AI_SERVICE_URL:http://localhost:8001}") String aiServiceUrl,
            ChatSessionRepository chatSessionRepository,
            RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(aiServiceUrl)
                .build();
        this.chatSessionRepository = chatSessionRepository;
    }

    // ── Inner payload types ────────────────────────────────────────────────

    public record CharacterPayload(
            @JsonProperty("characterId") String characterId,
            @JsonProperty("name") String name,
            @JsonProperty("title") String title,
            @JsonProperty("background") String background,
            @JsonProperty("personality") String personality,
            @JsonProperty("lifespan") String lifespan,
            @JsonProperty("side") String side) {}

    public record ContextPayload(
            @JsonProperty("contextId") String contextId,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("era") String era,
            @JsonProperty("year") Integer year,
            @JsonProperty("location") String location) {}

    public record MessageHistoryItem(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content) {}

    public record AiChatResult(String message, List<String> suggestedQuestions) {}

    // ── Internal request / response records ───────────────────────────────

    record ChatRequest(
            @JsonProperty("characterId") String characterId,
            @JsonProperty("contextId") String contextId,
            @JsonProperty("userMessage") String userMessage,
            @JsonProperty("messageHistory") List<MessageHistoryItem> messageHistory,
            @JsonProperty("characterData") CharacterPayload characterData,
            @JsonProperty("contextData") ContextPayload contextData) {}

    record ChatResponseData(
            @JsonProperty("message") String message,
            @JsonProperty("suggestedQuestions") List<String> suggestedQuestions) {}

    record ChatApiResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("data") ChatResponseData data) {}

    record GenerateTitleRequest(
            @JsonProperty("characterId") String characterId,
            @JsonProperty("contextId") String contextId,
            @JsonProperty("firstUserMessage") String firstUserMessage,
            @JsonProperty("firstAssistantMessage") String firstAssistantMessage,
            @JsonProperty("characterData") CharacterPayload characterData,
            @JsonProperty("contextData") ContextPayload contextData) {}

    record GenerateTitleData(
            @JsonProperty("title") String title) {}

    record GenerateTitleApiResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("data") GenerateTitleData data) {}

    // ── Public methods ────────────────────────────────────────────────────

    /**
     * Calls BE-Python POST /v1/ai/chat.
     * Passes characterData and contextData to skip the Python→Java callback.
     */
    public AiChatResult chat(
            String characterId,
            String contextId,
            String userMessage,
            List<MessageHistoryItem> messageHistory,
            CharacterPayload characterData,
            ContextPayload contextData) {

        ChatRequest request = new ChatRequest(characterId, contextId, userMessage,
                messageHistory, characterData, contextData);
        try {
            ChatApiResponse response = restClient.post()
                    .uri("/v1/ai/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatApiResponse.class);

            if (response == null || response.data() == null) {
                throw new SystemException("Empty response from AI service");
            }
            return new AiChatResult(response.data().message(), response.data().suggestedQuestions());
        } catch (RestClientException e) {
            log.error("AI service /chat call failed: {}", e.getMessage());
            throw new SystemException("AI service unavailable: " + e.getMessage());
        }
    }

    /**
     * Calls BE-Python POST /v1/ai/generate-title asynchronously (fire-and-forget).
     * Updates the session title in DB after receiving the response.
     */
    @Async
    @Transactional
    public void generateTitleAsync(
            String sessionId,
            String characterId,
            String contextId,
            String firstUserMessage,
            String firstAssistantMessage,
            CharacterPayload characterData,
            ContextPayload contextData) {

        try {
            GenerateTitleRequest request = new GenerateTitleRequest(
                    characterId, contextId, firstUserMessage, firstAssistantMessage,
                    characterData, contextData);

            GenerateTitleApiResponse response = restClient.post()
                    .uri("/v1/ai/generate-title")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GenerateTitleApiResponse.class);

            if (response != null && response.data() != null) {
                String title = response.data().title();
                chatSessionRepository.findById(UUID.fromString(sessionId)).ifPresent(session -> {
                    session.setTitle(title);
                    chatSessionRepository.save(session);
                    log.info("Session {} title updated to: {}", sessionId, title);
                });
            }
        } catch (Exception e) {
            log.warn("Failed to generate title for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Build CharacterPayload from a Character entity.
     */
    public static CharacterPayload buildCharacterPayload(com.historyTalk.entity.character.Character c) {
        return new CharacterPayload(
                c.getCharacterId().toString(),
                c.getName(),
                c.getTitle(),
                c.getBackground(),
                c.getPersonality(),
                c.getLifespan(),
                c.getSide());
    }

    /**
     * Build ContextPayload from a HistoricalContext entity.
     */
    public static ContextPayload buildContextPayload(com.historyTalk.entity.historicalContext.HistoricalContext ctx) {
        return new ContextPayload(
                ctx.getContextId().toString(),
                ctx.getName(),
                ctx.getDescription(),
                ctx.getEra() != null ? ctx.getEra().name() : null,
                ctx.getYear(),
                ctx.getLocation());
    }
}
