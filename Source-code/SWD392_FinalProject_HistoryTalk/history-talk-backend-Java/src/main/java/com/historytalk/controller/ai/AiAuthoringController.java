package com.historytalk.controller.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.historytalk.service.ai.AiAuthoringService;
import com.historytalk.service.chat.AiServiceClient.AuthoringResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI drafts for the story and battle-map editors. The editor gets the AI service's answer as it is, except
 * that errors use the same shape as the rest of the API.
 */
@RestController
@RequestMapping("/api/v1/ai-authoring")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'TEACHER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Authoring", description = "AI drafts of story scripts and battle-map plans")
public class AiAuthoringController {

    private final AiAuthoringService aiAuthoringService;
    private final ObjectMapper objectMapper;

    @PostMapping("/story/generate")
    @Operation(summary = "Draft a story script, or continue, rewrite or quiz part of one")
    public ResponseEntity<String> generateStory(@RequestBody ObjectNode body) {
        return toResponse(aiAuthoringService.generateStory(body));
    }

    @PostMapping("/battle-map/plan")
    @Operation(summary = "Plan factions, landmarks, phases and symbols of a battle map")
    public ResponseEntity<String> planBattleMap(@RequestBody ObjectNode body) {
        return toResponse(aiAuthoringService.planBattleMap(body));
    }

    @PostMapping("/battle-map/locate-anchors")
    @Operation(summary = "Suggest where landmarks are on a battle-map image")
    public ResponseEntity<String> locateAnchors(@RequestBody ObjectNode body) {
        return toResponse(aiAuthoringService.locateAnchors(body));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Status and result of a long draft")
    public ResponseEntity<String> getJob(@PathVariable String jobId) {
        return toResponse(aiAuthoringService.getJob(jobId));
    }

    @DeleteMapping("/jobs/{jobId}")
    @Operation(summary = "Cancel a long draft")
    public ResponseEntity<String> cancelJob(@PathVariable String jobId) {
        return toResponse(aiAuthoringService.cancelJob(jobId));
    }

    private ResponseEntity<String> toResponse(AuthoringResponse response) {
        if (response.status() == HttpStatus.NO_CONTENT.value()) {
            return ResponseEntity.noContent().build();
        }
        String body = response.status() >= 400 ? errorBody(response) : response.body();
        return ResponseEntity.status(response.status()).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** FastAPI puts the reason in "detail", as text or, for invalid bodies, as a list of field errors. */
    private String errorBody(AuthoringResponse response) {
        String message = "Dịch vụ AI không xử lý được yêu cầu.";
        try {
            JsonNode detail = objectMapper.readTree(response.body()).path("detail");
            if (detail.isTextual()) {
                message = detail.asText();
            } else if (detail.isArray() && !detail.isEmpty()) {
                JsonNode first = detail.get(0);
                message = "Dữ liệu không hợp lệ: " + first.path("loc").toString() + " " + first.path("msg").asText();
            }
        } catch (Exception ignored) {
            // Not JSON, e.g. a gateway error page; the generic message is enough.
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("errorCode", response.status());
        error.put("message", message);
        error.put("timestamp", LocalDateTime.now().toString());
        try {
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"Dịch vụ AI không xử lý được yêu cầu.\"}";
        }
    }
}
