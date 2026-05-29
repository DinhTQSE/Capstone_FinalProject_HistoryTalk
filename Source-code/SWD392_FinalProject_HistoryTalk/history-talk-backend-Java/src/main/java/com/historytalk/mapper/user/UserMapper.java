package com.historytalk.mapper.user;

import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.entity.user.User;

public interface UserMapper {
    UserProfileResponse toProfileResponse(User user);
}
