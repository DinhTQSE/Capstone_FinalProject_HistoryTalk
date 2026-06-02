package com.historytalk.service.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
@Slf4j
public class AiServiceClient {

        private final RestClient restClient;
        private final ChatSessionRepository chatSessionRepository;
        private final AiMetricsService aiMetricsService;
        private final ObjectMapper objectMapper;

        public AiServiceClient(
                        @Value("${AI_SERVICE_URL:http://localhost:8001}") String aiServiceUrl,
                        @Value("${AI_SERVICE_USERNAME:}") String aiServiceUsername,
                        @Value("${AI_SERVICE_PASSWORD:}") String aiServicePassword,
                        ChatSessionRepository chatSessionRepository,
                        AiMetricsService aiMetricsService,
                        ObjectMapper objectMapper,
                        RestClient.Builder restClientBuilder) {
                org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(60000); // 60s
                factory.setReadTimeout(180000); // 3 minutes

                RestClient.Builder builder = restClientBuilder
                                .requestFactory(factory)
                                .baseUrl(aiServiceUrl);

                this.aiServiceUrlStr = aiServiceUrl;
                
                String authHeader = null;
                // Add Basic Auth header if credentials are configured (e.g. Nginx auth_basic)
                if (aiServiceUsername != null && !aiServiceUsername.isBlank()) {
                        String credentials = Base64.getEncoder().encodeToString(
                                        (aiServiceUsername + ":" + aiServicePassword).getBytes(StandardCharsets.UTF_8));
                        authHeader = "Basic " + credentials;
                        builder.defaultHeader(HttpHeaders.AUTHORIZATION, authHeader);
                        log.info("AI service client configured with Basic Auth for user: {}", aiServiceUsername);
                }
                this.basicAuthHeader = authHeader;

                this.restClient = builder.build();
                this.chatSessionRepository = chatSessionRepository;
                this.aiMetricsService = aiMetricsService;
                this.objectMapper = objectMapper;
                this.javaHttpClient = HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .build();
        }

        private final String aiServiceUrlStr;
        private final String basicAuthHeader;
        private final HttpClient javaHttpClient;

        // ── Inner payload types ────────────────────────────────────────────────

        public record CharacterPayload(
                        @JsonProperty("characterId") String characterId,
                        @JsonProperty("name") String name,
                        @JsonProperty("title") String title,
                        @JsonProperty("background") String background,
                        @JsonProperty("personality") String personality,
                        @JsonProperty("lifespan") String lifespan) {
        }

        public record ContextPayload(
                        @JsonProperty("contextId") String contextId,
                        @JsonProperty("name") String name,
                        @JsonProperty("description") String description,
                        @JsonProperty("era") String era,
                        @JsonProperty("year") Integer year,
                        @JsonProperty("startYear") Integer startYear,
                        @JsonProperty("endYear") Integer endYear,
                        @JsonProperty("isBC") Boolean isBC,
                        @JsonProperty("location") String location) {
        }

        public record MessageHistoryItem(
                        @JsonProperty("role") String role,
                        @JsonProperty("content") String content) {
        }

        public record AiChatResult(String message, List<String> suggestedQuestions,
                        AiMetricsService.TokenUsage tokenUsage) {
        }

        // ── Internal request / response records ───────────────────────────────

        record ChatRequest(
                        @JsonProperty("characterId") String characterId,
                        @JsonProperty("contextId") String contextId,
                        @JsonProperty("userMessage") String userMessage,
                        @JsonProperty("messageHistory") List<MessageHistoryItem> messageHistory,
                        @JsonProperty("characterData") CharacterPayload characterData,
                        @JsonProperty("contextData") ContextPayload contextData) {
        }

        record ChatResponseData(
                        @JsonProperty("message") String message,
                        @JsonProperty("suggestedQuestions") List<String> suggestedQuestions,
                        @JsonProperty("tokenUsage") AiMetricsService.TokenUsage tokenUsage) {
        }

        record ChatApiResponse(
                        @JsonProperty("success") boolean success,
                        @JsonProperty("data") ChatResponseData data) {
        }

        record GenerateTitleRequest(
                        @JsonProperty("characterId") String characterId,
                        @JsonProperty("contextId") String contextId,
                        @JsonProperty("firstUserMessage") String firstUserMessage,
                        @JsonProperty("firstAssistantMessage") String firstAssistantMessage,
                        @JsonProperty("characterData") CharacterPayload characterData,
                        @JsonProperty("contextData") ContextPayload contextData) {
        }

        record GenerateTitleData(
                        @JsonProperty("title") String title,
                        @JsonProperty("tokenUsage") AiMetricsService.TokenUsage tokenUsage) {
        }

        record GenerateTitleApiResponse(
                        @JsonProperty("success") boolean success,
                        @JsonProperty("data") GenerateTitleData data) {
        }

        record ProcessDocumentRequest(
                        @JsonProperty("doc_id") String docId,
                        @JsonProperty("entity_id") String entityId,
                        @JsonProperty("content") String content) {
        }

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

                        log.info("Received from Python AI: tokenUsage={}", response.data().tokenUsage());

                        aiMetricsService.recordRequest("chat", "success");
                        aiMetricsService.recordTokens(response.data().tokenUsage());
                        return new AiChatResult(response.data().message(), response.data().suggestedQuestions(),
                                        response.data().tokenUsage());
                } catch (RestClientException e) {
                        aiMetricsService.recordRequest("chat", "failure");
                        log.error("AI service /chat call failed: {}", e.getMessage());
                        throw new SystemException("AI service unavailable: " + e.getMessage());
                }
        }

        /**
         * Calls BE-Python POST /v1/ai/chat/stream asynchronously.
         */
        public void streamChat(
                        String characterId,
                        String contextId,
                        String userMessage,
                        List<MessageHistoryItem> messageHistory,
                        CharacterPayload characterData,
                        ContextPayload contextData,
                        Consumer<String> onData,
                        Runnable onComplete,
                        Consumer<Throwable> onError) {
            
            ChatRequest requestPayload = new ChatRequest(characterId, contextId, userMessage,
                            messageHistory, characterData, contextData);
            try {
                String requestBody = objectMapper.writeValueAsString(requestPayload);
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(this.aiServiceUrlStr + "/v1/ai/chat/stream"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                
                if (this.basicAuthHeader != null) {
                    reqBuilder.header(HttpHeaders.AUTHORIZATION, this.basicAuthHeader);
                }
                
                HttpRequest request = reqBuilder.build();
                
                javaHttpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            String errorBody = "";
                            try (Stream<String> lines = response.body()) {
                                errorBody = lines.collect(java.util.stream.Collectors.joining("\n"));
                            } catch (Exception ignored) {}
                            onError.accept(new SystemException("AI streaming failed with status " + response.statusCode() + ". Body: " + errorBody));
                            return;
                        }
                        try (Stream<String> lines = response.body()) {
                            lines.forEach(line -> {
                                if (line != null && !line.isBlank()) {
                                    onData.accept(line);
                                }
                            });
                        } catch (Exception e) {
                            onError.accept(e);
                            return;
                        }
                        onComplete.run();
                    })
                    .exceptionally(ex -> {
                        onError.accept(ex);
                        return null;
                    });
            } catch (Exception e) {
                onError.accept(e);
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
         * Calls BE-Python POST /v1/ai/documents/process asynchronously.
         * Chunks and embeds the document content into Supabase Vector DB.
         */
        @Async
        public void processDocumentAsync(String docId, String entityId, String content) {
                ProcessDocumentRequest request = new ProcessDocumentRequest(docId, entityId, content);
                try {
                        log.info("Calling AI service to process document: {}", docId);
                        restClient.post()
                                        .uri("/v1/ai/documents/process")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .toBodilessEntity();
                        log.info("Successfully sent document {} for AI processing", docId);
                } catch (RestClientException e) {
                        log.error("AI service /documents/process call failed for doc {}: {}", docId, e.getMessage());
                }
        }

        /**
         * Calls BE-Python DELETE /v1/ai/documents/{docId} asynchronously.
         * Deletes the document's chunks from Supabase Vector DB.
         */
        @Async
        public void deleteDocumentAsync(String docId) {
                try {
                        log.info("Calling AI service to delete document chunks: {}", docId);
                        restClient.delete()
                                        .uri("/v1/ai/documents/" + docId)
                                        .retrieve()
                                        .toBodilessEntity();
                        log.info("Successfully requested AI deletion for document {}", docId);
                } catch (RestClientException e) {
                        log.error("AI service DELETE /documents call failed for doc {}: {}", docId, e.getMessage());
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
        public static ContextPayload buildContextPayload(
                        com.historytalk.entity.historicalContext.HistoricalContext ctx) {
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
