package com.historytalk.service.user;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.user.AdminUpdateUserRequest;
import com.historytalk.dto.user.ChangePasswordRequest;
import com.historytalk.dto.user.UpdateMyProfileRequest;
import com.historytalk.dto.user.UpdateUserRoleRequest;
import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.entity.enums.Gender;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.mapper.user.UserMapper;
import com.historytalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String userId) {
        return userMapper.toProfileResponse(loadActiveUser(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(String userId, UpdateMyProfileRequest request) {
        User user = loadActiveUser(userId);
        applyProfileUpdate(user, request.getUserName(), request.getFullName(), request.getDob(), request.getGender(),
                request.getPhoneNumber(), request.getAddress(), request.getAvatarUrl());
        return userMapper.toProfileResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changeMyPassword(String userId, ChangePasswordRequest request) {
        User user = loadActiveUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidRequestException("Current password is incorrect");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidRequestException("New password and confirmation password do not match");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<UserProfileResponse> listUsers(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize);
        var users = userRepository.findAll(pageable);
        return PaginatedResponse.<UserProfileResponse>builder()
                .content(users.getContent().stream().map(userMapper::toProfileResponse).toList())
                .totalElements(users.getTotalElements())
                .totalPages(users.getTotalPages())
                .currentPage(users.getNumber())
                .pageSize(users.getSize())
                .hasNext(users.hasNext())
                .hasPrevious(users.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(String userId) {
        return userMapper.toProfileResponse(loadUser(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse adminUpdateUser(String userId, AdminUpdateUserRequest request) {
        User user = loadUser(userId);
        applyProfileUpdate(user, request.getUserName(), request.getFullName(), request.getDob(), request.getGender(),
                request.getPhoneNumber(), request.getAddress(), request.getAvatarUrl());
        return userMapper.toProfileResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserRole(String userId, UpdateUserRoleRequest request) {
        User user = loadUser(userId);
        user.setRole(request.getRole());
        return userMapper.toProfileResponse(userRepository.save(user));
    }

    private User loadActiveUser(String userId) {
        User user = loadUser(userId);
        if (user.getDeletedAt() != null) {
            throw new InvalidRequestException("User account has been deactivated");
        }
        return user;
    }

    private User loadUser(String userId) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void applyProfileUpdate(User user, String userName, String fullName, LocalDate dob,
                                    Gender gender, String phoneNumber,
                                    String address, String avatarUrl) {
        if (StringUtils.hasText(userName) && !userName.equalsIgnoreCase(user.getUserName())) {
            if (userRepository.existsByUserNameIgnoreCase(userName)) {
                throw new InvalidRequestException("Username already exists");
            }
            user.setUserName(userName.trim());
        }
        if (fullName != null) {
            user.setFullName(fullName.trim());
        }
        if (dob != null) {
            user.setDob(dob);
        }
        if (gender != null) {
            user.setGender(gender);
        }
        if (phoneNumber != null) {
            user.setPhoneNumber(phoneNumber.trim());
        }
        if (address != null) {
            user.setAddress(address.trim());
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl.trim());
        }
    }
}
