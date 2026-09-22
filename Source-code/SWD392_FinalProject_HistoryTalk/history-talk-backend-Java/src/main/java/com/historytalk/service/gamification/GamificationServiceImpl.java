package com.historytalk.service.gamification;

import com.historytalk.dto.gamification.*;
import com.historytalk.entity.enums.QuestType;
import com.historytalk.entity.gamification.DailyCheckIn;
import com.historytalk.entity.gamification.DailyQuest;
import com.historytalk.entity.gamification.UserQuestProgress;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.gamification.DailyCheckInRepository;
import com.historytalk.repository.gamification.DailyQuestRepository;
import com.historytalk.repository.gamification.UserQuestProgressRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationServiceImpl implements GamificationService {

    private final DailyQuestRepository questRepository;
    private final UserQuestProgressRepository progressRepository;
    private final DailyCheckInRepository checkInRepository;
    private final UserRepository userRepository;

    @PostConstruct
    public void seedDefaultQuests() {
        try {
            if (questRepository.count() == 0) {
                List<DailyQuest> defaultQuests = List.of(
                        DailyQuest.builder()
                                .questId("chat_once")
                                .type(QuestType.CHAT)
                                .title("Trò chuyện với một nhân vật lịch sử")
                                .target(1)
                                .rewardTokens(500)
                                .orderIndex(1)
                                .isActive(true)
                                .build(),
                        DailyQuest.builder()
                                .questId("quiz_once")
                                .type(QuestType.QUIZ)
                                .title("Hoàn thành 1 bài trắc nghiệm lịch sử")
                                .target(1)
                                .rewardTokens(500)
                                .orderIndex(2)
                                .isActive(true)
                                .build(),
                        DailyQuest.builder()
                                .questId("read_context_once")
                                .type(QuestType.READ_CONTEXT)
                                .title("Khám phá 1 bối cảnh lịch sử")
                                .target(1)
                                .rewardTokens(300)
                                .orderIndex(3)
                                .isActive(true)
                                .build()
                );
                questRepository.saveAll(defaultQuests);
                log.info("Default daily quests seeded successfully");
            }
        } catch (Exception e) {
            log.warn("Failed to seed default quests: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TodayGamificationResponse getTodayState(String userId) {
        UUID userUid = UUID.fromString(userId);
        LocalDate today = LocalDate.now();

        Optional<DailyCheckIn> todayCheckIn = checkInRepository.findByUserUidAndDate(userUid, today);
        Optional<DailyCheckIn> lastCheckIn = checkInRepository.findFirstByUserUidOrderByDateDesc(userUid);

        int streakCount = todayCheckIn.map(DailyCheckIn::getStreakCount)
                .orElseGet(() -> lastCheckIn.map(DailyCheckIn::getStreakCount).orElse(0));
        boolean studiedToday = todayCheckIn.isPresent();

        List<DailyQuest> activeQuests = questRepository.findByIsActiveTrueOrderByOrderIndexAsc();
        List<UserQuestProgress> todayProgressList = progressRepository.findByUserUidAndDate(userUid, today);
        Map<String, UserQuestProgress> progressMap = new HashMap<>();
        for (UserQuestProgress p : todayProgressList) {
            progressMap.put(p.getQuestId(), p);
        }

        int claimableTokens = 0;
        List<TodayGamificationResponse.QuestItemDto> questDtos = new ArrayList<>();

        for (DailyQuest q : activeQuests) {
            UserQuestProgress p = progressMap.get(q.getQuestId());
            int currentProgress = p != null ? p.getProgress() : 0;
            boolean completed = currentProgress >= q.getTarget();
            boolean claimed = p != null && Boolean.TRUE.equals(p.getClaimed());

            if (completed && !claimed) {
                claimableTokens += q.getRewardTokens();
            }

            questDtos.add(TodayGamificationResponse.QuestItemDto.builder()
                    .id(q.getQuestId())
                    .title(q.getTitle())
                    .type(q.getType().name())
                    .target(q.getTarget())
                    .rewardTokens(q.getRewardTokens())
                    .progress(currentProgress)
                    .completed(completed)
                    .claimed(claimed)
                    .build());
        }

        return TodayGamificationResponse.builder()
                .date(today.toString())
                .streakCount(streakCount)
                .studiedToday(studiedToday)
                .claimableTokens(claimableTokens)
                .quests(questDtos)
                .build();
    }

    @Override
    @Transactional
    public ClaimQuestResponse claimQuestReward(String userId, String questId) {
        UUID userUid = UUID.fromString(userId);
        LocalDate today = LocalDate.now();

        User user = userRepository.findById(userUid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DailyQuest quest = questRepository.findById(questId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhiệm vụ: " + questId));

        UserQuestProgress progress = progressRepository.findByUserUidAndDateAndQuestId(userUid, today, questId)
                .orElseThrow(() -> new InvalidRequestException("Nhiệm vụ chưa được bắt đầu hoặc chưa hoàn thành"));

        if (progress.getProgress() < quest.getTarget()) {
            throw new InvalidRequestException("Nhiệm vụ chưa hoàn thành");
        }

        if (Boolean.TRUE.equals(progress.getClaimed())) {
            throw new InvalidRequestException("Nhiệm vụ này đã được nhận phần thưởng hôm nay");
        }

        progress.setClaimed(true);
        progressRepository.save(progress);

        int rewardTokens = quest.getRewardTokens();
        userRepository.addTokens(userUid, rewardTokens);

        int currentToken = user.getToken() != null ? user.getToken() : 0;
        int newBalance = currentToken + rewardTokens;

        return ClaimQuestResponse.builder()
                .questId(questId)
                .rewardTokens(rewardTokens)
                .tokenBalance(newBalance)
                .build();
    }

    @Override
    @Transactional
    public DailyCheckInResponse dailyCheckIn(String userId) {
        UUID userUid = UUID.fromString(userId);
        LocalDate today = LocalDate.now();

        User user = userRepository.findById(userUid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Optional<DailyCheckIn> todayCheckInOpt = checkInRepository.findByUserUidAndDate(userUid, today);
        int currentToken = user.getToken() != null ? user.getToken() : 0;

        if (todayCheckInOpt.isPresent()) {
            DailyCheckIn checkIn = todayCheckInOpt.get();
            return DailyCheckInResponse.builder()
                    .date(today.toString())
                    .streakCount(checkIn.getStreakCount())
                    .rewardTokens(0)
                    .tokenBalance(currentToken)
                    .alreadyCheckedInToday(true)
                    .build();
        }

        LocalDate yesterday = today.minusDays(1);
        Optional<DailyCheckIn> yesterdayCheckInOpt = checkInRepository.findByUserUidAndDate(userUid, yesterday);

        int streakCount = 1;
        if (yesterdayCheckInOpt.isPresent()) {
            streakCount = yesterdayCheckInOpt.get().getStreakCount() + 1;
        }

        DailyCheckIn newCheckIn = DailyCheckIn.builder()
                .user(user)
                .date(today)
                .streakCount(streakCount)
                .build();
        checkInRepository.save(newCheckIn);

        int rewardTokens = 200 + (Math.min(streakCount, 7) * 50); // Bonus based on streak
        userRepository.addTokens(userUid, rewardTokens);

        int newBalance = currentToken + rewardTokens;

        return DailyCheckInResponse.builder()
                .date(today.toString())
                .streakCount(streakCount)
                .rewardTokens(rewardTokens)
                .tokenBalance(newBalance)
                .alreadyCheckedInToday(false)
                .build();
    }

    @Override
    @Transactional
    public void recordProgress(String userId, QuestType questType) {
        try {
            UUID userUid = UUID.fromString(userId);
            LocalDate today = LocalDate.now();

            User user = userRepository.findById(userUid).orElse(null);
            if (user == null) return;

            List<DailyQuest> matchingQuests = questRepository.findByIsActiveTrueOrderByOrderIndexAsc()
                    .stream()
                    .filter(q -> q.getType() == questType)
                    .toList();

            for (DailyQuest q : matchingQuests) {
                UserQuestProgress p = progressRepository
                        .findByUserUidAndDateAndQuestId(userUid, today, q.getQuestId())
                        .orElseGet(() -> UserQuestProgress.builder()
                                .user(user)
                                .date(today)
                                .questId(q.getQuestId())
                                .progress(0)
                                .completed(false)
                                .claimed(false)
                                .build());

                p.setProgress(p.getProgress() + 1);
                if (p.getProgress() >= q.getTarget()) {
                    p.setCompleted(true);
                }
                progressRepository.save(p);
            }
        } catch (Exception e) {
            log.warn("Failed to record quest progress for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getStudyDays(String userId, int year, int month) {
        UUID userUid = UUID.fromString(userId);
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return checkInRepository.findAllDatesInRange(userUid, from, to)
                .stream()
                .map(LocalDate::toString)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyQuest> staffListQuests() {
        return questRepository.findAllByOrderByOrderIndexAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public DailyQuest staffGetQuest(String questId) {
        return questRepository.findById(questId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhiệm vụ: " + questId));
    }

    @Override
    @Transactional
    public DailyQuest staffUpdateQuest(String questId, UpdateQuestRequest request) {
        DailyQuest quest = staffGetQuest(questId);
        if (request.getType() != null) quest.setType(request.getType());
        if (request.getTitle() != null && !request.getTitle().isBlank()) quest.setTitle(request.getTitle());
        if (request.getTarget() != null && request.getTarget() > 0) quest.setTarget(request.getTarget());
        if (request.getRewardTokens() != null && request.getRewardTokens() >= 0) quest.setRewardTokens(request.getRewardTokens());
        if (request.getOrderIndex() != null) quest.setOrderIndex(request.getOrderIndex());
        if (request.getIsActive() != null) quest.setIsActive(request.getIsActive());
        return questRepository.save(quest);
    }
}
