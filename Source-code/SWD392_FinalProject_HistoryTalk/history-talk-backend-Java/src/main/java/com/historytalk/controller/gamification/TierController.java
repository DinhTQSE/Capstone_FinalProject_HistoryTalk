package com.historytalk.controller.gamification;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.gamification.CreateTierRequest;
import com.historytalk.dto.gamification.TierResponse;
import com.historytalk.service.gamification.TierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/system-admin/tiers", "/api/v1/tiers"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tiers", description = "API endpoints for managing membership tiers")
public class TierController {

    private final TierService tierService;

    @GetMapping
    @Operation(summary = "Get all membership tiers")
    public ResponseEntity<ApiResponse<List<TierResponse>>> getAllTiers() {
        log.info("GET /tiers");
        List<TierResponse> tiers = tierService.getAllTiers();
        return ResponseEntity.ok(ApiResponse.success(tiers, "Tiers retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tier by ID")
    public ResponseEntity<ApiResponse<TierResponse>> getTierById(@PathVariable String id) {
        log.info("GET /tiers/{}", id);
        TierResponse tier = tierService.getTierById(id);
        return ResponseEntity.ok(ApiResponse.success(tier, "Tier retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a new tier (System Admin only)")
    public ResponseEntity<ApiResponse<TierResponse>> createTier(@Valid @RequestBody CreateTierRequest request) {
        log.info("POST /tiers - title: {}", request.getTitle());
        TierResponse created = tierService.createTier(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tier created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update an existing tier (System Admin only)")
    public ResponseEntity<ApiResponse<TierResponse>> updateTier(
            @PathVariable String id,
            @Valid @RequestBody CreateTierRequest request) {
        log.info("PUT /tiers/{}", id);
        TierResponse updated = tierService.updateTier(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Tier updated successfully"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Patch an existing tier (System Admin only)")
    public ResponseEntity<ApiResponse<TierResponse>> patchTier(
            @PathVariable String id,
            @Valid @RequestBody CreateTierRequest request) {
        log.info("PATCH /tiers/{}", id);
        TierResponse updated = tierService.updateTier(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Tier updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a tier (System Admin only)")
    public ResponseEntity<ApiResponse<?>> deleteTier(@PathVariable String id) {
        log.info("DELETE /tiers/{}", id);
        tierService.deleteTier(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Tier deleted successfully"));
    }
}
