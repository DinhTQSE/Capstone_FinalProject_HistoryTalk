package com.historytalk.service.chat;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.chat.ChatScenarioResponse;
import com.historytalk.dto.chat.ChatSessionResponse;
import com.historytalk.dto.chat.CreateChatSessionRequest;
import com.historytalk.entity.enums.EventEra;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatSessionService {
    List<ChatSessionResponse> getSessions(String userId, String characterId);
    ChatSessionResponse createSession(String userId, String userRole, CreateChatSessionRequest request);
    void deleteSession(String sessionId, String userId, String userRole);
    void softDeleteSession(String sessionId, String userId, String userRole);
    PaginatedResponse<ChatScenarioResponse> getScenarios(String search, EventEra era, Pageable pageable);
}
