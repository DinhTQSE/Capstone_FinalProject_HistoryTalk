package com.historytalk.service.school.token;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The "token day" used by school daily allowances and token requests. It rolls over at 00:00 UTC,
 * matching stored UTC timestamps such as {@code lastTokenResetAt}.
 */
public final class TokenDay {

    public static final ZoneId ZONE = ZoneOffset.UTC;

    private TokenDay() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** 00:00 UTC today, matching stored UTC timestamps. */
    public static LocalDateTime startOfToday() {
        return today().atStartOfDay();
    }

    /** True when an allowance last reset at {@code lastResetAt} belongs to an earlier token day. */
    public static boolean isNewDay(LocalDateTime lastResetAt) {
        return lastResetAt == null || lastResetAt.isBefore(startOfToday());
    }
}
