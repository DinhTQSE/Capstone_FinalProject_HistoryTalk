package com.historytalk.service.map;

import com.historytalk.dto.map.CreateMapFocusRequest;
import com.historytalk.dto.map.MapFocusResponse;
import com.historytalk.dto.map.ReorderMapFocusRequest;
import com.historytalk.dto.map.UpdateMapFocusRequest;

import java.util.List;

public interface MapFocusService {

    /**
     * List focus points for a context.
     * <ul>
     *   <li>Non-admin callers: only {@code isActive=true}, non-deleted rows, ordered by {@code order_index}.</li>
     *   <li>Admin/Staff callers: all non-deleted rows (including inactive), ordered by {@code order_index}.</li>
     * </ul>
     */
    List<MapFocusResponse> getFocuses(String contextId, String role);

    /**
     * Create a new focus point for a context.
     * {@code order_index} is auto-assigned as {@code MAX(existing) + 1}.
     * If {@code isDefault=true}, the previous default (if any) is unset first.
     * Admin/Staff only.
     */
    MapFocusResponse createFocus(String contextId, CreateMapFocusRequest request,
                                 String callerId, String role);

    /**
     * Partially update an existing focus point (PATCH semantics).
     * Only non-null fields in the request are applied.
     * If {@code isDefault=true}, the previous default is unset first.
     * Admin/Staff only.
     */
    MapFocusResponse updateFocus(String contextId, String focusId,
                                 UpdateMapFocusRequest request, String callerId, String role);

    /**
     * Reorder all focus points for a context.
     * The client sends an ordered list of {@code focusId}s;
     * the server assigns {@code order_index = 0, 1, 2, …} accordingly.
     * All provided IDs must belong to the given context and must not be deleted.
     * Admin/Staff only.
     */
    List<MapFocusResponse> reorderFocuses(String contextId, ReorderMapFocusRequest request,
                                          String role);

    /**
     * Soft-delete a single focus point.
     * Admin/Staff only.
     */
    void deleteFocus(String contextId, String focusId, String role);

    /**
     * Cascade soft-delete: sets {@code deletedAt=now()} on all non-deleted
     * focus points for a context.
     * Called by {@code HistoricalContextServiceImpl.softDeleteContext()}.
     */
    void softDeleteAllFocusesForContext(String contextId);
}
