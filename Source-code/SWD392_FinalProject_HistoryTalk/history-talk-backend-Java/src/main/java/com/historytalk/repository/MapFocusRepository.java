package com.historytalk.repository;

import com.historytalk.entity.map.MapFocus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MapFocusRepository extends JpaRepository<MapFocus, UUID> {

    /**
     * Public / user-facing: active, non-deleted focuses for a context.
     * Sorted by order_index ascending.
     */
    List<MapFocus> findByHistoricalContext_ContextIdAndDeletedAtIsNullAndIsActiveTrue(
            UUID contextId, Sort sort);

    /**
     * Admin-facing: all non-deleted focuses (including inactive) for a context.
     * Sorted by order_index ascending.
     */
    List<MapFocus> findByHistoricalContext_ContextIdAndDeletedAtIsNull(
            UUID contextId, Sort sort);

    /**
     * Find the current default focus for a context.
     * Used before flipping is_default when a new default is being set.
     */
    Optional<MapFocus> findByHistoricalContext_ContextIdAndIsDefaultTrueAndDeletedAtIsNull(
            UUID contextId);

    /**
     * Load all non-deleted focuses for a context (no sort needed).
     * Used for cascade soft-delete and reorder validation.
     */
    List<MapFocus> findByHistoricalContext_ContextIdAndDeletedAtIsNull(UUID contextId);

    /**
     * Existence check for a single non-deleted focus.
     */
    Optional<MapFocus> findByFocusIdAndDeletedAtIsNull(UUID focusId);

    /**
     * Retrieve the highest order_index currently assigned to a context.
     * Returns null if there are no existing focuses.
     */
    @Query("""
            SELECT MAX(f.orderIndex)
            FROM MapFocus f
            WHERE f.historicalContext.contextId = :contextId
            AND f.deletedAt IS NULL
            """)
    Integer findMaxOrderIndexByContextId(@Param("contextId") UUID contextId);
}
