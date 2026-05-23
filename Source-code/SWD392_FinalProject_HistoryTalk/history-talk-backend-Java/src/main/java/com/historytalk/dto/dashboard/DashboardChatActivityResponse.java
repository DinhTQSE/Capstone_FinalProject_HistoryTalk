package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardChatActivityResponse {

    private ChatSummary summary;
    private List<ChatTrendPoint> trend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatSummary {
        private long sessions;
        private long activeSessions;
        private long messages;
        private long userMessages;
        private long aiMessages;
        private long sessionsToday;
        private long messagesToday;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatTrendPoint {
        private String date;
        private long sessions;
        private long messages;
    }
}
