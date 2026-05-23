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
public class DashboardSystemHealthResponse {

    private String status;
    private String uptime;
    private long jvmMemoryUsed;
    private long jvmMemoryMax;
    private long httpRequestCount;
    private long httpErrorCount;
    private LocalDateTime lastCheckedAt;
}
