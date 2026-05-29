package com.historytalk.mapper.user;

import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.user.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserProfileResponse toProfileResponse(User user) {
        Tier tier = user.getTier();
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
                .tierId(tier != null && tier.getTierId() != null ? tier.getTierId().toString() : null)
                .tierTitle(tier != null ? tier.getTitle() : null)
                .token(user.getToken())
                .lastActiveDate(user.getLastActiveDate())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .deletedAt(user.getDeletedAt())
                .build();
    }
}
