package com.historytalk.controller.gamification;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.gamification.UpdateQuestRequest;
import com.historytalk.entity.gamification.DailyQuest;
import com.historytalk.service.gamification.GamificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff/quests")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Staff Quests", description = "API endpoints for staff/admin to manage daily quests")
public class StaffQuestController {

    private final GamificationService gamificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List all quest definitions")
    public ResponseEntity<ApiResponse<List<DailyQuest>>> listQuests() {
        log.info("GET /api/v1/staff/quests");
        List<DailyQuest> quests = gamificationService.staffListQuests();
        return ResponseEntity.ok(ApiResponse.success(quests, "Quests retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a quest definition by ID")
    public ResponseEntity<ApiResponse<DailyQuest>> getQuest(@PathVariable String id) {
        log.info("GET /api/v1/staff/quests/{}", id);
        DailyQuest quest = gamificationService.staffGetQuest(id);
        return ResponseEntity.ok(ApiResponse.success(quest, "Quest retrieved successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a quest definition")
    public ResponseEntity<ApiResponse<DailyQuest>> updateQuest(
            @PathVariable String id,
            @Valid @RequestBody UpdateQuestRequest request) {
        log.info("PUT /api/v1/staff/quests/{}", id);
        DailyQuest updated = gamificationService.staffUpdateQuest(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Quest updated successfully"));
    }
}
