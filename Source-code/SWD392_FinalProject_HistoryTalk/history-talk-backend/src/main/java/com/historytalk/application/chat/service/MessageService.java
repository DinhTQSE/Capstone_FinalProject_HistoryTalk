package com.historytalk.application.chat.service;

import com.historytalk.presentation.chat.dto.GetMessagesResponse;
import com.historytalk.presentation.chat.dto.SendMessageRequest;
import com.historytalk.presentation.chat.dto.SendMessageResponse;

public interface MessageService {
    GetMessagesResponse getMessages(String sessionId, String userId);
    SendMessageResponse sendMessage(String userId, SendMessageRequest request);
}
