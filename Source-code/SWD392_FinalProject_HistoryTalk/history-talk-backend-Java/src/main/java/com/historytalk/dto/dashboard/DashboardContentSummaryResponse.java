package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardContentSummaryResponse {

    private InventoryStats historicalContexts;
    private InventoryStats characters;
    private DocumentStats documents;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InventoryStats {
        private long total;
        private long published;
        private long active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentStats {
        private long total;
        private long active;
    }
}
