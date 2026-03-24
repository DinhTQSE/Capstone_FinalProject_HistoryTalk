package com.historyTalk.service.chat;

import com.historyTalk.dto.chat.GetMessagesResponse;
import com.historyTalk.dto.chat.SendMessageRequest;
import com.historyTalk.dto.chat.SendMessageResponse;

public interface MessageService {
    GetMessagesResponse getMessages(String sessionId, String userId);
    SendMessageResponse sendMessage(String userId, SendMessageRequest request);
}
