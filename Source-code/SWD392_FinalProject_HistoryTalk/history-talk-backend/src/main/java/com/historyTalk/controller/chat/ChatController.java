package com.historyTalk.controller.chat;

import com.historyTalk.dto.ApiResponse;
import com.historyTalk.dto.chat.ChatSessionResponse;
import com.historyTalk.dto.chat.CreateChatSessionRequest;
import com.historyTalk.service.chat.ChatSessionService;
import com.historyTalk.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;

    /**
     * GET /v1/chat/sessions?contextId=&characterId=
     * Returns all sessions of the authenticated user filtered by context and character.
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<ChatSessionResponse>>> getSessions(
            @RequestParam String contextId,
            @RequestParam String characterId) {

        String userId = SecurityUtils.getUserId();
        List<ChatSessionResponse> sessions = chatSessionService.getSessions(userId, contextId, characterId);
        return ResponseEntity.ok(ApiResponse.success(sessions, "Sessions retrieved successfully"));
    }

    /**
     * POST /v1/chat/sessions
     * Creates a new chat session for the authenticated user.
     */
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession(
            @Valid @RequestBody CreateChatSessionRequest request) {

        String userId = SecurityUtils.getUserId();
        ChatSessionResponse session = chatSessionService.createSession(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(session, "Session created successfully"));
    }

    /**
     * DELETE /v1/chat/sessions/{id}
     * Deletes a session owned by the authenticated user.
     */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id) {
        String userId = SecurityUtils.getUserId();
        chatSessionService.deleteSession(id, userId);
        return ResponseEntity.noContent().build();
    }
}
