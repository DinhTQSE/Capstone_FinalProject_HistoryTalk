package com.historytalk.service.school.token;

import com.historytalk.entity.user.User;

public interface DailyTokenAllowanceService {

    /**
     * Verifies school subscription status, resets the student's daily usage counter
     * on a new day (without moving tokens out of the school's shared pool upfront),
     * and checks that both the student's remaining daily spending limit and the
     * school's shared pool have available tokens (> 0) for AI features.
     *
     * @param student the authenticated school student
     * @return effective remaining tokens the student can spend today
     */
    int verifyAndPrepareDailyAllowance(User student);

    /**
     * Deducts consumed AI tokens directly from the school's shared token pool
     * ({@code school.unallocatedTokenQuota}) and increments the student's daily
     * usage counter ({@code student.dailyTokensUsed}).
     *
     * @param student    the authenticated school student
     * @param tokensUsed total tokens consumed by the AI interaction
     * @return effective remaining tokens the student can spend today
     */
    int recordStudentTokenUsage(User student, int tokensUsed);

    /**
     * Verifies school subscription status for Teacher / School Admin when using AI features.
     *
     * @param staffUser the authenticated teacher or school admin
     */
    void verifySchoolSubscriptionForStaff(User staffUser);
}
