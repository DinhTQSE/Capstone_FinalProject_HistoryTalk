package com.historytalk.entity.enums;

import lombok.Getter;

@Getter
public enum EnterprisePackage {
    ENTERPRISE_SMALL(5_000_000, 350, 1_000),
    ENTERPRISE_MEDIUM(20_000_000, 1_500, 1_000),
    ENTERPRISE_LARGE(50_000_000, 3_500, 1_000);

    private final int tokens;
    private final int recommendedStudents;
    private final int defaultDailyQuota;

    EnterprisePackage(int tokens, int recommendedStudents, int defaultDailyQuota) {
        this.tokens = tokens;
        this.recommendedStudents = recommendedStudents;
        this.defaultDailyQuota = defaultDailyQuota;
    }
}
