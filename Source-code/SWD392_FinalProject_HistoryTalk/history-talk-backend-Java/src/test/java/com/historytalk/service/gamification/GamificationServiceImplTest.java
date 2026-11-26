package com.historytalk.service.gamification;

import com.historytalk.dto.gamification.ClaimQuestResponse;
import com.historytalk.dto.gamification.DailyCheckInResponse;
import com.historytalk.dto.gamification.TodayGamificationResponse;
import com.historytalk.dto.gamification.UpdateQuestRequest;
import com.historytalk.entity.enums.QuestType;
import com.historytalk.entity.gamification.DailyCheckIn;
import com.historytalk.entity.gamification.DailyQuest;
import com.historytalk.entity.gamification.UserQuestProgress;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.gamification.DailyCheckInRepository;
import com.historytalk.repository.gamification.DailyQuestRepository;
import com.historytalk.repository.gamification.UserQuestProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GamificationServiceImplTest {

    @Mock
    private DailyQuestRepository questRepository;

    @Mock
    private UserQuestProgressRepository progressRepository;

    @Mock
    private DailyCheckInRepository checkInRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GamificationServiceImpl gamificationService;

    private UUID userId;
    private User user;
    private DailyQuest chatQuest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .uid(userId)
                .userName("student_gamification")
                .token(1000)
                .build();

        chatQuest = DailyQuest.builder()
                .questId("chat_once")
                .type(QuestType.CHAT)
                .title("Trò chuyện với một nhân vật lịch sử")
                .target(1)
                .rewardTokens(500)
                .orderIndex(1)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("WF-03 / F-11: seedDefaultQuests seeds three default daily quests when table is empty")
    void seedDefaultQuests_WhenEmpty_SeedsThreeDefaultQuests() {
        when(questRepository.count()).thenReturn(0L);

        gamificationService.seedDefaultQuests();

        verify(questRepository).saveAll(argThat((List<DailyQuest> list) -> list.size() == 3));
    }

    @Test
    @DisplayName("WF-03 / F-11 / DS-03a: First daily check-in awards streak bonus tokens and saves check-in record")
    void dailyCheckIn_FirstTimeToday_AwardsStreakBonusAndCreditsTokens() {
        LocalDate today = LocalDate.now();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(checkInRepository.findByUserUidAndDate(userId, today)).thenReturn(Optional.empty());
        when(checkInRepository.findByUserUidAndDate(userId, today.minusDays(1))).thenReturn(Optional.empty());

        DailyCheckInResponse response = gamificationService.dailyCheckIn(userId.toString());

        assertThat(response.getAlreadyCheckedInToday()).isFalse();
        assertThat(response.getStreakCount()).isEqualTo(1);
        assertThat(response.getRewardTokens()).isEqualTo(250); // 200 + 1 * 50
        assertThat(response.getTokenBalance()).isEqualTo(1250);
        verify(checkInRepository).save(any(DailyCheckIn.class));
        verify(userRepository).addTokens(userId, 250);
    }

    @Test
    @DisplayName("WF-03 / F-11: Consecutive day check-in increments streak count and computes capped bonus")
    void dailyCheckIn_WithYesterdayCheckIn_IncrementsStreakAndComputesBonus() {
        LocalDate today = LocalDate.now();
        DailyCheckIn yesterdayCheckIn = DailyCheckIn.builder()
                .user(user)
                .date(today.minusDays(1))
                .streakCount(3)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(checkInRepository.findByUserUidAndDate(userId, today)).thenReturn(Optional.empty());
        when(checkInRepository.findByUserUidAndDate(userId, today.minusDays(1)))
                .thenReturn(Optional.of(yesterdayCheckIn));

        DailyCheckInResponse response = gamificationService.dailyCheckIn(userId.toString());

        assertThat(response.getAlreadyCheckedInToday()).isFalse();
        assertThat(response.getStreakCount()).isEqualTo(4);
        assertThat(response.getRewardTokens()).isEqualTo(400); // 200 + 4 * 50
        assertThat(response.getTokenBalance()).isEqualTo(1400);
    }

    @Test
    @DisplayName("WF-03 / F-11 / DS-03b: Second check-in on the same day returns 0 reward and alreadyCheckedInToday=true")
    void dailyCheckIn_SecondTimeSameDay_ReturnsZeroRewardAndAlreadyCheckedInTrue() {
        LocalDate today = LocalDate.now();
        DailyCheckIn existing = DailyCheckIn.builder()
                .user(user)
                .date(today)
                .streakCount(2)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(checkInRepository.findByUserUidAndDate(userId, today)).thenReturn(Optional.of(existing));

        DailyCheckInResponse response = gamificationService.dailyCheckIn(userId.toString());

        assertThat(response.getAlreadyCheckedInToday()).isTrue();
        assertThat(response.getRewardTokens()).isZero();
        assertThat(response.getTokenBalance()).isEqualTo(1000);
        verify(userRepository, never()).addTokens(any(), anyInt());
    }

    @Test
    @DisplayName("WF-03 / F-11 / DS-03a: Claiming completed daily quest credits reward tokens and marks claimed")
    void claimQuestReward_CompletedAndUnclaimed_CreditsRewardTokensAndMarksClaimed() {
        LocalDate today = LocalDate.now();
        UserQuestProgress progress = UserQuestProgress.builder()
                .user(user)
                .date(today)
                .questId("chat_once")
                .progress(1)
                .completed(true)
                .claimed(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(questRepository.findById("chat_once")).thenReturn(Optional.of(chatQuest));
        when(progressRepository.findByUserUidAndDateAndQuestId(userId, today, "chat_once"))
                .thenReturn(Optional.of(progress));

        ClaimQuestResponse response = gamificationService.claimQuestReward(userId.toString(), "chat_once");

        assertThat(response.getQuestId()).isEqualTo("chat_once");
        assertThat(response.getRewardTokens()).isEqualTo(500);
        assertThat(response.getTokenBalance()).isEqualTo(1500);
        assertThat(progress.getClaimed()).isTrue();
        verify(progressRepository).save(progress);
        verify(userRepository).addTokens(userId, 500);
    }

    @Test
    @DisplayName("WF-03 / F-11 / DS-03b (MSG34): Claiming quest reward before completion throws InvalidRequestException")
    void claimQuestReward_BeforeCompletion_ThrowsInvalidRequestException() {
        LocalDate today = LocalDate.now();
        UserQuestProgress incompleteProgress = UserQuestProgress.builder()
                .user(user)
                .date(today)
                .questId("chat_once")
                .progress(0)
                .completed(false)
                .claimed(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(questRepository.findById("chat_once")).thenReturn(Optional.of(chatQuest));
        when(progressRepository.findByUserUidAndDateAndQuestId(userId, today, "chat_once"))
                .thenReturn(Optional.of(incompleteProgress));

        assertThatThrownBy(() -> gamificationService.claimQuestReward(userId.toString(), "chat_once"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Nhiệm vụ chưa hoàn thành");

        verify(userRepository, never()).addTokens(any(), anyInt());
    }

    @Test
    @DisplayName("WF-03 / F-11 / DS-03b (MSG35): Claiming already-claimed quest reward throws InvalidRequestException")
    void claimQuestReward_AlreadyClaimedToday_ThrowsInvalidRequestException() {
        LocalDate today = LocalDate.now();
        UserQuestProgress claimedProgress = UserQuestProgress.builder()
                .user(user)
                .date(today)
                .questId("chat_once")
                .progress(1)
                .completed(true)
                .claimed(true)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(questRepository.findById("chat_once")).thenReturn(Optional.of(chatQuest));
        when(progressRepository.findByUserUidAndDateAndQuestId(userId, today, "chat_once"))
                .thenReturn(Optional.of(claimedProgress));

        assertThatThrownBy(() -> gamificationService.claimQuestReward(userId.toString(), "chat_once"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("đã được nhận phần thưởng hôm nay");

        verify(userRepository, never()).addTokens(any(), anyInt());
    }

    @Test
    @DisplayName("WF-03 / F-11: recordProgress increments progress and marks quest completed when target reached")
    void recordProgress_MatchingQuest_IncrementsProgressAndMarksCompleted() {
        LocalDate today = LocalDate.now();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(questRepository.findByIsActiveTrueOrderByOrderIndexAsc()).thenReturn(List.of(chatQuest));
        when(progressRepository.findByUserUidAndDate(userId, today)).thenReturn(List.of());

        gamificationService.recordProgress(userId.toString(), QuestType.CHAT);

        verify(progressRepository).saveAll(argThat((List<UserQuestProgress> saved) ->
                saved.size() == 1
                        && saved.get(0).getProgress() == 1
                        && Boolean.TRUE.equals(saved.get(0).getCompleted())));
    }

    @Test
    @DisplayName("WF-03 / F-11: getTodayState returns streak count, claimable tokens, and quest progress list")
    void getTodayState_ReturnsStreakAndQuestItemsWithClaimableTotal() {
        LocalDate today = LocalDate.now();
        DailyCheckIn todayCheckIn = DailyCheckIn.builder()
                .user(user)
                .date(today)
                .streakCount(5)
                .build();
        UserQuestProgress progress = UserQuestProgress.builder()
                .user(user)
                .date(today)
                .questId("chat_once")
                .progress(1)
                .completed(true)
                .claimed(false)
                .build();

        when(checkInRepository.findByUserUidAndDate(userId, today)).thenReturn(Optional.of(todayCheckIn));
        when(checkInRepository.findFirstByUserUidOrderByDateDesc(userId)).thenReturn(Optional.of(todayCheckIn));
        when(questRepository.findByIsActiveTrueOrderByOrderIndexAsc()).thenReturn(List.of(chatQuest));
        when(progressRepository.findByUserUidAndDate(userId, today)).thenReturn(List.of(progress));

        TodayGamificationResponse state = gamificationService.getTodayState(userId.toString());

        assertThat(state.getStreakCount()).isEqualTo(5);
        assertThat(state.getStudiedToday()).isTrue();
        assertThat(state.getClaimableTokens()).isEqualTo(500);
        assertThat(state.getQuests()).hasSize(1);
        assertThat(state.getQuests().get(0).getCompleted()).isTrue();
    }

    @Test
    @DisplayName("WF-03 / F-11: a check-in older than yesterday breaks the streak; stats and week are filled")
    void getTodayState_StaleLastCheckIn_ResetsStreakAndReturnsStatsAndWeek() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        DailyCheckIn oldCheckIn = DailyCheckIn.builder()
                .user(user)
                .date(today.minusDays(3))
                .streakCount(4)
                .build();

        when(checkInRepository.findByUserUidAndDate(userId, today)).thenReturn(Optional.empty());
        when(checkInRepository.findFirstByUserUidOrderByDateDesc(userId)).thenReturn(Optional.of(oldCheckIn));
        when(checkInRepository.findLongestStreak(userId)).thenReturn(4);
        when(checkInRepository.countByUserUid(userId)).thenReturn(9L);
        when(checkInRepository.findAllDatesInRange(eq(userId), any(), any())).thenReturn(List.of(today.minusDays(3)));
        when(questRepository.findByIsActiveTrueOrderByOrderIndexAsc()).thenReturn(List.of());
        when(progressRepository.findByUserUidAndDate(userId, today)).thenReturn(List.of());

        TodayGamificationResponse state = gamificationService.getTodayState(userId.toString());

        assertThat(state.getStreakCount()).isZero();
        assertThat(state.getStudiedToday()).isFalse();
        assertThat(state.getLongestStreak()).isEqualTo(4);
        assertThat(state.getTotalStudyDays()).isEqualTo(9);
        assertThat(state.getWeek()).hasSize(7);
        assertThat(state.getWeek().get(0).getWeekday()).isZero();
        assertThat(state.getWeek()).filteredOn(TodayGamificationResponse.WeekDayDto::getIsToday)
                .singleElement()
                .satisfies(d -> assertThat(d.getDate()).isEqualTo(today.toString()));
    }

    @Test
    @DisplayName("WF-03 / F-11: staffUpdateQuest updates quest configuration fields")
    void staffUpdateQuest_UpdatesQuestFields() {
        UpdateQuestRequest req = new UpdateQuestRequest(
                QuestType.CHAT, "Chat 2 lần", 2, 800, 1, true);
        when(questRepository.findById("chat_once")).thenReturn(Optional.of(chatQuest));
        when(questRepository.save(any(DailyQuest.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyQuest updated = gamificationService.staffUpdateQuest("chat_once", req);

        assertThat(updated.getTitle()).isEqualTo("Chat 2 lần");
        assertThat(updated.getTarget()).isEqualTo(2);
        assertThat(updated.getRewardTokens()).isEqualTo(800);
    }
}
