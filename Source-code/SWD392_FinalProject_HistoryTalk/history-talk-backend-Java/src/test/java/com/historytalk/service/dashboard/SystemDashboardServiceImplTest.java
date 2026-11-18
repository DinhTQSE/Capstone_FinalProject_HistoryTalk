package com.historytalk.service.dashboard;

import com.historytalk.dto.dashboard.DashboardChatActivityResponse;
import com.historytalk.dto.dashboard.DashboardContentSummaryResponse;
import com.historytalk.dto.dashboard.DashboardOverviewResponse;
import com.historytalk.dto.dashboard.DashboardQuizAnalyticsResponse;
import com.historytalk.dto.dashboard.DashboardRevenueResponse;
import com.historytalk.dto.dashboard.DashboardTokenUsageResponse;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.PaymentOrderType;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MessageRepository;
import com.historytalk.repository.QuizAnswerDetailRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.repository.QuizSessionRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.dashboard.DashboardTokenBalanceSummaryProjection;
import com.historytalk.repository.dashboard.DashboardTokenSummaryProjection;
import com.historytalk.repository.dashboard.DashboardTopCharacterProjection;
import com.historytalk.repository.dashboard.DashboardTopWrongQuestionProjection;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.PaymentTransactionRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.payment.UserTierRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthEndpoint;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemDashboardServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private HistoricalContextRepository historicalContextRepository;
    @Mock private CharacterRepository characterRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private TierRepository tierRepository;
    @Mock private UserTierRepository userTierRepository;
    @Mock private QuizRepository quizRepository;
    @Mock private QuizSessionRepository quizSessionRepository;
    @Mock private QuizAnswerDetailRepository quizAnswerDetailRepository;
    @Mock private ObjectProvider<HealthEndpoint> healthEndpointProvider;

    private SystemDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemDashboardServiceImpl(
                userRepository,
                historicalContextRepository,
                characterRepository,
                documentRepository,
                chatSessionRepository,
                messageRepository,
                paymentOrderRepository,
                paymentTransactionRepository,
                tierRepository,
                userTierRepository,
                quizRepository,
                quizSessionRepository,
                quizAnswerDetailRepository,
                new SimpleMeterRegistry(),
                healthEndpointProvider
        );
    }

    @Test
    @DisplayName("getOverview separates global and local top characters and B2B/B2C roles")
    void getOverview_SeparatesGlobalAndLocalCharacters() {
        DashboardTopCharacterProjection globalChar = mock(DashboardTopCharacterProjection.class);
        when(globalChar.getCharacterId()).thenReturn("c-global");
        when(globalChar.getName()).thenReturn("Ngô Quyền");
        when(globalChar.getTitle()).thenReturn("Tiết độ sứ");
        when(globalChar.getTotalMessages()).thenReturn(19L);

        DashboardTopCharacterProjection localChar = mock(DashboardTopCharacterProjection.class);
        when(localChar.getCharacterId()).thenReturn("c-local");
        when(localChar.getName()).thenReturn("Ngô Quyền");
        when(localChar.getTitle()).thenReturn("Tiền Ngô Vương");
        when(localChar.getTotalMessages()).thenReturn(4L);

        when(messageRepository.findTopCharactersForDashboard(10)).thenReturn(List.of(globalChar));
        when(messageRepository.findTopLocalCharactersForDashboard(10)).thenReturn(List.of(localChar));
        when(userRepository.countActiveUsersByRole(UserRole.CUSTOMER)).thenReturn(50L);
        when(userRepository.countActiveUsersByRole(UserRole.CONTENT_ADMIN)).thenReturn(2L);
        when(userRepository.countActiveUsersByRole(UserRole.SYSTEM_ADMIN)).thenReturn(1L);
        when(userRepository.countActiveUsersByRole(UserRole.SCHOOL_ADMIN)).thenReturn(3L);
        when(userRepository.countActiveUsersByRole(UserRole.TEACHER)).thenReturn(10L);
        when(userRepository.countActiveUsersByRole(UserRole.SCHOOL_STUDENT)).thenReturn(100L);

        DashboardOverviewResponse b2cOverview = service.getOverview("B2C");
        assertEquals(1, b2cOverview.getTopCharacters().size());
        assertEquals("c-global", b2cOverview.getTopCharacters().get(0).getCharacterId());
        assertEquals(1, b2cOverview.getTopGlobalCharacters().size());
        assertEquals(1, b2cOverview.getTopLocalCharacters().size());
        assertEquals(3L, b2cOverview.getRoles().getSchoolAdmins());
        assertEquals(10L, b2cOverview.getRoles().getTeachers());
        assertEquals(100L, b2cOverview.getRoles().getSchoolStudents());

        DashboardOverviewResponse b2bOverview = service.getOverview("B2B");
        assertEquals(1, b2bOverview.getTopCharacters().size());
        assertEquals("c-local", b2bOverview.getTopCharacters().get(0).getCharacterId());
    }

    @Test
    @DisplayName("getChatActivity uses a 2-minute cutoff for active sessions")
    void getChatActivity_UsesTwoMinuteCutoffForActiveSessions() {
        LocalDate today = LocalDate.now();
        when(chatSessionRepository.countSessionsByPeriod(any(), any(), eq("day"))).thenReturn(Collections.emptyList());
        when(messageRepository.countMessagesByPeriod(any(), any(), eq("day"))).thenReturn(Collections.emptyList());
        when(chatSessionRepository.countActiveSince(any())).thenReturn(3L);

        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        DashboardChatActivityResponse response = service.getChatActivity(today, today, "day", "ALL");
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(chatSessionRepository).countActiveSince(cutoffCaptor.capture());

        LocalDateTime captured = cutoffCaptor.getValue();
        long minutesDiffBefore = Duration.between(captured, before).toSeconds();
        long minutesDiffAfter = Duration.between(captured, after).toSeconds();
        assertTrue(minutesDiffBefore >= 119 && minutesDiffAfter <= 125,
                "Active session cutoff should be ~2 minutes before now");
        assertEquals(3L, response.getSummary().getActiveSessions());
    }

    @Test
    @DisplayName("getRevenue separates B2C tier subscriptions from B2B school token topups")
    void getRevenue_SeparatesB2cAndB2b() {
        LocalDate today = LocalDate.now();
        when(paymentOrderRepository.sumAmountByStatusAndOrderType(
                PaymentOrderStatus.PAID, PaymentOrderType.TIER_SUBSCRIPTION)).thenReturn(396000L);
        when(paymentOrderRepository.countByDeletedAtIsNullAndStatusAndOrderType(
                PaymentOrderStatus.PAID, PaymentOrderType.TIER_SUBSCRIPTION)).thenReturn(4L);
        when(paymentOrderRepository.sumPaidRevenueByPeriodAndOrderType(
                any(), any(), eq("day"), eq("TIER_SUBSCRIPTION"))).thenReturn(Collections.emptyList());
        when(paymentOrderRepository.countOrdersByStatusAndOrderType("TIER_SUBSCRIPTION"))
                .thenReturn(Collections.emptyList());
        when(paymentOrderRepository.sumPaidRevenueByTier(any(), any())).thenReturn(Collections.emptyList());

        DashboardRevenueResponse b2cRevenue = service.getRevenue(today, today, "day", "B2C");
        assertEquals(396000L, b2cRevenue.getSummary().getTotalRevenue());
        assertEquals(4L, b2cRevenue.getSummary().getPaidOrders());
        assertEquals(99000L, b2cRevenue.getSummary().getAverageOrderValue());

        when(paymentOrderRepository.sumAmountByStatusAndOrderType(
                PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP)).thenReturn(1000000L);
        when(paymentOrderRepository.countByDeletedAtIsNullAndStatusAndOrderType(
                PaymentOrderStatus.PAID, PaymentOrderType.SCHOOL_TOKEN_TOPUP)).thenReturn(2L);
        when(paymentOrderRepository.sumPaidRevenueByPeriodAndOrderType(
                any(), any(), eq("day"), eq("SCHOOL_TOKEN_TOPUP"))).thenReturn(Collections.emptyList());
        when(paymentOrderRepository.countOrdersByStatusAndOrderType("SCHOOL_TOKEN_TOPUP"))
                .thenReturn(Collections.emptyList());

        DashboardRevenueResponse b2bRevenue = service.getRevenue(today, today, "day", "B2B");
        assertEquals(1000000L, b2bRevenue.getSummary().getTotalRevenue());
        assertEquals(2L, b2bRevenue.getSummary().getPaidOrders());
        assertEquals(500000L, b2bRevenue.getSummary().getAverageOrderValue());
        assertTrue(b2bRevenue.getRevenueByTier().isEmpty());
    }

    @Test
    @DisplayName("getContentSummary separates global and local content")
    void getContentSummary_SeparatesGlobalAndLocal() {
        when(historicalContextRepository.countCurrent()).thenReturn(4L);
        when(historicalContextRepository.countPublished()).thenReturn(4L);
        when(historicalContextRepository.countActive()).thenReturn(4L);
        when(historicalContextRepository.countLocalCurrent()).thenReturn(2L);
        when(historicalContextRepository.countLocalPublished()).thenReturn(1L);
        when(historicalContextRepository.countLocalActive()).thenReturn(1L);

        DashboardContentSummaryResponse localSummary = service.getContentSummary("LOCAL");
        assertEquals(2L, localSummary.getHistoricalContexts().getTotal());
        assertEquals(1L, localSummary.getHistoricalContexts().getPublished());
        assertEquals(2L, localSummary.getLocalHistoricalContexts().getTotal());
    }

    @Test
    @DisplayName("getQuiz maps questionContent and respects scope")
    void getQuiz_MapsQuestionContentAndRespectsScope() {
        LocalDate today = LocalDate.now();
        when(quizRepository.countGlobalQuizzes()).thenReturn(10L);
        when(quizRepository.countGlobalPublishedQuizzes()).thenReturn(4L);
        when(quizRepository.countGlobalDraftQuizzes()).thenReturn(6L);
        when(quizSessionRepository.countStartedSessionsByScope(false)).thenReturn(20L);
        when(quizSessionRepository.countCompletedSessionsByScope(false)).thenReturn(15L);
        when(quizSessionRepository.averageScorePercentageByScope(false)).thenReturn(75.0);
        when(quizSessionRepository.countSessionsByPeriodAndScope(any(), any(), eq("day"), eq(false)))
                .thenReturn(Collections.emptyList());

        DashboardTopWrongQuestionProjection wrongQ = mock(DashboardTopWrongQuestionProjection.class);
        when(wrongQ.getQuestionId()).thenReturn("q-1");
        when(wrongQ.getQuestionContent()).thenReturn("Chiến dịch Điện Biên Phủ diễn ra năm nào?");
        when(wrongQ.getQuizId()).thenReturn("quiz-1");
        when(wrongQ.getQuizTitle()).thenReturn("Chiến dịch Điện Biên Phủ (1954)");
        when(wrongQ.getWrongAnswers()).thenReturn(1L);
        when(wrongQ.getTotalAnswers()).thenReturn(1L);
        when(wrongQ.getWrongRate()).thenReturn(1.0);
        when(quizAnswerDetailRepository.findTopWrongQuestionsForDashboardByScope(any(), any(), eq(false), eq(5)))
                .thenReturn(List.of(wrongQ));

        DashboardQuizAnalyticsResponse response = service.getQuiz(today, today, "day", "GLOBAL");
        assertEquals(10L, response.getSummary().getTotalQuizzes());
        assertEquals(1, response.getTopWrongQuestions().size());
        assertEquals("Chiến dịch Điện Biên Phủ diễn ra năm nào?",
                response.getTopWrongQuestions().get(0).getQuestionContent());
    }

    @Test
    @DisplayName("getTokens separates B2B and B2C token stats and rejects invalid scope")
    void getTokens_SeparatesB2bAndB2c() {
        LocalDate today = LocalDate.now();
        DashboardTokenSummaryProjection b2bTokens = mock(DashboardTokenSummaryProjection.class);
        when(b2bTokens.getPromptTokens()).thenReturn(100L);
        when(b2bTokens.getCompletionTokens()).thenReturn(400L);
        when(b2bTokens.getTotalTokens()).thenReturn(500L);

        DashboardTokenBalanceSummaryProjection b2bBalance = mock(DashboardTokenBalanceSummaryProjection.class);
        when(b2bBalance.getRemainingTokens()).thenReturn(5000L);

        when(messageRepository.sumTokensBetweenByRoles(any(), any(), anyList())).thenReturn(b2bTokens);
        when(userRepository.getTokenBalanceSummaryByRoles(anyList())).thenReturn(b2bBalance);
        when(messageRepository.sumTokensByPeriodByRoles(any(), any(), eq("day"), anyList()))
                .thenReturn(Collections.emptyList());
        when(messageRepository.findTopTokenUsersByRoles(any(), any(), anyList(), eq(10)))
                .thenReturn(Collections.emptyList());

        DashboardTokenUsageResponse response = service.getTokens(today, today, "day", "B2B");
        assertEquals(500L, response.getSummary().getTotalTokens());
        assertEquals(5000L, response.getSummary().getRemainingTokens());
        assertTrue(response.getTokenBalanceByTier().isEmpty());

        assertThrows(InvalidRequestException.class, () -> service.getOverview("INVALID_SCOPE"));
    }
}
