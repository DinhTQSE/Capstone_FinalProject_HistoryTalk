package com.historytalk.service.user;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.user.AdminUpdateUserRequest;
import com.historytalk.dto.user.ChangePasswordRequest;
import com.historytalk.dto.user.UpdateMyProfileRequest;
import com.historytalk.dto.user.UpdateUserRoleRequest;
import com.historytalk.dto.user.UserProfileResponse;
import com.historytalk.dto.user.BulkRestoreUsersResponse;
import com.historytalk.dto.user.UserDashboardResponse;
import com.historytalk.entity.enums.Gender;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.mapper.user.UserMapper;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.dashboard.DashboardTokenSummaryProjection;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserTierRepository userTierRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final MessageRepository messageRepository;

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

        // Daily token top-up based on ALL currently active tiers
        LocalDate today = LocalDate.now();
        if (user.getLastTokenResetAt() == null ||
                !user.getLastTokenResetAt().toLocalDate().isEqual(today)) {
            
            List<UserTier> allActiveTiers = userTierRepository.findAllActiveByUid(uid, now);
            int totalTokensToAdd = 0;
            
            for (UserTier ut : allActiveTiers) {
                if (ut.getTier() != null && ut.getTier().getLimitedToken() != null) {
                    totalTokensToAdd += ut.getTier().getLimitedToken();
                }
            }

            if (totalTokensToAdd > 0) {
                user.setToken((user.getToken() == null ? 0 : user.getToken()) + totalTokensToAdd);
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

    @Override
    @Transactional(readOnly = true)
    public UserDashboardResponse getUserDashboard(String userId) {
        User user = loadActiveUser(userId);
        UUID uid = user.getUid();
        LocalDateTime now = LocalDateTime.now();

        // 1. Learning Analytics
        List<QuizSession> completedSessions = quizSessionRepository
                .findCompletedByUserUid(uid, PageRequest.of(0, 100))
                .getContent();

        long totalQuizzesAttempted = completedSessions.size();
        double totalScore = 0.0;
        Map<String, Long> eraDistribution = new HashMap<>();

        for (QuizSession s : completedSessions) {
            double sessionPercentage = 0.0;
            if (s.getScore() != null) {
                int questionCount = (s.getQuiz() != null && s.getQuiz().getQuestions() != null && !s.getQuiz().getQuestions().isEmpty())
                        ? s.getQuiz().getQuestions().size() : 1;
                sessionPercentage = s.getScore() <= questionCount ? (s.getScore() * 100.0 / questionCount) : s.getScore().doubleValue();
            }
            totalScore += sessionPercentage;

            if (s.getQuiz() != null && s.getQuiz().getHistoricalContext() != null && s.getQuiz().getHistoricalContext().getEra() != null) {
                String era = s.getQuiz().getHistoricalContext().getEra().name();
                eraDistribution.put(era, eraDistribution.getOrDefault(era, 0L) + 1);
            }
        }

        double averageQuizScore = totalQuizzesAttempted > 0 ? totalScore / totalQuizzesAttempted : 0.0;

        List<UserDashboardResponse.RecentQuizItem> recentQuizzes = completedSessions.stream()
                .limit(5)
                .map(s -> {
                    double pct = 0.0;
                    if (s.getScore() != null) {
                        int qCount = (s.getQuiz() != null && s.getQuiz().getQuestions() != null && !s.getQuiz().getQuestions().isEmpty())
                                ? s.getQuiz().getQuestions().size() : 1;
                        pct = s.getScore() <= qCount ? (s.getScore() * 100.0 / qCount) : s.getScore().doubleValue();
                    }
                    return UserDashboardResponse.RecentQuizItem.builder()
                            .sessionId(s.getSessionId().toString())
                            .quizTitle(s.getQuiz() != null ? s.getQuiz().getTitle() : "Unknown")
                            .percentage(Math.round(pct * 10.0) / 10.0)
                            .completedAt(s.getEndTime())
                            .build();
                })
                .toList();

        UserDashboardResponse.LearningAnalytics learning = UserDashboardResponse.LearningAnalytics.builder()
                .totalQuizzesAttempted(totalQuizzesAttempted)
                .averageScorePercentage(Math.round(averageQuizScore * 10.0) / 10.0)
                .eraDistribution(eraDistribution)
                .recentQuizzes(recentQuizzes)
                .build();

        // 2. AI Usage Analytics
        Optional<UserTier> activeSubOpt = userTierRepository.findCurrentActiveByUid(uid, now);
        String tierTitle = activeSubOpt.map(ut -> ut.getTier().getTitle()).orElse("free");

        DashboardTokenSummaryProjection tokenSummary = messageRepository.sumTokensForUser(uid);
        long promptTokens = tokenSummary != null && tokenSummary.getPromptTokens() != null ? tokenSummary.getPromptTokens() : 0L;
        long completionTokens = tokenSummary != null && tokenSummary.getCompletionTokens() != null ? tokenSummary.getCompletionTokens() : 0L;
        long totalTokensUsed = tokenSummary != null && tokenSummary.getTotalTokens() != null ? tokenSummary.getTotalTokens() : 0L;

        List<UserDashboardResponse.TopCharacterItem> topCharacters = messageRepository
                .findTopCharactersForUser(uid, 3)
                .stream()
                .map(tc -> UserDashboardResponse.TopCharacterItem.builder()
                        .characterId(tc.getCharacterId())
                        .name(tc.getName() != null ? tc.getName() : "Unknown Character")
                        .messageCount(tc.getMessageCount() != null ? tc.getMessageCount() : 0L)
                        .tokenUsed(tc.getTokenUsed() != null ? tc.getTokenUsed() : 0L)
                        .build())
                .toList();

        UserDashboardResponse.AiUsageAnalytics aiUsage = UserDashboardResponse.AiUsageAnalytics.builder()
                .currentBalance(user.getToken() != null ? user.getToken() : 0)
                .tier(tierTitle)
                .totalTokensUsed(totalTokensUsed)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .topCharacters(topCharacters)
                .build();

        return UserDashboardResponse.builder()
                .learning(learning)
                .aiUsage(aiUsage)
                .build();
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
