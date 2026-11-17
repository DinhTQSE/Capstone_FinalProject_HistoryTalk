package com.historytalk.service.school.token;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The "token day" used by school daily allowances and token requests. It rolls over at 00:00 Vietnam time,
 * while timestamps such as {@code lastTokenResetAt} are stored in the JVM default zone (UTC).
 */
public final class TokenDay {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private TokenDay() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** 00:00 Vietnam time today, expressed in the JVM default zone so it compares with stored timestamps. */
    public static LocalDateTime startOfToday() {
        return today().atStartOfDay(ZONE).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** True when an allowance last reset at {@code lastResetAt} belongs to an earlier token day. */
    public static boolean isNewDay(LocalDateTime lastResetAt) {
        return lastResetAt == null || lastResetAt.isBefore(startOfToday());
    }
}
