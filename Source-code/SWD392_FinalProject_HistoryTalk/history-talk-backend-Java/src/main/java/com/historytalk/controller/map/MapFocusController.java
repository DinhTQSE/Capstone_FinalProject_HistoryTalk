package com.historytalk.controller.map;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.map.CreateMapFocusRequest;
import com.historytalk.dto.map.MapFocusResponse;
import com.historytalk.dto.map.ReorderMapFocusRequest;
import com.historytalk.dto.map.UpdateMapFocusRequest;
import com.historytalk.service.map.MapFocusService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Map Focus endpoints.
 *
 * A Map Focus defines a named geographic camera position (lat/lng/zoom) that
 * the frontend uses to pan and zoom the map when a historical context is opened.
 * Admins manage focus points; all clients (including guests) can read the active list.
 *
 * Base path: /api/v1/historical-contexts/{contextId}/map-focuses
 */
@RestController
@RequestMapping("/api/v1/historical-contexts/{contextId}/map-focuses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Map Focus", description = "Context-level map camera focus points")
public class MapFocusController {

    private final MapFocusService mapFocusService;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/historical-contexts/{contextId}/map-focuses
    // Public — JWT optional. Admin JWT reveals inactive focuses.
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(
            summary = "List map focus points for a context",
            description = """
                    Returns focus points ordered by order_index.
                    - Guest / authenticated user: only active (is_active=true) focuses.
                    - Admin / Staff: all non-deleted focuses including inactive ones.
                    """)
    public ResponseEntity<ApiResponse<List<MapFocusResponse>>> getFocuses(
            @PathVariable String contextId) {

        log.info("GET map-focuses contextId={}", contextId);

        String role = SecurityUtils.getRoleName();   // null for guests
        List<MapFocusResponse> data = mapFocusService.getFocuses(contextId, role);

        return ResponseEntity.ok(ApiResponse.success(data, "Map focuses retrieved successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/historical-contexts/{contextId}/map-focuses
    // Admin / Staff only.
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Create a map focus point",
            description = """
                    Creates a new named focus point for the context.
                    order_index is auto-assigned (appended to the tail).
                    If isDefault=true, any existing default is unset first.
                    Admin / Staff only.
                    """)
    public ResponseEntity<ApiResponse<MapFocusResponse>> createFocus(
            @PathVariable String contextId,
            @Valid @RequestBody CreateMapFocusRequest request) {

        log.info("POST map-focus contextId={}", contextId);

        String callerId = SecurityUtils.getUserId();
        String role     = SecurityUtils.getRoleName();

        MapFocusResponse data = mapFocusService.createFocus(contextId, request, callerId, role);

        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(ApiResponse.success(data, "Map focus created successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/historical-contexts/{contextId}/map-focuses/{focusId}
    // Admin / Staff only. PATCH semantics — only non-null fields are applied.
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/{focusId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update a map focus point",
            description = """
                    Partially updates an existing focus point (PATCH semantics).
                    Only provided (non-null) fields are modified.
                    orderIndex is NOT editable here — use PATCH /reorder instead.
                    Admin / Staff only.
                    """)
    public ResponseEntity<ApiResponse<MapFocusResponse>> updateFocus(
            @PathVariable String contextId,
            @PathVariable String focusId,
            @Valid @RequestBody UpdateMapFocusRequest request) {

        log.info("PUT map-focus focusId={} contextId={}", focusId, contextId);

        String callerId = SecurityUtils.getUserId();
        String role     = SecurityUtils.getRoleName();

        MapFocusResponse data =
                mapFocusService.updateFocus(contextId, focusId, request, callerId, role);

        return ResponseEntity.ok(ApiResponse.success(data, "Map focus updated successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/historical-contexts/{contextId}/map-focuses/reorder
    // Admin / Staff only.
    // ─────────────────────────────────────────────────────────────────────────

    @PatchMapping("/reorder")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Reorder map focus points",
            description = """
                    Accepts an ordered list of focusIds.
                    The server assigns order_index = 0, 1, 2, … based on the position
                    of each ID in the list.
                    All provided IDs must belong to this context and must not be deleted.
                    Admin / Staff only.
                    """)
    public ResponseEntity<ApiResponse<List<MapFocusResponse>>> reorderFocuses(
            @PathVariable String contextId,
            @Valid @RequestBody ReorderMapFocusRequest request) {

        log.info("PATCH map-focuses/reorder contextId={} count={}",
                contextId, request.getFocusIds().size());

        String role = SecurityUtils.getRoleName();

        List<MapFocusResponse> data = mapFocusService.reorderFocuses(contextId, request, role);

        return ResponseEntity.ok(ApiResponse.success(data, "Map focuses reordered successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/historical-contexts/{contextId}/map-focuses/{focusId}
    // Admin / Staff only. Soft-delete.
    // ─────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/{focusId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Soft-delete a map focus point",
            description = """
                    Marks the focus as deleted (sets deleted_at).
                    The focus is excluded from all subsequent GET responses.
                    Admin / Staff only.
                    """)
    public ResponseEntity<Void> deleteFocus(
            @PathVariable String contextId,
            @PathVariable String focusId) {

        log.info("DELETE map-focus focusId={} contextId={}", focusId, contextId);

        String role = SecurityUtils.getRoleName();

        mapFocusService.deleteFocus(contextId, focusId, role);

        return ResponseEntity.noContent().build();
    }
}
