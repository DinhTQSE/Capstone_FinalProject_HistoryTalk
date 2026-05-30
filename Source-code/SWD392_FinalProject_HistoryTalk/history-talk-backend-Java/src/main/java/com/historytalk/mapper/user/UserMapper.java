package com.historytalk.mapper.user;

import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.user.User;

import java.time.LocalDateTime;

public interface UserMapper {
    /**
     * Maps a User to its profile response.
     *
     * @param user              the user entity
     * @param activeTier        the currently active Tier resolved from UserTier (null = no active tier)
     * @param subscriptionEndTime the end time of the active subscription (null = free / no paid sub)
     */
    UserProfileResponse toProfileResponse(User user, Tier activeTier, LocalDateTime subscriptionEndTime);
}
