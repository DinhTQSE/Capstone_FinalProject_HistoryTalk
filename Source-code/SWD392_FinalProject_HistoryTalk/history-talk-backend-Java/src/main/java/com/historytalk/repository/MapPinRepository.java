package com.historytalk.repository;

import com.historytalk.entity.map.MapPin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MapPinRepository extends JpaRepository<MapPin, UUID> {

    /**
     * Returns only ADMIN pins for a context at an exact year.
     * Used for unauthenticated (guest) requests.
     */
    @Query("""
            SELECT p FROM MapPin p
            WHERE p.historicalContext.contextId = :contextId
            AND p.pinYear = :pinYear
            AND p.pinOwnerType = 'ADMIN'
            AND p.deletedAt IS NULL
            """)
    List<MapPin> findAdminPinsByContextAndYear(@Param("contextId") UUID contextId,
                                               @Param("pinYear") Integer pinYear);

    /**
     * Returns ADMIN pins + the caller's own USER pins for a context at an exact year.
     * Used for authenticated user requests.
     */
    @Query("""
            SELECT p FROM MapPin p
            WHERE p.historicalContext.contextId = :contextId
            AND p.pinYear = :pinYear
            AND p.deletedAt IS NULL
            AND (p.pinOwnerType = 'ADMIN'
                 OR (p.pinOwnerType = 'USER' AND p.createdBy.uid = :userId))
            """)
    List<MapPin> findVisiblePinsForUser(@Param("contextId") UUID contextId,
                                        @Param("pinYear") Integer pinYear,
                                        @Param("userId") UUID userId);

    /**
     * Load a single non-deleted pin by ID regardless of owner type.
     * Used for existence checks.
     */
    Optional<MapPin> findByPinIdAndDeletedAtIsNull(UUID pinId);

    /**
     * Load all non-deleted pins for a context.
     * Used for cascade soft-delete when the parent context is soft-deleted.
     */
    List<MapPin> findByHistoricalContext_ContextIdAndDeletedAtIsNull(UUID contextId);
}
