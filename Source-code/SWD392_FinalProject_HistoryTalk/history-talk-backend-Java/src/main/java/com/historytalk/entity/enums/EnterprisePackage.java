package com.historytalk.entity.enums;

import lombok.Getter;

@Getter
public enum EnterprisePackage {
    ENTERPRISE_SMALL(435_750_000, 600, 15, 5_000, 5_000, 70_200_000L, 10, 63_180_000L),
    ENTERPRISE_MEDIUM(1_016_750_000, 1_400, 35, 5_000, 5_000, 163_800_000L, 20, 131_040_000L),
    ENTERPRISE_LARGE(1_452_500_000, 2_000, 50, 5_000, 5_000, 234_000_000L, 30, 163_800_000L),
    ENTERPRISE_CUSTOM(0, 0, 0, 5_000, 5_000, 0L, 0, 0L);

    private final int tokens;
    private final int recommendedStudents;
    private final int recommendedTeachers;
    private final int defaultDailyQuota;
    private final int defaultDailyTeacherQuota;
    private final long subtotalVnd;
    private final int discountPercent;
    private final long finalPriceVnd;

    EnterprisePackage(
            int tokens,
            int recommendedStudents,
            int recommendedTeachers,
            int defaultDailyQuota,
            int defaultDailyTeacherQuota,
            long subtotalVnd,
            int discountPercent,
            long finalPriceVnd) {
        this.tokens = tokens;
        this.recommendedStudents = recommendedStudents;
        this.recommendedTeachers = recommendedTeachers;
        this.defaultDailyQuota = defaultDailyQuota;
        this.defaultDailyTeacherQuota = defaultDailyTeacherQuota;
        this.subtotalVnd = subtotalVnd;
        this.discountPercent = discountPercent;
        this.finalPriceVnd = finalPriceVnd;
    }
}
