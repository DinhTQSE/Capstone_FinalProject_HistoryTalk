package com.historytalk.controller.gamification;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.gamification.*;
import com.historytalk.service.gamification.GamificationService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gamification")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Gamification", description = "API endpoints for streak, daily quests and rewards")
public class GamificationController {

    private final GamificationService gamificationService;

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get today's streak and daily quests")
    public ResponseEntity<ApiResponse<TodayGamificationResponse>> getToday() {
        String userId = SecurityUtils.getUserId();
        log.info("GET /api/v1/gamification/today - user: {}", userId);
        TodayGamificationResponse data = gamificationService.getTodayState(userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Gamification state retrieved successfully"));
    }

    @GetMapping("/study-days")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get list of studied dates (yyyy-MM-dd) for a given month",
               description = "Returns an array of date strings for each day the user checked in during the requested month.")
    public ResponseEntity<ApiResponse<List<String>>> getStudyDays(
            @Parameter(description = "Năm cần xem lịch (ví dụ: 2026)", example = "2026")
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") @Min(2000) @Max(2100) int year,
            @Parameter(description = "Tháng cần xem lịch (1–12)", example = "9")
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getMonthValue()}") @Min(1) @Max(12) int month) {
        String userId = SecurityUtils.getUserId();
        log.info("GET /api/v1/gamification/study-days - user: {}, year: {}, month: {}", userId, year, month);
        List<String> days = gamificationService.getStudyDays(userId, year, month);
        return ResponseEntity.ok(ApiResponse.success(days, "Study days retrieved successfully"));
    }

    @PostMapping("/claim")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Claim token reward for a completed daily quest")
    public ResponseEntity<ApiResponse<ClaimQuestResponse>> claimReward(
            @Valid @RequestBody ClaimQuestRequest request) {
        String userId = SecurityUtils.getUserId();
        log.info("POST /api/v1/gamification/claim - questId: {}", request.getQuestId());
        ClaimQuestResponse data = gamificationService.claimQuestReward(userId, request.getQuestId());
        return ResponseEntity.ok(ApiResponse.success(data, "Quest reward claimed successfully"));
    }
}
