package com.historytalk.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentTokenUsageDetailDto {
    private UUID studentId;
    private String studentCode;
    private String fullName;
    private String userName;
    private String email;
    private long tokensUsedTotal;
    private long tokensUsedToday;
    private long dailyTokenLimit;
    private long dailyBonusTokens;
    private long effectiveRemainingToday;
    private long messageCount;
    private LocalDateTime lastActiveAt;
}
