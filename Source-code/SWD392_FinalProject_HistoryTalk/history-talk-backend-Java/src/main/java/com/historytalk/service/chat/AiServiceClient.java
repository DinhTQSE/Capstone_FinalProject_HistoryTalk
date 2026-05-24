package com.historytalk.service.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.ChatSessionRepository;
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
    private final AiMetricsService aiMetricsService;

    public AiServiceClient(
            @Value("${AI_SERVICE_URL:http://localhost:8001}") String aiServiceUrl,
            ChatSessionRepository chatSessionRepository,
            AiMetricsService aiMetricsService,
            RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(aiServiceUrl)
                .build();
        this.chatSessionRepository = chatSessionRepository;
        this.aiMetricsService = aiMetricsService;
    }

    // ── Inner payload types ────────────────────────────────────────────────

    public record CharacterPayload(
            @JsonProperty("characterId") String characterId,
            @JsonProperty("name") String name,
            @JsonProperty("title") String title,
            @JsonProperty("background") String background,
            @JsonProperty("personality") String personality,
            @JsonProperty("lifespan") String lifespan) {}

    public record ContextPayload(
            @JsonProperty("contextId") String contextId,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("era") String era,
            @JsonProperty("year") Integer year,
            @JsonProperty("startYear") Integer startYear,
            @JsonProperty("endYear") Integer endYear,
            @JsonProperty("isBC") Boolean isBC,
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
            @JsonProperty("suggestedQuestions") List<String> suggestedQuestions,
            @JsonProperty("tokenUsage") AiMetricsService.TokenUsage tokenUsage) {}

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
            @JsonProperty("title") String title,
            @JsonProperty("tokenUsage") AiMetricsService.TokenUsage tokenUsage) {}

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
            aiMetricsService.recordRequest("chat", "success");
            aiMetricsService.recordTokens(response.data().tokenUsage());
            return new AiChatResult(response.data().message(), response.data().suggestedQuestions());
        } catch (RestClientException e) {
            aiMetricsService.recordRequest("chat", "failure");
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
                aiMetricsService.recordRequest("generate_title", "success");
                aiMetricsService.recordTokens(response.data().tokenUsage());
                String title = response.data().title();
                chatSessionRepository.findById(UUID.fromString(sessionId)).ifPresent(session -> {
                    session.setTitle(title);
                    chatSessionRepository.save(session);
                    log.info("Session {} title updated to: {}", sessionId, title);
                });
            }
        } catch (Exception e) {
            aiMetricsService.recordRequest("generate_title", "failure");
            log.warn("Failed to generate title for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Build CharacterPayload from a Character entity.
     */
    public static CharacterPayload buildCharacterPayload(com.historytalk.entity.character.Character c) {
        String born = "";
        if (c.getBornYear() != null) {
            born = (c.getBornDay() != null ? c.getBornDay() + "/" : "") + 
                   (c.getBornMonth() != null ? c.getBornMonth() + "/" : "") + 
                   c.getBornYear() + 
                   (Boolean.TRUE.equals(c.getIsBornBc()) ? " BC" : "");
        }
        String died = "";
        if (c.getDeathYear() != null) {
            died = (c.getDeathDay() != null ? c.getDeathDay() + "/" : "") + 
                   (c.getDeathMonth() != null ? c.getDeathMonth() + "/" : "") + 
                   c.getDeathYear() + 
                   (Boolean.TRUE.equals(c.getIsDeathBc()) ? " BC" : "");
        }
        String lifespan = null;
        if (!born.isEmpty() || !died.isEmpty()) {
            lifespan = born + " - " + died;
        }
        return new CharacterPayload(
                c.getCharacterId().toString(),
                c.getName(),
                c.getTitle(),
                c.getBackground(),
                c.getPersonality(),
                lifespan);
    }

    /**
     * Build ContextPayload from a HistoricalContext entity.
     */
    public static ContextPayload buildContextPayload(com.historytalk.entity.historicalContext.HistoricalContext ctx) {
        return new ContextPayload(
                ctx.getContextId().toString(),
                ctx.getName(),
                ctx.getDescription(),
                ctx.getEra() != null ? ctx.getEra().name() : null,
                ctx.getYear(),
                ctx.getStartYear(),
                ctx.getEndYear(),
                ctx.getIsBC(),
                ctx.getLocation());
    }
}
