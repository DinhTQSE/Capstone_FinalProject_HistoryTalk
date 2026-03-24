package com.historyTalk.service.chat;

import com.historyTalk.dto.chat.ChatHistoryGroupResponse;
import java.util.List;

public interface ChatHistoryService {
    List<ChatHistoryGroupResponse> getHistory(String userId);
}
