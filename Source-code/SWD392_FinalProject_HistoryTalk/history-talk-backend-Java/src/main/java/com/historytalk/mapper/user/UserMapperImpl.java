package com.historytalk.mapper.user;

import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.user.User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserProfileResponse toProfileResponse(User user, Tier activeTier, LocalDateTime subscriptionEndTime) {
        return UserProfileResponse.builder()
                .uid(user.getUid() != null ? user.getUid().toString() : null)
                .userName(user.getUserName())
                .email(user.getEmail())
                .role(user.getRole())
                .fullName(user.getFullName())
                .dob(user.getDob())
                .gender(user.getGender())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .avatarUrl(user.getAvatarUrl())
                .tierId(activeTier != null && activeTier.getTierId() != null
                        ? activeTier.getTierId().toString() : null)
                .tierTitle(activeTier != null ? activeTier.getTitle() : null)
                .subscriptionEndTime(subscriptionEndTime)
                .token(user.getToken())
                .lastActiveDate(user.getLastActiveDate())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .deletedAt(user.getDeletedAt())
                .build();
    }
}
