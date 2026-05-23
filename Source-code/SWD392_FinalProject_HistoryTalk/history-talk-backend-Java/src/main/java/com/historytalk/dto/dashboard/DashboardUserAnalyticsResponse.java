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
public class DashboardUserAnalyticsResponse {

    private UserSummary summary;
    private List<RoleCount> byRole;
    private List<UserTrendPoint> trend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserSummary {
        private long total;
        private long active;
        private long inactive;
        private long deleted;
        private long recentlyActive;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoleCount {
        private String role;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserTrendPoint {
        private String date;
        private long newUsers;
        private long activeUsers;
    }
}
