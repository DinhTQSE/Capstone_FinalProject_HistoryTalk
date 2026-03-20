package com.historyTalk.controller.chat;

import com.historyTalk.dto.ApiResponse;
import com.historyTalk.dto.chat.ChatHistoryGroupResponse;
import com.historyTalk.dto.chat.ChatSessionResponse;
import com.historyTalk.dto.chat.CreateChatSessionRequest;
import com.historyTalk.dto.chat.GetMessagesResponse;
import com.historyTalk.dto.chat.SendMessageRequest;
import com.historyTalk.dto.chat.SendMessageResponse;
import com.historyTalk.service.chat.ChatHistoryService;
import com.historyTalk.service.chat.ChatSessionService;
import com.historyTalk.service.chat.MessageService;
import com.historyTalk.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final MessageService messageService;
    private final ChatHistoryService chatHistoryService;

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

    /**
     * PATCH /v1/chat/sessions/{id}/soft-delete
     * Soft deletes a session owned by the authenticated user.
     */
    @PatchMapping("/sessions/{id}/soft-delete")
    public ResponseEntity<ApiResponse<?>> softDeleteSession(@PathVariable String id) {
        String userId = SecurityUtils.getUserId();
        chatSessionService.softDeleteSession(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Session soft-deleted successfully"));
    }

    /**
     * GET /v1/chat/sessions/{id}/messages
     * Returns all messages for a session owned by the authenticated user.
     */
    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<ApiResponse<GetMessagesResponse>> getMessages(@PathVariable String id) {
        String userId = SecurityUtils.getUserId();
        GetMessagesResponse result = messageService.getMessages(id, userId);
        return ResponseEntity.ok(ApiResponse.success(result, "Messages retrieved successfully"));
    }

    /**
     * POST /v1/chat/messages
     * Sends a user message and receives an AI response.
     */
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<SendMessageResponse>> sendMessage(
            @Valid @RequestBody SendMessageRequest request) {

        String userId = SecurityUtils.getUserId();
        SendMessageResponse result = messageService.sendMessage(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Message sent successfully"));
    }

    /**
     * GET /v1/chat/history
     * Returns all chat sessions of the authenticated user grouped by historical context.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatHistoryGroupResponse>>> getChatHistory() {
        String userId = SecurityUtils.getUserId();
        List<ChatHistoryGroupResponse> history = chatHistoryService.getHistory(userId);
        return ResponseEntity.ok(ApiResponse.success(history, "Chat history retrieved successfully"));
    }
}
