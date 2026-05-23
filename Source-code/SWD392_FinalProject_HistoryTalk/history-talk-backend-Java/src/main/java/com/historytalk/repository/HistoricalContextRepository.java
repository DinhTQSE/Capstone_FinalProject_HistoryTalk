package com.historytalk.repository;

import com.historytalk.entity.enums.EventCategory;
import com.historytalk.entity.enums.EventEra;
import com.historytalk.entity.historicalContext.HistoricalContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HistoricalContextRepository extends JpaRepository<HistoricalContext, UUID> {

       Optional<HistoricalContext> findByNameIgnoreCase(String name);

       boolean existsByNameIgnoreCaseAndContextIdNot(String name, UUID contextId);

       Page<HistoricalContext> findByCreatedByUid(UUID uid, Pageable pageable);

       @Query("""
                     SELECT hc FROM HistoricalContext hc
                     WHERE (:search IS NULL OR :search = ''
                            OR hc.name ILIKE CONCAT('%', :search, '%')
                            OR hc.description ILIKE CONCAT('%', :search, '%'))
                     AND (:era IS NULL OR hc.era = :era)
                     AND (:category IS NULL OR hc.category = :category)
                     AND (:includeDraft = true OR hc.isPublished = true)
                     AND (:includeDeleted = true OR hc.deletedAt IS NULL)
                     """)
       Page<HistoricalContext> findAllWithSearch(
                     @Param("search") String search,
                     @Param("era") EventEra era,
                     @Param("category") EventCategory category,
                     @Param("includeDraft") boolean includeDraft,
                     @Param("includeDeleted") boolean includeDeleted,
                     Pageable pageable);

       @Query("""
                     SELECT hc FROM HistoricalContext hc
                     WHERE (:search IS NULL OR :search = ''
                            OR hc.name ILIKE CONCAT('%', :search, '%')
                            OR hc.description ILIKE CONCAT('%', :search, '%'))
                     AND (:includeDraft = true OR hc.isPublished = true)
                     AND (:includeDeleted = true OR hc.deletedAt IS NULL)
                     ORDER BY hc.createdAt DESC
                     """)
       List<HistoricalContext> findAllSimple(@Param("search") String search,
                                             @Param("includeDraft") boolean includeDraft,
                                             @Param("includeDeleted") boolean includeDeleted);

       @Query(value = """
               SELECT * FROM historical_schema.historical_context hc
               WHERE hc.deleted_at IS NOT NULL
               ORDER BY hc.created_at DESC
               """, nativeQuery = true)
       List<HistoricalContext> findAllDeleted();

       @Query(value = """
               UPDATE historical_schema.historical_context
               SET deleted_at = NULL, is_active = true
               WHERE context_id = :contextId
               """, nativeQuery = true)
       @org.springframework.data.jpa.repository.Modifying
       int restoreById(@Param("contextId") UUID contextId);

       @Query("SELECT COUNT(hc) FROM HistoricalContext hc")
       long countCurrent();

       @Query("SELECT COUNT(hc) FROM HistoricalContext hc WHERE hc.isPublished = true")
       long countPublished();

       @Query("SELECT COUNT(hc) FROM HistoricalContext hc WHERE hc.deletedAt IS NULL AND hc.isActive = true")
       long countActive();
}
