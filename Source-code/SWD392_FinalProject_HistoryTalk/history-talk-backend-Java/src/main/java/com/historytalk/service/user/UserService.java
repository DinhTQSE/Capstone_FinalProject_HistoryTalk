package com.historytalk.service.user;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.user.AdminUpdateUserRequest;
import com.historytalk.dto.user.ChangePasswordRequest;
import com.historytalk.dto.user.UpdateMyProfileRequest;
import com.historytalk.dto.user.UpdateUserRoleRequest;
import com.historytalk.dto.user.UserProfileResponse;

public interface UserService {
    UserProfileResponse getMyProfile(String userId);

    UserProfileResponse updateMyProfile(String userId, UpdateMyProfileRequest request);

    void changeMyPassword(String userId, ChangePasswordRequest request);

    PaginatedResponse<UserProfileResponse> listUsers(int page, int size);

    UserProfileResponse getUserById(String userId);

    UserProfileResponse adminUpdateUser(String userId, AdminUpdateUserRequest request);

    UserProfileResponse updateUserRole(String userId, UpdateUserRoleRequest request);
}
