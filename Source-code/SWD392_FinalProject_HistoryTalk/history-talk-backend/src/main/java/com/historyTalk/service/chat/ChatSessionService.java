package com.historyTalk.service.chat;

import com.historyTalk.dto.chat.ChatSessionResponse;
import com.historyTalk.dto.chat.CreateChatSessionRequest;
import java.util.List;

public interface ChatSessionService {
    List<ChatSessionResponse> getSessions(String userId, String contextId, String characterId);
    ChatSessionResponse createSession(String userId, CreateChatSessionRequest request);
    void deleteSession(String sessionId, String userId);
    void softDeleteSession(String sessionId, String userId);
}
