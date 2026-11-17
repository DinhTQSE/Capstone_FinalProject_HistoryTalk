package com.historytalk.controller.gamification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.gamification.ClaimQuestRequest;
import com.historytalk.dto.gamification.ClaimQuestResponse;
import com.historytalk.dto.gamification.TodayGamificationResponse;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.security.AuthenticatedPrincipal;
import com.historytalk.service.gamification.GamificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GamificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GamificationService gamificationService;

    @InjectMocks
    private GamificationController gamificationController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal("student@school.edu.vn", userId, "SCHOOL_STUDENT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_STUDENT"))
                )
        );

        mockMvc = MockMvcBuilders.standaloneSetup(gamificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("IT-GAM-01: GET /api/v1/gamification/today returns streak and quest list")
    void getToday_Authenticated_Returns200() throws Exception {
        TodayGamificationResponse state = TodayGamificationResponse.builder()
                .date("2026-11-12")
                .streakCount(4)
                .studiedToday(true)
                .claimableTokens(500)
                .quests(List.of())
                .build();

        when(gamificationService.getTodayState(eq(userId))).thenReturn(state);

        mockMvc.perform(get("/api/v1/gamification/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.streakCount").value(4))
                .andExpect(jsonPath("$.data.claimableTokens").value(500));
    }

    @Test
    @DisplayName("IT-GAM-02: GET /api/v1/gamification/study-days returns studied dates in month")
    void getStudyDays_ValidMonth_Returns200() throws Exception {
        when(gamificationService.getStudyDays(eq(userId), eq(2026), eq(11)))
                .thenReturn(List.of("2026-11-10", "2026-11-11", "2026-11-12"));

        mockMvc.perform(get("/api/v1/gamification/study-days")
                        .param("year", "2026")
                        .param("month", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("2026-11-10"));
    }

    @Test
    @DisplayName("IT-GAM-03: POST /api/v1/gamification/claim credits quest reward and returns 200")
    void claimReward_CompletedQuest_Returns200() throws Exception {
        ClaimQuestRequest req = new ClaimQuestRequest("chat_once");
        ClaimQuestResponse res = ClaimQuestResponse.builder()
                .questId("chat_once")
                .rewardTokens(500)
                .tokenBalance(1500)
                .build();

        when(gamificationService.claimQuestReward(eq(userId), eq("chat_once"))).thenReturn(res);

        mockMvc.perform(post("/api/v1/gamification/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rewardTokens").value(500))
                .andExpect(jsonPath("$.data.tokenBalance").value(1500));
    }

    @Test
    @DisplayName("IT-GAM-04: POST /api/v1/gamification/claim on incomplete or already-claimed quest returns 400")
    void claimReward_EarlyOrDuplicateClaim_Returns400() throws Exception {
        ClaimQuestRequest req = new ClaimQuestRequest("chat_once");
        when(gamificationService.claimQuestReward(eq(userId), eq("chat_once")))
                .thenThrow(new InvalidRequestException("Nhiệm vụ này đã được nhận phần thưởng hôm nay"));

        mockMvc.perform(post("/api/v1/gamification/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
