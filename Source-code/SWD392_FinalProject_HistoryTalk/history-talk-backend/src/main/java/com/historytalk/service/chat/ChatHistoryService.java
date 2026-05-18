package com.historytalk.service.chat;

import com.historytalk.dto.chat.ChatHistoryGroupResponse;
import java.util.List;

public interface ChatHistoryService {
    List<ChatHistoryGroupResponse> getHistory(String userId);
}

