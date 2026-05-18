package com.historytalk.application.chat.service;

import com.historytalk.presentation.chat.dto.ChatHistoryGroupResponse;
import java.util.List;

public interface ChatHistoryService {
    List<ChatHistoryGroupResponse> getHistory(String userId);
}
