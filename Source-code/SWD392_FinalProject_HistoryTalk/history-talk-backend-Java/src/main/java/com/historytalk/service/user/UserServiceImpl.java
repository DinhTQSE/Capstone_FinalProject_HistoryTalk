package com.historytalk.service.user;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.user.AdminUpdateUserRequest;
import com.historytalk.dto.user.ChangePasswordRequest;
import com.historytalk.dto.user.UpdateMyProfileRequest;
import com.historytalk.dto.user.UpdateUserRoleRequest;
import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.dto.user.BulkRestoreUsersResponse;
import com.historytalk.entity.enums.Gender;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.mapper.user.UserMapper;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.UserTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserTierRepository userTierRepository;

    @Override
    @Transactional
    public UserProfileResponse getMyProfile(String userId) {
        User user = loadActiveUser(userId);
        UUID uid = user.getUid();
        LocalDateTime now = LocalDateTime.now();

        // Resolve current active tier: paid tier is preferred over free (ORDER BY amount DESC)
        Optional<UserTier> activeSubOpt = userTierRepository.findCurrentActiveByUid(uid, now);
        Tier activeTier = activeSubOpt.map(UserTier::getTier).orElse(null);
        LocalDateTime subEndTime = activeSubOpt.map(UserTier::getEndTime).orElse(null);

        // Daily token top-up based on whichever tier is currently active
        LocalDate today = LocalDate.now();
        if (user.getLastTokenResetAt() == null ||
                !user.getLastTokenResetAt().toLocalDate().isEqual(today)) {
            if (activeTier != null && activeTier.getLimitedToken() != null) {
                user.setToken((user.getToken() == null ? 0 : user.getToken())
                        + activeTier.getLimitedToken());
            }
            user.setLastTokenResetAt(now);
            user = userRepository.save(user);
        }

        return userMapper.toProfileResponse(user, activeTier, subEndTime);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(String userId, UpdateMyProfileRequest request) {
        User user = loadActiveUser(userId);
        applyProfileUpdate(user, request.getUserName(), request.getFullName(), request.getDob(), request.getGender(),
                request.getPhoneNumber(), request.getAddress(), request.getAvatarUrl());
        return userMapper.toProfileResponse(userRepository.save(user), null, null);
    }

    @Override
    @Transactional
    public void changeMyPassword(String userId, ChangePasswordRequest request) {
        User user = loadActiveUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidRequestException("Mật khẩu hiện tại không chính xác");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidRequestException("Mật khẩu mới và mật khẩu xác nhận không khớp");
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
                .content(users.getContent().stream()
                        .map(u -> userMapper.toProfileResponse(u, null, null))
                        .toList())
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
        return userMapper.toProfileResponse(loadUser(userId), null, null);
    }

    @Override
    @Transactional
    public UserProfileResponse adminUpdateUser(String userId, AdminUpdateUserRequest request) {
        User user = loadUser(userId);
        applyProfileUpdate(user, request.getUserName(), request.getFullName(), request.getDob(), request.getGender(),
                request.getPhoneNumber(), request.getAddress(), request.getAvatarUrl());
        return userMapper.toProfileResponse(userRepository.save(user), null, null);
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserRole(String userId, UpdateUserRoleRequest request) {
        User user = loadUser(userId);
        user.setRole(request.getRole());
        return userMapper.toProfileResponse(userRepository.save(user), null, null);
    }

    @Override
    @Transactional
    public UserProfileResponse restoreUser(String userId) {
        User user = loadUser(userId);
        if (user.getDeletedAt() == null) {
            throw new InvalidRequestException("User account is already active");
        }
        user.setDeletedAt(null);
        return userMapper.toProfileResponse(userRepository.save(user), null, null);
    }

    @Override
    @Transactional
    public BulkRestoreUsersResponse restoreUsersBatch(List<String> userIds) {
        List<UUID> uids = userIds.stream()
                .map(UUID::fromString)
                .toList();

        List<User> deactivatedUsers = userRepository.findAllById(uids).stream()
                .filter(u -> u.getDeletedAt() != null)
                .toList();

        for (User user : deactivatedUsers) {
            user.setDeletedAt(null);
        }
        userRepository.saveAll(deactivatedUsers);

        List<String> restoredIds = deactivatedUsers.stream()
                .map(u -> u.getUid().toString())
                .toList();

        List<String> failedIds = userIds.stream()
                .filter(id -> !restoredIds.contains(id))
                .toList();

        return BulkRestoreUsersResponse.builder()
                .restoredCount(deactivatedUsers.size())
                .restoredUserIds(restoredIds)
                .failedUserIds(failedIds)
                .build();
    }

    @Override
    @Transactional
    public int restoreAllUsers() {
        return userRepository.restoreAllUsers();
    }

    private User loadActiveUser(String userId) {
        User user = loadUser(userId);
        if (user.getDeletedAt() != null) {
            throw new InvalidRequestException("Tài khoản người dùng đã bị vô hiệu hóa");
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
                throw new InvalidRequestException("Tên đăng nhập đã tồn tại");
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
