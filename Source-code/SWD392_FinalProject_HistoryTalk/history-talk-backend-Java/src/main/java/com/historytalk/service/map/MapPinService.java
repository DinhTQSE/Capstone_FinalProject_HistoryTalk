package com.historytalk.service.map;

import com.historytalk.dto.map.CreateMapPinRequest;
import com.historytalk.dto.map.MapPinResponse;

import java.util.List;

public interface MapPinService {

    /**
     * Load map pins for a historical context at a specific year.
     * Behavior differs by caller:
     * - Guest (callerId = null): returns ADMIN pins only.
     * - Authenticated user: returns ADMIN pins + caller's own USER pins.
     * - Admin role: returns ADMIN pins only (manages own pins, user pins are private).
     */
    List<MapPinResponse> getPins(String contextId, Integer year, String callerId, String role);

    /**
     * Create a map pin. Role determines pin_owner_type and validation:
     * - Admin role: creates ADMIN pin; pinType must be ALLIED_FORCE or ENEMY_FORCE.
     * - User role: creates USER pin; pinType is ignored and set to null.
     *              Context must be published.
     */
    MapPinResponse createPin(String contextId, CreateMapPinRequest request,
                             String callerId, String role);

    /**
     * Delete a map pin (hard delete).
     * - Admin role: can delete any ADMIN pin on this context.
     * - User role: can only delete their own USER pin.
     * Throws ResourceNotFoundException on any mismatch (no info leakage).
     */
    void deletePin(String contextId, String pinId, String callerId, String role);

    /**
     * Soft-delete all non-deleted pins for a context.
     * Called from HistoricalContextServiceImpl.softDeleteContext() cascade.
     */
    void softDeleteAllPinsForContext(String contextId);
}
