package com.historytalk.controller.map;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.map.CreateMapPinRequest;
import com.historytalk.dto.map.MapPinResponse;
import com.historytalk.service.map.MapPinService;
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
 * Interactive Map endpoints.
 *
 * All three operations are role-aware — admin and user share the same paths,
 * and the service layer applies different rules based on the caller's role.
 *
 * Base path: /api/v1/historical-contexts/{contextId}/map-pins
 */
@RestController
@RequestMapping("/api/v1/historical-contexts/{contextId}/map-pins")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Interactive Map", description = "Role-aware map pin endpoints for historical contexts")
public class MapPinController {

    private final MapPinService mapPinService;

    /**
     * GET /api/v1/historical-contexts/{contextId}/map-pins?year={year}
     *
     * Public for admin pins; enriched with caller's own user pins when authenticated.
     * - Guest: ADMIN pins only.
     * - Authenticated user: ADMIN pins + caller's own USER pins.
     * - Admin role: ADMIN pins only (manages their own; user pins are private).
     */
    @GetMapping
    @Operation(
            summary = "Load map pins for a timeline year",
            description = """
                    Returns educational (ADMIN) pins visible to everyone.
                    Authenticated users additionally receive their own personal (USER) pins.
                    Filter by `year` uses exact match against the pin's pin_year.
                    """)
    public ResponseEntity<ApiResponse<List<MapPinResponse>>> getPins(
            @PathVariable String contextId,
            @RequestParam Integer year) {

        log.info("GET map-pins contextId={} year={}", contextId, year);

        // getUserId() returns null if unauthenticated — service uses this to decide fetch strategy
        String callerId = SecurityUtils.getUserId();
        String role     = SecurityUtils.getRoleName();

        List<MapPinResponse> data = mapPinService.getPins(contextId, year, callerId, role);

        return ResponseEntity.ok(ApiResponse.success(data, "Map pins retrieved successfully"));
    }

    /**
     * POST /api/v1/historical-contexts/{contextId}/map-pins
     *
     * Creates a pin. Role determines type:
     * - Admin: creates ADMIN pin; pinType is required (ALLIED_FORCE | ENEMY_FORCE).
     * - User: creates USER pin; pinType is ignored; context must be published.
     */
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Create a map pin",
            description = """
                    Admins create educational ADMIN pins (pinType required).
                    Authenticated users create personal USER study pins (pinType ignored).
                    """)
    public ResponseEntity<ApiResponse<MapPinResponse>> createPin(
            @PathVariable String contextId,
            @Valid @RequestBody CreateMapPinRequest request) {

        log.info("POST map-pin contextId={}", contextId);

        String callerId = SecurityUtils.getUserId();
        String role     = SecurityUtils.getRoleName();

        MapPinResponse data = mapPinService.createPin(contextId, request, callerId, role);

        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(ApiResponse.success(data, "Map pin created successfully"));
    }

    /**
     * DELETE /api/v1/historical-contexts/{contextId}/map-pins/{pinId}
     *
     * Hard-deletes a pin. Ownership enforced in service layer:
     * - Admin: can delete any ADMIN pin on this context.
     * - User: can only delete their own USER pin.
     * Returns 404 on any mismatch (no info leakage).
     */
    @DeleteMapping("/{pinId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Delete a map pin",
            description = """
                    Admins can delete any ADMIN pin on this context.
                    Users can only delete their own USER pins.
                    Returns 404 if the pin is not found or the caller is not the owner.
                    """)
    public ResponseEntity<Void> deletePin(
            @PathVariable String contextId,
            @PathVariable String pinId) {

        log.info("DELETE map-pin pinId={} contextId={}", pinId, contextId);

        String callerId = SecurityUtils.getUserId();
        String role     = SecurityUtils.getRoleName();

        mapPinService.deletePin(contextId, pinId, callerId, role);

        return ResponseEntity.noContent().build();
    }
}
