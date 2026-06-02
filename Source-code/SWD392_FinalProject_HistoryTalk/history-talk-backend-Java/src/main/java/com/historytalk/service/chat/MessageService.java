package com.historytalk.service.chat;

import com.historytalk.dto.chat.GetMessagesResponse;
import com.historytalk.dto.chat.SendMessageRequest;
import com.historytalk.dto.chat.SendMessageResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface MessageService {
    GetMessagesResponse getMessages(String sessionId, String userId);
    SendMessageResponse sendMessage(String userId, SendMessageRequest request);
    SseEmitter sendMessageStream(String userId, SendMessageRequest request);
}
