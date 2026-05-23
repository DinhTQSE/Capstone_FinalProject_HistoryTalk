package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardOverviewResponse {

    private UserOverview users;
    private RoleOverview roles;
    private ContentOverview content;
    private ChatOverview chat;
    private SystemHealthOverview systemHealth;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserOverview {
        private long total;
        private long active;
        private long inactive;
        private long deleted;
        private long newToday;
        private long newThisMonth;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoleOverview {
        private long customers;
        private long contentAdmins;
        private long systemAdmins;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContentOverview {
        private long historicalContexts;
        private long publishedHistoricalContexts;
        private long characters;
        private long publishedCharacters;
        private long documents;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatOverview {
        private long sessions;
        private long messages;
        private long messagesToday;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SystemHealthOverview {
        private String status;
        private LocalDateTime lastCheckedAt;
    }
}
