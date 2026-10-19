package com.historytalk.service.school.token;

import com.historytalk.entity.user.User;

public interface DailyTokenAllowanceService {

    /**
     * Verifies school subscription status, performs lazy daily token reset
     * (refunding unused tokens from previous days back to the school pool and
     * withdrawing today's daily allowance), and verifies the student has > 0 tokens
     * for AI features.
     *
     * @param student the authenticated school student
     * @return available tokens for today
     */
    int verifyAndPrepareDailyAllowance(User student);

    /**
     * Verifies school subscription status for Teacher / School Admin when using AI features.
     *
     * @param staffUser the authenticated teacher or school admin
     */
    void verifySchoolSubscriptionForStaff(User staffUser);
}
