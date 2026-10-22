package com.historytalk.repository.dashboard;

import java.time.LocalDateTime;
import java.util.UUID;

public interface StudentTokenUsageDetailProjection {
    UUID getStudentId();
    String getStudentCode();
    String getFullName();
    String getUserName();
    String getEmail();
    Long getTokensUsedTotal();
    Long getTokensUsedToday();
    Integer getDailyTokensUsed();
    Integer getDailyBonusTokens();
    Long getMessageCount();
    LocalDateTime getLastActiveAt();
}
